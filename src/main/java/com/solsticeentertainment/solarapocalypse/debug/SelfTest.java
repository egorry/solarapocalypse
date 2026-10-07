package com.solsticeentertainment.solarapocalypse.debug;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
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
                census("fresh world");
                spawnPigs();
                jump(t.end(0) - 1, "end of phase 1");
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
                if (ticks - waited < 25) return;
                pigs("phase 4, one hit (expect sun 5, roof 9, deep 10)");
                jump(t.start(4) + Timeline.days(0.5), "phase 5 + 0.5 days");
                break;
            case 3:
                if (!drained(3000)) return;
                census("erosion");
                erosionCheck(SolarApocalypse.timeline().depthAt(ApocalypseClock.progress()));
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
                (long) SolarApocalypse.timeline().depthAt(progress), CubeEngine.queued(world));
        engineNanos = CubeEngine.nanos;
        engineCubes = CubeEngine.cubesProcessed;
        engineBlocks = CubeEngine.blocksChanged;
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
                CubeEngine.cubesProcessed - engineCubes, CubeEngine.blocksChanged - engineBlocks,
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
    private static void erosionCheck(double depth) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int left = 0, checked = 0, noReference = 0;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                Chunk column = cubes.getLoadedColumn(cx, cz);
                if (column == null) continue;
                for (ICube cube : new ArrayList<>(((IColumn) column).getLoadedCubes())) {
                    ExtendedBlockStorage storage = cube.getStorage();
                    if (storage == null || storage.isEmpty() || !cube.isSurfaceTracked()) continue;
                    for (int i = 0; i < 256; i++) {
                        int x = (cx << 4) + (i & 15), z = (cz << 4) + (i >> 4);
                        int reference = CubeEngine.referenceAt(world, x, z);
                        if (reference == BlockChanges.NO_Y) {
                            noReference++;
                            continue;
                        }
                        for (int ly = 0; ly < 16; ly++) {
                            int y = cube.getY() * 16 + ly;
                            if (y <= reference - (long) depth) continue;
                            checked++;
                            if (storage.get(i & 15, ly, i >> 4).getMaterial() != Material.AIR) left++;
                        }
                    }
                }
            }
        }
        log("erosion check ({} layers): {} positions above the line in ready cubes, {} still hold a block, {} without reference",
                (long) depth, checked, left, noReference);
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
