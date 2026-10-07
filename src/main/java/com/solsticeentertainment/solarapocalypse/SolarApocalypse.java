package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.cc.CubeEngine;
import com.solsticeentertainment.solarapocalypse.cc.CubicSky;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

// Cubic Chunks is optional: "after:" only orders loading, it never requires the mod.
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, dependencies = "after:cubicchunks")
public class SolarApocalypse {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    private static final boolean CUBIC_CHUNKS = Loader.isModLoaded("cubicchunks");

    private static Timeline timeline;
    private static BlockRules rules;
    private static final Map<World, BlockChanges> CHANGES = new WeakHashMap<>();
    private static int lastPhase = Integer.MIN_VALUE;
    private static long budgetNanos;
    private static final double MIN_BUDGET_MS = 0.5;
    private static double engineMs;
    private static long engineNanos;
    private static long tickStartChanges;
    /** For /solar status: averages over ~100 ticks, and the slowest engine tick since the last status. */
    public static double averageEngineMs, averageChanges, maxEngineMs;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File config = event.getSuggestedConfigurationFile();
        // the self-test runs on its own fixed config (scripts/selftest.cfg), never on the one being edited
        if (System.getProperty("solarapocalypse.selftest") != null) config = new File(config.getParentFile(), Tags.MOD_ID + "-selftest.cfg");
        SolarConfig.init(config);
        timeline = new Timeline(SolarConfig.safeDays, SolarConfig.phases);
        MinecraftForge.EVENT_BUS.register(SolarApocalypse.class);
        MinecraftForge.EVENT_BUS.register(ApocalypseClock.class);
        MinecraftForge.EVENT_BUS.register(SunDamage.class);
        MinecraftForge.EVENT_BUS.register(SolarFire.class);
        Announcer.register();
        if (event.getSide().isClient()) MinecraftForge.EVENT_BUS.register(com.solsticeentertainment.solarapocalypse.client.SplashOverlay.class);
        SurfaceRecord.register();
        if (CUBIC_CHUNKS) CubeEngine.register();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new SolarCommand());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        ApocalypseClock.attach(server);
        rebuild();
        // Dev checks (scripts/probe_server.sh); they reference CC classes, so they are only loaded with CC present.
        if (System.getProperty("solarapocalypse.probe") != null && CUBIC_CHUNKS) {
            com.solsticeentertainment.solarapocalypse.debug.CubicProbe.run(server);
        }
        if (System.getProperty("solarapocalypse.selftest") != null && CUBIC_CHUNKS) {
            com.solsticeentertainment.solarapocalypse.debug.SelfTest.run(server);
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ApocalypseClock.detach();
        CHANGES.clear();
        if (CUBIC_CHUNKS) CubicSky.reset();
        lastPhase = Integer.MIN_VALUE;
    }

    /** Re-reads the config, recompiles the block rules and queues every loaded cube again. */
    public static void reload() {
        SolarConfig.load();
        rebuild();
    }

    private static void rebuild() {
        timeline = new Timeline(SolarConfig.safeDays, SolarConfig.phases);
        rules = BlockRules.compile(SolarConfig.phases);
        summarize();
        CHANGES.clear();
        if (CUBIC_CHUNKS) CubicSky.reset();
        requeueAll();
    }

    /** Logs the phase plan once per load, and warns about settings that do not do what they say. */
    private static void summarize() {
        int[] previous = new int[2]; // per depth line
        boolean infinite = false;
        for (int i = 0; i < timeline.phaseCount(); i++) {
            SolarConfig.Phase p = SolarConfig.phases[i];
            int line = timeline.track(i);
            if (infinite && p.depth != SolarConfig.INFINITE) {
                LOGGER.warn("phase_{}.depth {} follows an infinite phase; every phase after an infinite one is infinite", i + 1, p.depth);
            }
            infinite |= p.depth == SolarConfig.INFINITE;
            if (p.depth != SolarConfig.INFINITE && p.depth < previous[line]) {
                LOGGER.warn("phase_{}.depth {} is lower than an earlier phase's {} on the same reference; depth never decreases, using {}",
                        i + 1, p.depth, previous[line], previous[line]);
            }
            if (p.depth != SolarConfig.INFINITE) previous[line] = Math.max(previous[line], p.depth);
            LOGGER.info("Phase {}: days {} to {}, depth {} below {}{}, {} convert rules ({} block states), {} destroy selectors ({} block"
                            + " states), {} liquid states start evaporating",
                    i + 1, String.format("%.2f", timeline.start(i) / (double) Timeline.DAY), String.format("%.2f", timeline.end(i) / (double) Timeline.DAY),
                    p.depth == SolarConfig.INFINITE ? "infinite" : String.valueOf(p.depth), line == Timeline.TOP ? "TOP_Y" : "SURFACE",
                    layerTime(i), p.convert.length, rules.convertCount(i), p.destroy.length, rules.destroyCount(i), rules.evaporateCount(i));
        }
    }

    /** ", 16 layers a day (a layer every 75 s at 20-minute days)" for phases that descend at a rate. */
    private static String layerTime(int phase) {
        double perDay = timeline.layersPerDay(phase);
        if (perDay <= 0) return "";
        boolean sun = SolarConfig.clockMode == SolarConfig.ClockMode.SUN;
        double seconds = (sun ? 1200 : SolarConfig.ticksPerDay / 20.0) / perDay;
        return String.format(" at %.4g layers a day (a layer every %.4g s%s)", perDay, seconds, sun ? " at 20-minute days" : "");
    }

    /** After a jump in time or a new phase: every loaded cube may have work again. */
    public static void requeueAll() {
        if (!CUBIC_CHUNKS) return;
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (isActive(world) && isCubic(world)) CubeEngine.queueAll(world);
        }
    }

    public static Timeline timeline() {
        return timeline;
    }

    public static boolean isActive(World world) {
        return SolarConfig.dimensions.contains(world.provider.getDimension());
    }

    public static boolean isCubic(World world) {
        return CUBIC_CHUNKS && CubicSky.isCubic(world);
    }

    public static BlockChanges changes(World world) {
        return CHANGES.computeIfAbsent(world, w -> new BlockChanges(timeline, rules, evaporationTopY(w)));
    }

    /** world.topY, resolving 'auto'. */
    public static int topY(World world) {
        if (SolarConfig.topY != SolarConfig.AUTO) return SolarConfig.topY;
        return isCubic(world) ? CubicSky.maxGenerationHeight(world) : world.getActualHeight();
    }

    private static int evaporationTopY(World world) {
        if (SolarConfig.evaporationTopY != SolarConfig.AUTO) return SolarConfig.evaporationTopY;
        return CubicWorldGenWater.level(world);
    }

    /** Stops liquids from making new sources while their evaporation runs (only matters with blocks.blockPhysics). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFluidSource(BlockEvent.CreateFluidSourceEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !SolarConfig.blockPhysics || !isActive(world)) return;
        int phase = rules.evaporationPhase(event.getState());
        if (phase >= 0 && ApocalypseClock.progress() >= timeline.start(phase)) event.setResult(Event.Result.DENY);
    }

    /**
     * Whether block changes may still run in this server tick: engine time spent in it, and change count. The engine runs
     * after the world's own tick, so this is time spent, not a deadline from the tick's start.
     */
    public static boolean hasBudget() {
        return CubeEngine.nanos - engineNanos < budgetNanos && mayChange();
    }

    /** Whether performance.maxBlockChangesPerTick allows another change in this tick. */
    public static boolean mayChange() {
        return SolarConfig.maxBlockChangesPerTick <= 0 || BlockChanges.changed - tickStartChanges < SolarConfig.maxBlockChangesPerTick;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        double averageMs = MathHelper.average(server.tickTimeArray) * 1.0E-6;
        if (CUBIC_CHUNKS) {
            long used = CubeEngine.nanos;
            double ms = (used - engineNanos) * 1.0E-6;
            engineMs = engineMs * 0.99 + ms * 0.01; // ~100-tick average, like tickTimeArray
            maxEngineMs = Math.max(maxEngineMs, ms);
            engineNanos = used;
        }
        averageEngineMs = engineMs;
        averageChanges = averageChanges * 0.99 + (BlockChanges.changed - tickStartChanges) * 0.01;
        tickStartChanges = BlockChanges.changed;
        double freeMs = Math.max(0, 50 - (averageMs - engineMs));
        double budgetMs = Math.max(MIN_BUDGET_MS, Math.min(SolarConfig.tickBudgetMs, SolarConfig.freeTickShare * freeMs));
        budgetNanos = (long) (budgetMs * 1.0E6);
        long progress = ApocalypseClock.progress();
        int phase = timeline.phaseAt(progress);
        if (phase != lastPhase) {
            if (lastPhase != Integer.MIN_VALUE) {
                if (phase < 0) LOGGER.info("The sun is calm again");
                else Announcer.phaseStarted(server, phase, progress);
                requeueAll();
            }
            lastPhase = phase;
        }
    }
}
