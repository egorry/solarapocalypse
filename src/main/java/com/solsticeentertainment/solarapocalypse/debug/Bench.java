package com.solsticeentertainment.solarapocalypse.debug;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import com.solsticeentertainment.solarapocalypse.Timeline;
import com.solsticeentertainment.solarapocalypse.cc.CubeEngine;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only throughput check of an infinite phase (-Dsolarapocalypse.bench, scripts/bench.cfg): the clock runs far
 * ahead, so the engine works flat out within its budget, one layer per round; logs "SOLAR BENCH" lines with the block
 * changes per layer and the time per change, then stops the server. Variants, comma-separated
 * (-Dsolarapocalypse.bench=fire0,stone): fire0 no fire; light<n> solar fire light n; noconv no conversions; stone adds
 * stone -> cobblestone -> gravel -> sand -> vitrified sand to the gradient (layers 5 to 2); vit1 replaces the gradient
 * with dirt and stone to vitrified sand in layer 1; measure<n> measures n ticks (default 1200: a fast variant can run
 * out of loaded terrain, after which the clock steps through empty rounds); sea logs a census of the sea around spawn
 * every 100 ticks with LAYERS evaporation at the erosion's rate (how flat it drains, water over air, how far the drained
 * floors and the land came down); liquidsout keeps water and lava out of the destroy list (they go by evaporation only).
 */
public final class Bench {

    private static final int WARMUP = 600; // ticks
    private static int MEASURE = 1200;
    private static MinecraftServer server;
    private static WorldServer world;
    private static int ticks;
    private static long tickStart, serverNanos; // whole server ticks (engine, lighting, the rest) while measuring
    private static long nanos, changed, cubes, clock;
    private static boolean sea;

    private Bench() {}

    public static void run(MinecraftServer s) {
        server = s;
        world = s.getWorld(0);
        SolarConfig.Phase p = SolarConfig.phases[0];
        List<String> convert = new ArrayList<>(Arrays.asList(p.convert));
        convert.removeIf(rule -> rule.contains("layer=")); // the gradient; the trees' rules stay
        for (String variant : System.getProperty("solarapocalypse.bench").split(",")) {
            if (variant.equals("fire0")) p.ignitePercent = p.igniteFlammablePercent = 0;
            if (variant.startsWith("measure")) MEASURE = Integer.parseInt(variant.substring(7));
            if (variant.startsWith("light")) SolarConfig.solarFireLight = Integer.parseInt(variant.substring(5)); // outside the engine's budget
            if (variant.equals("noconv")) p.convert = new String[0];
            if (variant.equals("sea")) {
                sea = true;
                SolarConfig.evaporationMode = SolarConfig.EvaporationMode.LAYERS;
                SolarConfig.evaporationLayersPerDay = p.layersPerDay;
            }
            if (variant.equals("liquidsout")) {
                List<String> destroy = new ArrayList<>(Arrays.asList(p.destroy));
                destroy.addAll(Arrays.asList("!material:water", "!material:lava"));
                p.destroy = destroy.toArray(new String[0]);
            }
            if (variant.equals("stone")) {
                convert = new ArrayList<>(Arrays.asList(p.convert));
                convert.addAll(Arrays.asList("minecraft:stone -> minecraft:cobblestone layer=5",
                        "minecraft:stone, minecraft:cobblestone -> minecraft:gravel layer=4",
                        "minecraft:stone, minecraft:cobblestone -> minecraft:sand layer=3",
                        "minecraft:stone, minecraft:cobblestone -> solarapocalypse:vitrified_sand layer=2"));
                p.convert = convert.toArray(new String[0]);
            }
            if (variant.equals("vit1")) {
                convert.add("minecraft:grass, minecraft:grass_path, minecraft:dirt, minecraft:gravel, minecraft:sand, minecraft:stone"
                        + " -> solarapocalypse:vitrified_sand layer=1");
                p.convert = convert.toArray(new String[0]);
                p.convertDepth = 1;
            }
        }
        SolarApocalypse.rebuild();
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
            log("variant {}, cap {}, budget {} ms, fire {} % (light {}), {} convert rules", System.getProperty("solarapocalypse.bench"),
                    SolarConfig.maxBlockChangesPerTick, SolarConfig.tickBudgetMs, SolarConfig.phases[0].ignitePercent, SolarFire.light,
                    SolarConfig.phases[0].convert.length);
        }
        if (ticks < 40) return;
        ApocalypseClock.set(Math.max(ApocalypseClock.progress(), CubeEngine.clock(world) + Timeline.DAY / 10)); // always behind
        if (sea && ticks % 100 == 0) seaCensus();
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

