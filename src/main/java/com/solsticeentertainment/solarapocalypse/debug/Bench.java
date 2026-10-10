package com.solsticeentertainment.solarapocalypse.debug;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import com.solsticeentertainment.solarapocalypse.Timeline;
import com.solsticeentertainment.solarapocalypse.cc.CubeEngine;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

/**
 * Dev-only throughput check of an infinite phase (-Dsolarapocalypse.bench, scripts/bench.cfg): the clock runs far
 * ahead, so the engine works flat out within its budget, one layer per round; logs "SOLAR BENCH" lines with the block
 * changes per layer and the time per change, then stops the server. -Dsolarapocalypse.bench=fire0 turns the fire off,
 * light0 keeps the fire but without its light.
 */
public final class Bench {

    private static final int WARMUP = 600, MEASURE = 1200; // ticks
    private static MinecraftServer server;
    private static WorldServer world;
    private static int ticks;
    private static long tickStart, serverNanos; // whole server ticks (engine, lighting, the rest) while measuring
    private static long nanos, changed, cubes, clock;

    private Bench() {}

    public static void run(MinecraftServer s) {
        server = s;
        world = s.getWorld(0);
        String variant = System.getProperty("solarapocalypse.bench");
        if ("fire0".equals(variant)) {
            for (SolarConfig.Phase p : SolarConfig.phases) p.ignitePercent = p.igniteFlammablePercent = 0;
        }
        if ("light0".equals(variant)) { // what the fire's light costs (lighting is outside the engine's budget)
            try {
                Field light = Block.class.getDeclaredField("lightValue"); // dev names
                light.setAccessible(true);
                light.setInt(SolarFire.BLOCK, 0);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        MinecraftForge.EVENT_BUS.register(Bench.class);
    }

    private static void log(String format, Object... args) {
        SolarApocalypse.LOGGER.info("SOLAR BENCH " + format, args);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStart = System.nanoTime();
            return;
        }
        if (ticks > 40 + WARMUP) serverNanos += System.nanoTime() - tickStart;
        ticks++;
        Timeline t = SolarApocalypse.timeline();
        if (ticks == 40) {
            ApocalypseClock.set(t.start(0));
            SolarApocalypse.requeueAll(true);
            log("variant {}, cap {}, budget {} ms, fire {} %", System.getProperty("solarapocalypse.bench"),
                    SolarConfig.maxBlockChangesPerTick, SolarConfig.tickBudgetMs, SolarConfig.phases[0].ignitePercent);
        }
        if (ticks < 40) return;
        ApocalypseClock.set(Math.max(ApocalypseClock.progress(), CubeEngine.clock(world) + Timeline.DAY / 10)); // always behind
        if (ticks == 40 + WARMUP) {
            nanos = CubeEngine.nanos;
            changed = BlockChanges.changed;
            cubes = CubeEngine.cubesProcessed;
            clock = CubeEngine.clock(world);
            log("measuring from engine depth {} layers", depth(clock));
        }
        if (ticks == 40 + WARMUP + MEASURE) {
            long now = CubeEngine.clock(world);
            double layers = depth(now) - depth(clock);
            long n = BlockChanges.changed - changed, ns = CubeEngine.nanos - nanos;
            log("{} ticks: {} layers, {} block changes ({} per layer, {} per tick), {} cube passes, engine {} ms per tick, {} us per change,"
                    + " server {} ms per tick ({} ms per layer)", MEASURE, String.format("%.1f", layers), n, String.format("%.0f", n / layers),
                    n / MEASURE, CubeEngine.cubesProcessed - cubes, String.format("%.2f", ns / 1e6 / MEASURE), String.format("%.2f", ns / 1e3 / n),
                    String.format("%.1f", serverNanos / 1e6 / MEASURE), String.format("%.0f", serverNanos / 1e6 / layers));
            CubeEngine.FireCensus fire = CubeEngine.fireCensus(world, world.getSpawnPoint(), 6);
            log("solar fire within 6 columns of spawn: {} ({} % of x/z positions; set {} %), by layer {}", fire.solar,
                    String.format("%.1f", 100.0 * fire.solar / (fire.columns * 256)), SolarConfig.phases[0].ignitePercent, fire.epochs);
            server.initiateShutdown();
        }
    }

    private static double depth(long clock) {
        return SolarApocalypse.timeline().depthAt(clock, Timeline.SURFACE);
    }
}
