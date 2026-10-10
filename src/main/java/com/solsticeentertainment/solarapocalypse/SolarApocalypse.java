package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.cc.CubeEngine;
import com.solsticeentertainment.solarapocalypse.cc.CubicSky;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.WorldInfo;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Cubic Chunks is optional: "after:" only orders loading, it never requires the mod.
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, dependencies = "after:cubicchunks")
public class SolarApocalypse {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    private static final boolean CUBIC_CHUNKS = Loader.isModLoaded("cubicchunks");

    private static Timeline timeline;
    private static final Map<Integer, BlockRules> RULES = new HashMap<>(); // per dimension (rules can be scoped)
    private static int boostUntil = Integer.MIN_VALUE; // server tick until which a /time skip catches up faster
    private static boolean boost;
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
        // the self-test and the bench run on their own fixed configs (scripts/), never on the one being edited
        if (System.getProperty("solarapocalypse.selftest") != null) config = new File(config.getParentFile(), Tags.MOD_ID + "-selftest.cfg");
        if (System.getProperty("solarapocalypse.bench") != null) config = new File(config.getParentFile(), Tags.MOD_ID + "-bench.cfg");
        SolarConfig.init(config);
        timeline = new Timeline(SolarConfig.safeDays, SolarConfig.phases);
        MinecraftForge.EVENT_BUS.register(SolarApocalypse.class);
        MinecraftForge.EVENT_BUS.register(ApocalypseClock.class);
        MinecraftForge.EVENT_BUS.register(SunDamage.class);
        MinecraftForge.EVENT_BUS.register(SolarFire.class);
        MinecraftForge.EVENT_BUS.register(VitrifiedSand.class);
        Announcer.register();
        if (event.getSide().isClient()) MinecraftForge.EVENT_BUS.register(com.solsticeentertainment.solarapocalypse.client.SplashOverlay.class);
        if (event.getSide().isClient()) MinecraftForge.EVENT_BUS.register(com.solsticeentertainment.solarapocalypse.client.VitrifiedSandModel.class);
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
        if (System.getProperty("solarapocalypse.bench") != null && CUBIC_CHUNKS) {
            com.solsticeentertainment.solarapocalypse.debug.Bench.run(server);
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
        RULES.clear();
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
                    layerTime(i), p.convert.length, rules(0).convertCount(i), p.destroy.length, rules(0).destroyCount(i),
                    rules(0).evaporateCount(i));
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

    /** After a config load or reload: every loaded cube may have work again, at the engine's clock. */
    public static void requeueAll() {
        requeueAll(false);
    }

    /** Every loaded cube may have work again; jump (the time was changed by command) brings the engine to the present. */
    public static void requeueAll(boolean jump) {
        if (!CUBIC_CHUNKS) return;
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (isActive(world) && isCubic(world)) CubeEngine.queueAll(world, jump);
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
        return CHANGES.computeIfAbsent(world, w -> new BlockChanges(timeline, rules(w.provider.getDimension()), evaporationTopY(w)));
    }

    /** The block rules of a dimension (entries scoped with dimensions=... apply only where they say), compiled on first use. */
    public static BlockRules rules(int dimension) {
        return RULES.computeIfAbsent(dimension, d -> BlockRules.compile(SolarConfig.phases, d));
    }

