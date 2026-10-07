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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.WeakHashMap;

// Cubic Chunks is optional: "after:" only orders loading, it never requires the mod.
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, dependencies = "after:cubicchunks", acceptableRemoteVersions = "*")
public class SolarApocalypse {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    private static final boolean CUBIC_CHUNKS = Loader.isModLoaded("cubicchunks");

    private static Timeline timeline;
    private static BlockRules rules;
    private static final Map<World, BlockChanges> CHANGES = new WeakHashMap<>();
    private static int lastPhase = Integer.MIN_VALUE;
    private static long budgetEnd;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SolarConfig.init(event.getSuggestedConfigurationFile());
        timeline = new Timeline(SolarConfig.safeDays, SolarConfig.phases);
        MinecraftForge.EVENT_BUS.register(SolarApocalypse.class);
        MinecraftForge.EVENT_BUS.register(ApocalypseClock.class);
        MinecraftForge.EVENT_BUS.register(SunDamage.class);
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
        CHANGES.clear();
        if (CUBIC_CHUNKS) CubicSky.reset();
        requeueAll();
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

    /** Whether block changes may still run in this server tick. */
    public static boolean hasBudget() {
        return System.nanoTime() < budgetEnd;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        double averageMs = MathHelper.average(server.tickTimeArray) * 1.0E-6;
        double budgetMs = averageMs > SolarConfig.lagThresholdMs ? Math.min(1, SolarConfig.tickBudgetMs) : SolarConfig.tickBudgetMs;
        budgetEnd = System.nanoTime() + (long) (budgetMs * 1.0E6);
        int phase = timeline.phaseAt(ApocalypseClock.progress());
        if (phase != lastPhase) {
            if (lastPhase != Integer.MIN_VALUE) {
                LOGGER.info(phase < 0 ? "The sun is calm again" : "Solar apocalypse phase {} begins", phase + 1);
                requeueAll();
            }
            lastPhase = phase;
        }
    }
}
