package com.solsticeentertainment.solarapocalypse.debug;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.BlockRules;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import com.solsticeentertainment.solarapocalypse.cc.CubicSky;
import com.solsticeentertainment.solarapocalypse.cc.CwgSurface;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.Sky;
import com.solsticeentertainment.solarapocalypse.Timeline;
import com.solsticeentertainment.solarapocalypse.cc.CubeEngine;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * Dev-only end-to-end check on a fresh Cubic Chunks world with the default config (-Dsolarapocalypse.selftest,
 * scripts/probe_server.sh <tag> selftest). Jumps the clock through the phases, lets the engine catch the loaded spawn
 * cubes up, counts blocks around spawn and checks sun damage on three pigs. Logs "SOLAR TEST" lines, then stops the server.
 */
public final class SelfTest {

    private static final int RADIUS = 6; // columns around spawn to count
    private static MinecraftServer server;
    private static WorldServer world;
    private static int stage, ticks, waited;
    private static EntityPig sunPig, roofPig, deepPig;
    private static long engineNanos, engineCubes, engineBlocks;
    private static int fireCount, cap, planeY;
    private static long lastChanged, maxChanges;
    private static final float[] firstHit = new float[2];

    private SelfTest() {}

    public static void run(MinecraftServer s) {
        server = s;
        world = s.getWorld(0);
        MinecraftForge.EVENT_BUS.register(SelfTest.class);
    }

    private static void log(String format, Object... args) {
        SolarApocalypse.LOGGER.info("SOLAR TEST " + format, args);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        Timeline t = SolarApocalypse.timeline();
        switch (stage) {
            case 0:
                if (ticks < 40) return; // let spawn cubes finish lighting
                log("depth reference {}, CubicWorldGen model {}, phases start {} end {}", SolarConfig.depthReference,
                        CubicSky.model(world) != null, t.firstStart(), t.end(t.phaseCount() - 1));
                modelCheck();
                rulesCheck();
                cap = SolarConfig.maxBlockChangesPerTick;
                SolarConfig.maxBlockChangesPerTick = 0; // catch-ups at full speed; the cap is checked at the end
                SolarConfig.phases[3].message = "&6The ground cracks"; // the phase 4 start log line carries it
                census("fresh world");
                spawnPigs();
                jump(t.end(0) - 1, "end of phase 1, every world tick made 12 ms slower");
                break;
            case 1:
                if (!drained(600)) return;
                census("after phase 1");
                pigs("phase 1 (fire only)");
                // Entities stop ticking 300 ticks after the last player left, so hurt cooldowns never run out here:
                // reset them and check one hit (phase 4: sun 4 + background 1, background reaches 3 blocks of cover).
                for (EntityPig pig : new EntityPig[]{sunPig, roofPig, deepPig}) {
                    pig.setHealth(pig.getMaxHealth());
                    pig.extinguish();
                    pig.hurtResistantTime = 0;
                }
                jump(t.start(3) + 1, "start of phase 4");
                break;
            case 2:
                // the first hit only: with entities still ticking, hurt cooldowns can run out and let a second one in
                if (firstHit[0] == 0 && sunPig.getHealth() < sunPig.getMaxHealth()) firstHit[0] = sunPig.getHealth();
                if (firstHit[1] == 0 && roofPig.getHealth() < roofPig.getMaxHealth()) firstHit[1] = roofPig.getHealth();
                if (ticks - waited < 25) return;
                log("pigs after the first hit in phase 4 (expect sun 5, roof 9, deep 10): sun {}, roof {}, deep {}",
                        firstHit[0], firstHit[1], deepPig.getHealth());
                jump(t.end(3) - 1, "end of phase 4 (fire: 10 % solar, 75 % of flammables)");
                break;
            case 3:
                if (!drained(3000)) return;
                fireCount = fire("end of phase 4");
                stage++;
                waited = ticks;
                break;
            case 4:
                if (ticks - waited < 200) return;
                log("solar fire after 200 more ticks: {} (was {}; it must not spread or burn out)", fire("200 ticks later"), fireCount);
                jump(t.start(4) + Timeline.days(0.5), "phase 5 + 0.5 days");
                break;
            case 5:
                if (!drained(3000)) return;
                fire("erosion (15 % per layer, earlier fire removed)");
                census("erosion");
                long depth = (long) t.depthAt(ApocalypseClock.progress(), Timeline.SURFACE);
                lineCheck(depth + " layers of erosion", (x, z) -> {
                    int ground = CubeEngine.groundAt(world, x, z);
                    return ground == BlockChanges.NO_Y ? BlockChanges.NO_Y : (int) (ground - depth + 1);
                });
                SolarConfig.maxBlockChangesPerTick = cap;
                jump(t.start(4) + Timeline.days(2), "phase 5 + 2 days, block changes capped at " + cap + " per tick");
                lastChanged = BlockChanges.changed;
                break;
            case 6:
                maxChanges = Math.max(maxChanges, BlockChanges.changed - lastChanged);
                lastChanged = BlockChanges.changed;
                if (ticks - waited < 100) return;
                log("most block changes in one tick over 100 ticks: {} (cap {}), {} cubes still queued", maxChanges, cap,
                        CubeEngine.queued(world));
                SolarConfig.maxBlockChangesPerTick = 0;
                // below what the SURFACE line (frozen during phase 6) leaves of the highest ground, so the plane has terrain to cut
                planeY = highestGround() - (int) t.depthAt(t.start(5), Timeline.SURFACE) - 8;
                jump(t.reachTime(planeY, SolarApocalypse.topY(world), Timeline.TOP),
                        "phase 6 (TOP_Y line from Y " + SolarApocalypse.topY(world) + ") down to Y " + planeY);
                break;
            case 7:
                if (!drained(3000)) return;
                long top = SolarApocalypse.topY(world) - (long) t.depthAt(ApocalypseClock.progress(), Timeline.TOP) + 1;
                lineCheck("TOP_Y line at Y " + top, (x, z) -> (int) top);
                server.initiateShutdown();
                stage++;
                break;
            default:
        }
    }