    /**
     * The clock skipped ahead (sleeping, /time set, /time add; ApocalypseClock). After sleeping the engine carries on a
     * step per round from where it was (even, the world catching up behind the clock). A /time command brings the
     * engine's clock straight to the present, with the bigger performance.skip* budget for a while; players whose ground
     * an infinite phase's erosion took during it die.
     */
    static void skipped(long from, long to, boolean command) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (command && SolarConfig.skipBoostSeconds > 0) boostUntil = server.getTickCounter() + SolarConfig.skipBoostSeconds * 20;
        LOGGER.info("Time skip of {} days ({}){}", String.format("%.2f", (to - from) / (double) Timeline.DAY), command ? "/time" : "sleep",
                command && SolarConfig.skipBoostSeconds > 0 ? ", catching up faster for " + SolarConfig.skipBoostSeconds + " s" : "");
        if (!command) return;
        if (CUBIC_CHUNKS) for (WorldServer world : DimensionManager.getWorlds()) if (isActive(world) && isCubic(world)) CubeEngine.jump(world, to);
        int phase = timeline.phaseAt(to);
        if (phase < 0 || !timeline.infinite(phase)) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            World world = player.world;
            if (!isActive(world) || !isCubic(world) || player.isCreative() || player.isSpectator()) continue;
            BlockPos ground = new BlockPos(player.posX, player.posY - 0.5, player.posZ); // the block (or bed) they are on
            if (!world.isBlockLoaded(ground)) continue;
            IBlockState state = world.getBlockState(ground);
            int surface = CubeEngine.groundAt(world, ground.getX(), ground.getZ());
            IBlockState after = changes(world).evaluate(state, ground, surface, topY(world), BlockChanges.NO_Y, Sky.UNKNOWN, to);
            if (state.getMaterial() != Material.AIR && after.getMaterial() == Material.AIR) {
                LOGGER.info("{} stood where the erosion passed during the skip and dies", player.getName());
                player.attackEntityFrom(SunDamage.SUN, Float.MAX_VALUE);
            }
        }
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
        int phase = rules(world.provider.getDimension()).evaporationPhase(event.getState());
        if (phase >= 0 && ApocalypseClock.progress() >= timeline.start(phase)) event.setResult(Event.Result.DENY);
    }

    /**
     * Whether block changes may still run in this server tick: engine time spent in it, and change count. The engine runs
     * after the world's own tick, so this is time spent, not a deadline from the tick's start.
     */
    public static boolean hasBudget() {
        return hasBudget(0);
    }

    /** hasBudget, counting engine time not yet added to CubeEngine.nanos (the cube being processed). */
    public static boolean hasBudget(long pendingNanos) {
        return CubeEngine.nanos + pendingNanos - engineNanos < budgetNanos && mayChange();
    }

    /** Whether performance.maxBlockChangesPerTick (skipMaxBlockChangesPerTick right after a /time skip) allows another change. */
    public static boolean mayChange() {
        int cap = boost ? SolarConfig.skipMaxBlockChangesPerTick : SolarConfig.maxBlockChangesPerTick;
        return cap <= 0 || BlockChanges.changed - tickStartChanges < cap;
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
        boost = server.getTickCounter() < boostUntil;
        if (boost) budgetMs = Math.max(budgetMs, SolarConfig.skipTickBudgetMs);
        budgetNanos = (long) (budgetMs * 1.0E6);
        long progress = ApocalypseClock.progress();
        int phase = timeline.phaseAt(progress);
        if (phase != lastPhase) {
            if (lastPhase != Integer.MIN_VALUE) {
                if (phase < 0) LOGGER.info("The sun is calm again");
                else Announcer.phasesStarted(phase > lastPhase ? Math.max(lastPhase + 1, 0) : phase, phase, progress);
                // the engine looks at every loaded cube itself when its clock gets to the phase's start
            }
            lastPhase = phase;
        }
        Announcer.tick(server);
        if (phase >= 0 && server.getTickCounter() % 20 == 0) holdWeather(SolarConfig.phases[phase].weather);
    }

    /** Vanilla's rain counter is set to this: the rain (or dry spell) lasts half a day after a phase stops holding it. */
    private static final int HOLD_WEATHER = 12000;

    /**
     * Holds a phase's weather (phase_n.weather) in the apocalypse's dimensions that have weather; UNCHANGED leaves it alone.
     * Dimensions that share the overworld's weather (vanilla's DerivedWorldInfo ignores changes) are held through it.
     * Rain and thunder only end together after a storm: otherwise the thunder counter stays at least two days, so a dry
     * spell does not end in a thunderstorm.
     */
    private static void holdWeather(SolarConfig.Weather weather) {
        if (weather == SolarConfig.Weather.UNCHANGED) return;
        Set<WorldInfo> held = Collections.newSetFromMap(new IdentityHashMap<>());
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (!isActive(world) || !world.provider.hasSkyLight()) continue;
            WorldInfo info = world.getWorldInfo() instanceof DerivedWorldInfo ? DimensionManager.getWorld(0).getWorldInfo() : world.getWorldInfo();
            if (!held.add(info)) continue;
            boolean thunder = weather == SolarConfig.Weather.THUNDER;
            info.setCleanWeatherTime(0);
            info.setRainTime(HOLD_WEATHER);
            info.setThunderTime(thunder ? HOLD_WEATHER : Math.max(info.getThunderTime(), 4 * HOLD_WEATHER));
            info.setRaining(weather != SolarConfig.Weather.NONE);
            info.setThundering(thunder);
        }
    }
}