    /**
     * The sea within 8 columns of spawn (the loaded spawn area): columns whose ground lies below the evaporation's top,
     * where the standing water's top is (flat: one or two values), standing water with air right under it (0: the floor
     * waits for its water), and layers lost by drained sea columns and by land columns (against the engine's line).
     */
    private static void seaCensus() {
        ICubeProvider provider = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int seaTop = SolarApocalypse.changes(world).evaporationTopY;
        Map<Integer, Integer> waterTops = new TreeMap<>(), dryLost = new TreeMap<>(), landLost = new TreeMap<>();
        int seaColumns = 0, hanging = 0;
        long since = CubeEngine.clock(world) - SolarApocalypse.timeline().start(0);
        int level = seaTop + 1 - (int) (since * SolarConfig.evaporationLayersPerDay / Timeline.DAY); // evaporation has gone down to here
        List<String> lingering = new ArrayList<>(); // standing water above that level, which the sun does not reach
        for (int x = spawn.getX() - 128; x < spawn.getX() + 128; x++) {
            for (int z = spawn.getZ() - 128; z < spawn.getZ() + 128; z++) {
                int ground = CubeEngine.groundAt(world, x, z);
                if (ground == BlockChanges.NO_Y || provider.getLoadedColumn(x >> 4, z >> 4) == null) continue;
                int waterTop = BlockChanges.NO_Y, solidTop = BlockChanges.NO_Y;
                boolean liquidAbove = false;
                for (int y = Math.max(ground, seaTop) + 1; y > ground - 160; y--) {
                    IBlockState state = CubeEngine.blockAt(provider, x >> 4, z >> 4, x & 15, y, z & 15);
                    if (state == null) break;
                    if (CubeEngine.standing(state)) {
                        if (waterTop == BlockChanges.NO_Y) waterTop = y;
                        liquidAbove = true;
                    } else if (state.getMaterial() == Material.AIR) {
                        if (liquidAbove) hanging++;
                        liquidAbove = false;
                    } else if (state.getMaterial().blocksMovement()) { // fire and plants on top are not the ground
                        solidTop = y;
                        break;
                    }
                }
                if (ground >= seaTop) {
                    if (solidTop != BlockChanges.NO_Y) landLost.merge(ground - solidTop, 1, Integer::sum);
                    continue;
                }
                seaColumns++;
                if (waterTop != BlockChanges.NO_Y) waterTops.merge(waterTop, 1, Integer::sum);
                else if (solidTop != BlockChanges.NO_Y) dryLost.merge(ground - solidTop, 1, Integer::sum);
                if (waterTop != BlockChanges.NO_Y && waterTop > level && lingering.size() < 4) {
                    int top = ((IColumn) provider.getLoadedColumn(x >> 4, z >> 4)).getHeightValue(x & 15, z & 15) - 1;
                    IBlockState cover = CubeEngine.blockAt(provider, x >> 4, z >> 4, x & 15, top, z & 15);
                    lingering.add(x + " " + waterTop + " " + z + " under " + (cover == null ? "?" : cover.getBlock().getRegistryName()) + " at " + top);
                }
                else if (solidTop != BlockChanges.NO_Y) dryLost.merge(ground - solidTop, 1, Integer::sum);
            }
        }
        log("sea at line {} layers (engine): {} sea columns (ground below Y {}), {} still wet, water top Y by columns {};"
                        + " standing water over air {} (0); drained sea floors' layers lost {}; land columns' layers lost {}; water above the"
                        + " evaporation level (Y {}), e.g. {}",
                String.format("%.0f", depth(CubeEngine.clock(world))), seaColumns, seaTop, waterTops.values().stream().mapToInt(Integer::intValue).sum(),
                waterTops, hanging, dryLost, landLost, level, lingering);
    }

    private static double depth(long clock) {
        return SolarApocalypse.timeline().depthAt(clock, Timeline.SURFACE);
    }
}