    private static void jump(long progress, String what) {
        ApocalypseClock.set(progress);
        SolarApocalypse.requeueAll();
        log("--- jump to {} (day {}, depth {} layers), {} cubes queued", what, String.format("%.2f", progress / (double) Timeline.DAY),
                (long) SolarApocalypse.timeline().depthAt(progress, Timeline.SURFACE), CubeEngine.queued(world));
        engineNanos = CubeEngine.nanos;
        engineCubes = CubeEngine.cubesProcessed;
        engineBlocks = BlockChanges.changed;
        waited = ticks;
        stage++;
    }

    private static boolean drained(int maxTicks) {
        int queued = CubeEngine.queued(world);
        if (queued > 0 && ticks - waited < maxTicks) {
            if ((ticks - waited) % 100 == 0) log("  {} ticks: {} cubes queued", ticks - waited, queued);
            return false;
        }
        log("  caught up in {} ticks ({} queued left): {} cube passes, {} blocks changed, {} ms engine time", ticks - waited, queued,
                CubeEngine.cubesProcessed - engineCubes, BlockChanges.changed - engineBlocks,
                String.format("%.1f", (CubeEngine.nanos - engineNanos) / 1e6));
        return true;
    }

    private static void spawnPigs() {
        BlockPos spawn = world.getSpawnPoint();
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        int x = spawn.getX(), z = spawn.getZ();
        sunPig = pig(x, top(cubes, x, z) + 1, z);
        int rx = x + 4, ry = top(cubes, rx, z) + 1;
        world.setBlockState(new BlockPos(rx, ry + 3, z), Blocks.STONE.getDefaultState(), 18); // a one-block roof
        roofPig = pig(rx, ry, z);
        int dx = x + 8, dy = top(cubes, dx, z) - 24;
        world.setBlockState(new BlockPos(dx, dy, z), Blocks.AIR.getDefaultState(), 18);
        world.setBlockState(new BlockPos(dx, dy + 1, z), Blocks.AIR.getDefaultState(), 18);
        deepPig = pig(dx, dy, z);
        for (EntityPig pig : new EntityPig[]{sunPig, roofPig, deepPig}) {
            BlockPos eye = new BlockPos(pig.posX, pig.posY + pig.getEyeHeight(), pig.posZ);
            log("pig at {}: sun {}, heat {}", eye, Sky.at(world, eye), Sky.heat(world, eye));
        }
    }

    private static EntityPig pig(int x, int y, int z) {
        EntityPig pig = new EntityPig(world);
        pig.setLocationAndAngles(x + 0.5, y, z + 0.5, 0, 0);
        pig.enablePersistence();
        pig.setNoAI(true);
        world.spawnEntity(pig);
        return pig;
    }

    private static void pigs(String when) {
        for (EntityPig pig : new EntityPig[]{sunPig, roofPig, deepPig}) {
            BlockPos eye = new BlockPos(pig.posX, pig.posY + pig.getEyeHeight(), pig.posZ);
            log("  pig at {}: sun {} (1 = exposed), heat {}, day {}", eye, Sky.at(world, eye), Sky.heat(world, eye), world.isDaytime());
        }
        log("pigs after {}: sun {} hp burning={}, roof {} hp burning={}, deep {} hp burning={}", when,
                sunPig.getHealth(), sunPig.isBurning(), roofPig.getHealth(), roofPig.isBurning(), deepPig.getHealth(), deepPig.isBurning());
    }

    /** Stage 1 runs with the world's own tick 12 ms slow, more than the 10 ms budget: the engine must still get its time. */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (stage != 1 || event.phase != TickEvent.Phase.START || event.world != world) return;
        long until = System.nanoTime() + 12_000_000L;
        while (System.nanoTime() < until) Thread.yield();
    }

    /** Chances, selector exclusions, loop cutting and the vanilla fire blacklist, on a throwaway two-phase rule set (CARRY). */
    private static void rulesCheck() {
        SolarConfig.Phase a = new SolarConfig.Phase(), b = new SolarConfig.Phase();
        a.convert = new String[]{"minecraft:dirt -> minecraft:sand", "minecraft:gravel -> minecraft:sand @ 30%",
                "minecraft:gravel -> minecraft:clay @ 20"};
        a.destroy = new String[]{"material:rock, !minecraft:cobblestone"};
        b.convert = new String[]{"minecraft:sand -> minecraft:dirt"};
        b.destroy = new String[0];
        a.evaporate = new String[]{"material:water, !temperature>=1000"};
        b.evaporate = new String[]{"fluid:lava"};
        BlockRules r = BlockRules.compile(new SolarConfig.Phase[]{a, b});
        log("rules: evaporation from phase water {} (expect 0), flowing water {} (0), lava {} (1), flowing lava {} (1), stone {} (-1)",
                r.evaporationPhase(Blocks.WATER.getDefaultState()), r.evaporationPhase(Blocks.FLOWING_WATER.getDefaultState()),
                r.evaporationPhase(Blocks.LAVA.getDefaultState()), r.evaporationPhase(Blocks.FLOWING_LAVA.getDefaultState()),
                r.evaporationPhase(Blocks.STONE.getDefaultState()));
        IBlockState dirt = Blocks.DIRT.getDefaultState(), sand = Blocks.SAND.getDefaultState(), gravel = Blocks.GRAVEL.getDefaultState();
        BlockRules.Step g = r.convert(0, gravel), d = r.convert(1, dirt), s = r.convert(1, sand);
        int toSand = 0, toClay = 0, same = 0;
        for (int i = 0; i < 10000; i++) {
            IBlockState to = g.pick(i, 64, i * 7, gravel);
            if (to == sand) toSand++;
            else if (to == Blocks.CLAY.getDefaultState()) toClay++;
            if (to == g.pick(i, 64, i * 7, gravel)) same++;
        }
        log("rules: gravel of 10000 blocks -> {} sand (expect ~3000), {} clay (~2000), same on a second look {} (10000);"
                        + " stone destroyed in phase {} (expect 0), cobblestone {} (-1); loop cut in phase 2: dirt -> {} (none),"
                        + " sand -> {} (dirt); TNT blacklisted for vanilla fire {} (true)", toSand, toClay, same,
                r.destroyPhase(0, Blocks.STONE.getDefaultState()), r.destroyPhase(0, Blocks.COBBLESTONE.getDefaultState()),
                d == null ? "none" : d, s == null ? "none" : s, r.noVanillaFire(Blocks.TNT.getDefaultState()));
    }

    /** Counts solar fire (by epoch) and vanilla fire in the loaded spawn columns; returns the solar fire count. */
    private static int fire(String when) {
        CubeEngine.FireCensus c = CubeEngine.fireCensus(world, world.getSpawnPoint(), RADIUS);
        log("fire at {}: solar {} ({} % of {} x/z positions, by epoch {}), vanilla {}", when, c.solar,
                String.format("%.1f", c.solar * 100.0 / (c.columns * 256)), c.columns * 256, c.epochs, c.vanilla);
        return c.solar;
    }

    /** Compares the CubicWorldGen surface model with the generated ground of the loaded spawn columns. */
    private static void modelCheck() {
        CwgSurface model = CubicSky.model(world);
        if (model == null) return;
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int exact = 0, near = 0, lower = 0, higher = 0, unknown = 0, worst = 0;
        long t0 = System.nanoTime();
        int columns = 0;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                if (cubes.getLoadedColumn(cx, cz) == null) continue;
                columns++;
                for (int i = 0; i < 256; i++) model.top((cx << 4) + (i & 15), (cz << 4) + (i >> 4));
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                if (cubes.getLoadedColumn(cx, cz) == null) continue;
                for (int i = 0; i < 256; i++) {
                    int x = (cx << 4) + (i & 15), z = (cz << 4) + (i >> 4);
                    int ground = ground(cubes, x, z);
                    if (ground == Integer.MIN_VALUE) {
                        unknown++;
                        continue;
                    }
                    int d = ground - model.top(x, z);
                    if (d == 0) exact++;
                    else if (Math.abs(d) == 1) near++;
                    else if (d < 0) lower++;
                    else higher++;
                    if (d > 0) worst = Math.max(worst, d);
                }
            }
        }
        log("CWG surface model, {} columns in {} ms: ground = model {}, off by 1 {}, lower (caves, lakes) {}, higher {} (max +{}), unknown {}",
                columns, String.format("%.1f", ms), exact, near, lower, higher, worst, unknown);
    }

    /** Topmost ground block (BlockChanges.isGround) under the column's top, from loaded cubes; MIN_VALUE if not loaded. */
    private static int ground(ICubeProvider cubes, int x, int z) {
        for (int y = top(cubes, x, z), n = 0; n < 64; y--, n++) {
            ICube cube = cubes.getLoadedCube(x >> 4, y >> 4, z >> 4);
            if (cube == null) return Integer.MIN_VALUE;
            ExtendedBlockStorage storage = cube.getStorage();
            if (storage != null && BlockChanges.isGround(storage.get(x & 15, y & 15, z & 15))) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** After erosion to `depth` layers: counts blocks left above reference - depth + 1 in the loaded spawn columns. */
    /** Checks that nothing is left at or above a line (lowest Y that must be gone per x, z) in the ready spawn cubes. */
    private static void lineCheck(String what, IntBinaryOperator lowestGone) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int left = 0, checked = 0, noReference = 0, cubesSeen = 0, highest = Integer.MIN_VALUE;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                Chunk column = cubes.getLoadedColumn(cx, cz);
                if (column == null) continue;
                for (ICube cube : new ArrayList<>(((IColumn) column).getLoadedCubes())) {
                    ExtendedBlockStorage storage = cube.getStorage();
                    if (storage == null || storage.isEmpty() || !cube.isSurfaceTracked()) continue;
                    cubesSeen++;
                    for (int i = 0; i < 4096; i++) {
                        if (storage.get(i & 15, i >> 8, (i >> 4) & 15).getMaterial() != Material.AIR) highest = Math.max(highest, cube.getY() * 16 + (i >> 8));
                    }
                    for (int i = 0; i < 256; i++) {
                        int x = (cx << 4) + (i & 15), z = (cz << 4) + (i >> 4);
                        int gone = lowestGone.applyAsInt(x, z);
                        if (gone == BlockChanges.NO_Y) {
                            noReference++;
                            continue;
                        }
                        for (int ly = 0; ly < 16; ly++) {
                            int y = cube.getY() * 16 + ly;
                            if (y < gone) continue;
                            checked++;
                            IBlockState left0 = storage.get(i & 15, ly, i >> 4);
                            if (left0.getMaterial() != Material.AIR && !SolarFire.is(left0)) left++; // solar fire stands on the surface
                        }
                    }
                }
            }
        }
        log("line check, {}: {} positions above the line in {} ready cubes (highest block Y {}), {} still hold a block, {} without reference",
                what, checked, cubesSeen, highest, left, noReference);
    }

    /** The highest terrain surface (SURFACE reference) among the spawn columns. */
    private static int highestGround() {
        BlockPos spawn = world.getSpawnPoint();
        int highest = Integer.MIN_VALUE;
        for (int x = spawn.getX() - RADIUS * 16; x < spawn.getX() + RADIUS * 16; x++) {
            for (int z = spawn.getZ() - RADIUS * 16; z < spawn.getZ() + RADIUS * 16; z++) highest = Math.max(highest, CubeEngine.groundAt(world, x, z));
        }
        return highest;
    }

    private static int top(ICubeProvider cubes, int x, int z) {
        Chunk column = cubes.getLoadedColumn(x >> 4, z >> 4);
        return ((IColumn) column).getHeightValue(x & 15, z & 15) - 1;
    }

    /** Counts block kinds in the loaded cubes of the columns around spawn: total, and sun-exposed (at or above the top). */
    private static void census(String when) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (String k : new String[]{"grass", "dirt", "sand", "water", "lava", "ice/snow", "plants", "leaves", "wood", "stone", "other"}) {
            counts.put(k, new int[2]);
        }
        int columns = 0, cubeCount = 0, highest = Integer.MIN_VALUE, lowestTop = Integer.MAX_VALUE;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                Chunk column = cubes.getLoadedColumn(cx, cz);
                if (column == null) continue;
                columns++;
                IColumn heights = (IColumn) column;
                List<ICube> loaded = new ArrayList<>(heights.getLoadedCubes());
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) lowestTop = Math.min(lowestTop, heights.getHeightValue(lx, lz) - 1);
                for (ICube cube : loaded) {
                    ExtendedBlockStorage storage = cube.getStorage();
                    cubeCount++;
                    if (storage == null || storage.isEmpty()) continue;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int top = heights.getHeightValue(lx, lz) - 1;
                            for (int ly = 0; ly < 16; ly++) {
                                IBlockState s = storage.get(lx, ly, lz);
                                if (s.getMaterial() == Material.AIR) continue;
                                int y = cube.getY() * 16 + ly;
                                highest = Math.max(highest, y);
                                int[] c = counts.get(kind(s));
                                c[0]++;
                                if (y >= top) c[1]++;
                            }
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            if (e.getValue()[0] > 0) sb.append(e.getKey()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]).append(", ");
        }
        log("census {}: {} columns, {} cubes, highest block Y {}, lowest top Y {}; total/exposed: {}", when, columns, cubeCount,
                highest, lowestTop, sb);
    }

    private static String kind(IBlockState s) {
        Material m = s.getMaterial();
        if (s.getBlock() == Blocks.GRASS) return "grass";
        if (s.getBlock() == Blocks.DIRT) return "dirt";
        if (m == Material.SAND) return "sand";
        if (m == Material.WATER) return "water";
        if (m == Material.LAVA) return "lava";
        if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.SNOW || m == Material.CRAFTED_SNOW) return "ice/snow";
        if (m == Material.PLANTS || m == Material.VINE) return "plants";
        if (m == Material.LEAVES) return "leaves";
        if (m == Material.WOOD) return "wood";
        if (m == Material.ROCK) return "stone";
        return "other";
    }
}
