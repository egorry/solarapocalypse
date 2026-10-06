package com.solsticeentertainment.solarapocalypse.debug;

import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import io.github.opencubicchunks.cubicchunks.api.world.CubeEvent;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.IHeightMap;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer.Requirement.LIGHT;

/**
 * Research probe for docs/RESEARCH.md ("Empirical checks"). Dev only: runs once after server start when the JVM has
 * -Dsolarapocalypse.probe and Cubic Chunks is loaded, logs "SOLAR PROBE" lines, then stops the server.
 * References CC classes: only load it behind Loader.isModLoaded("cubicchunks").
 */
public final class CubicProbe {

    private static final int CX = 40000, CZ = 40000;      // block 640000: a column nobody has generated
    private static final int DEEP_LOW = -52, DEEP_HIGH = -48; // cube Ys: blocks -832..-753
    private static final int SURFACE_HIGH = 8;               // cube Y 8 = blocks 128..143

    private int loads, loadsPopulated, loadsLit, loadsTracked;

    @SubscribeEvent
    public void onCubeLoad(CubeEvent.Load event) {
        ICube cube = event.getCube();
        loads++;
        if (cube.isFullyPopulated()) loadsPopulated++;
        if (cube.isInitialLightingDone()) loadsLit++;
        if (cube.isSurfaceTracked()) loadsTracked++;
    }

    public static void run(MinecraftServer server) {
        WorldServer world = server.getWorld(0);
        if (!((ICubicWorld) world).isCubicWorld()) {
            log("overworld is not cubic, nothing to probe");
        } else {
            CubicProbe probe = new CubicProbe();
            MinecraftForge.EVENT_BUS.register(probe);
            try {
                probe.probe(world);
            } catch (RuntimeException e) {
                SolarApocalypse.LOGGER.error("SOLAR PROBE failed", e);
            }
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        server.initiateShutdown();
    }

    private void probe(WorldServer world) {
        ICubeProviderServer cubes = ((ICubicWorldServer) world).getCubeCache();
        int x0 = CX << 4, z0 = CZ << 4;

        // 1. Deep-only column: what the heightmap says when the surface was never generated.
        long t = System.nanoTime();
        for (int cy = DEEP_LOW; cy <= DEEP_HIGH; cy++) cubes.getCube(CX, cy, CZ, LIGHT);
        log("deep cubes %d..%d to LIGHT: %d ms, %d cube loads (populated/lit/tracked at load: %d/%d/%d)",
                DEEP_LOW, DEEP_HIGH, ms(t), loads, loadsPopulated, loadsLit, loadsTracked);
        int highestGenerated = Integer.MIN_VALUE;
        for (int cy = DEEP_HIGH; cy <= SURFACE_HIGH; cy++) if (cubes.isCubeGenerated(CX, cy, CZ)) highestGenerated = cy;
        log("highest generated cube above the deep range: %d (surface cube 4 generated: %b)",
                highestGenerated, cubes.isCubeGenerated(CX, 4, CZ));
        IHeightMap heights = ((IColumn) cubes.getLoadedColumn(CX, CZ)).getOpacityIndex();
        logTops(heights, "deep-only column");
        logExposure(world, cubes, "before surface");

        int before = loads;
        for (int i = 0; i < 1000; i++) {
            BlockPos pos = new BlockPos(x0 + (i & 15), -800, z0 + ((i >> 4) & 15));
            world.canSeeSky(pos);
            world.getPrecipitationHeight(pos);
            world.getHeight(pos.getX(), pos.getZ());
            world.getLightFor(EnumSkyBlock.SKY, pos.up(5000)); // unloaded: expect the default 15, no load
        }
        log("1000x canSeeSky/getPrecipitationHeight/getHeight/getLightFor: %d cube loads, light 15 at unloaded Y 4200: %b",
                loads - before, world.getLightFor(EnumSkyBlock.SKY, new BlockPos(x0, 4200, z0)) == 15);
        before = loads;
        boolean sees = world.canBlockSeeSky(new BlockPos(x0, -800, z0));
        log("canBlockSeeSky(-800) = %b: %d cube loads (vanilla scan from sea level down via getBlockState)", sees,
                loads - before);

        // 1b. A shaft opened at the top of the tracked range (a cave or a player's tunnel there): cube -47 above is
        // generated (population neighbour) but not tracked, so only the volatile staging map knows its stone.
        int shaftTop = (DEEP_HIGH << 4) + 15;
        for (int y = shaftTop - 7; y <= shaftTop; y++) world.setBlockState(new BlockPos(x0, y, z0), Blocks.AIR.getDefaultState(), 18);
        BlockPos inShaft = new BlockPos(x0, shaftTop - 4, z0);
        ICube above = cubes.getLoadedCube(CX, DEEP_HIGH + 1, CZ);
        log("shaft %d..%d: canSeeSky %b, index top %d, getHeightValue-1 %d, sky light %d; cube above loaded %b tracked %b, its block at %d: %s",
                shaftTop - 7, shaftTop, world.canSeeSky(inShaft), heights.getTopBlockY(0, 0),
                world.getChunk(inShaft).getHeightValue(0, 0) - 1, world.getLightFor(EnumSkyBlock.SKY, inShaft),
                above != null, above != null && above.isSurfaceTracked(), shaftTop + 1,
                above == null ? "-" : above.getBlockState(new BlockPos(x0, shaftTop + 1, z0)).getBlock().getRegistryName());

        // 2. Generate the surface above; occlusion should become known and persist.
        t = System.nanoTime();
        before = loads;
        for (int cy = DEEP_HIGH + 1; cy <= SURFACE_HIGH; cy++) cubes.getCube(CX, cy, CZ, LIGHT);
        log("cubes %d..%d to LIGHT: %d ms, %d cube loads", DEEP_HIGH + 1, SURFACE_HIGH, ms(t), loads - before);
        logTops(heights, "after surface");
        logExposure(world, cubes, "after surface");
        log("shaft after surface: canSeeSky %b, index top %d", world.canSeeSky(inShaft), heights.getTopBlockY(0, 0));

        editCost(world, cubes, CX, CZ, "ocean column");
        BlockPos spawn = world.getSpawnPoint();
        int sx = spawn.getX() >> 4, sz = spawn.getZ() >> 4;
        for (int cy = (spawn.getY() >> 4) - 4; cy <= (spawn.getY() >> 4) + 4; cy++) cubes.getCube(sx, cy, sz, LIGHT);
        editCost(world, cubes, sx, sz, "spawn column");
    }

    /**
     * Cost of surface edits with flag 18 (2|16: client sync, no neighbour/observer updates): grass to dirt on the top
     * layer, then removal of the top block of all 256 x/z, 12 times. A 16x16x8 air room is carved 11 blocks below the
     * lowest top first, so the last removals open it to the sky (light floods a cave).
     */
    private void editCost(WorldServer world, ICubeProviderServer cubes, int cx, int cz, String label) {
        IHeightMap heights = ((IColumn) cubes.getLoadedColumn(cx, cz)).getOpacityIndex();
        int x0 = cx << 4, z0 = cz << 4;
        BlockPos flushPos = new BlockPos(x0, heights.getTopBlockY(0, 0), z0);
        logTops(heights, label);
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < 256; i++) lowest = Math.min(lowest, heights.getTopBlockY(i & 15, i >> 4));
        for (int i = 0; i < 2048; i++) {
            world.setBlockState(new BlockPos(x0 + (i & 15), lowest - 11 - (i >> 8), z0 + ((i >> 4) & 15)), Blocks.AIR.getDefaultState(), 18);
        }
        world.getLightFor(EnumSkyBlock.SKY, flushPos);
        log("%s: carved room Y %d..%d", label, lowest - 18, lowest - 11);

        int converted = 0;
        long t = System.nanoTime();
        int before = loads;
        for (int i = 0; i < 256; i++) {
            BlockPos top = new BlockPos(x0 + (i & 15), heights.getTopBlockY(i & 15, i >> 4), z0 + (i >> 4));
            if (world.getBlockState(top).getBlock() == Blocks.GRASS) {
                world.setBlockState(top, Blocks.DIRT.getDefaultState(), 18);
                converted++;
            }
        }
        long edit = System.nanoTime() - t;
        t = System.nanoTime();
        world.getLightFor(EnumSkyBlock.SKY, flushPos); // a server light read flushes CC's light queue
        log("%s: grass->dirt on %d top blocks: edits %d us, light flush %d us, %d cube loads", label, converted,
                edit / 1000, (System.nanoTime() - t) / 1000, loads - before);

        for (int layer = 1; layer <= 12; layer++) {
            int water = 0, leaves = 0, other = 0;
            t = System.nanoTime();
            before = loads;
            for (int i = 0; i < 256; i++) {
                BlockPos top = new BlockPos(x0 + (i & 15), heights.getTopBlockY(i & 15, i >> 4), z0 + (i >> 4));
                Material material = world.getBlockState(top).getMaterial();
                if (material == Material.WATER) water++;
                else if (material == Material.LEAVES) leaves++;
                else other++;
                world.setBlockState(top, Blocks.AIR.getDefaultState(), 18);
            }
            edit = System.nanoTime() - t;
            t = System.nanoTime();
            world.getLightFor(EnumSkyBlock.SKY, flushPos);
            log("%s: remove top layer %d (%d water, %d leaves, %d other): edits %d us, light flush %d us, %d cube loads",
                    label, layer, water, leaves, other, edit / 1000, (System.nanoTime() - t) / 1000, loads - before);
        }
        logTops(heights, label + " after 12 layers");
    }

    /** Air blocks in the deep cubes that the heightmap calls sky-exposed (canSeeSky). Reads loaded cubes only. */
    private static void logExposure(WorldServer world, ICubeProviderServer cubes, String label) {
        StringBuilder perCube = new StringBuilder();
        int air = 0, exposed = 0;
        for (int cy = DEEP_LOW; cy <= DEEP_HIGH; cy++) {
            ICube cube = cubes.getLoadedCube(CX, cy, CZ);
            if (cube == null) {
                perCube.append(' ').append(cy).append(":unloaded");
                continue;
            }
            int cubeAir = 0, cubeExposed = 0;
            for (int i = 0; i < 4096; i++) {
                BlockPos pos = new BlockPos((CX << 4) + (i & 15), (cy << 4) + (i >> 8), (CZ << 4) + ((i >> 4) & 15));
                if (cube.getBlockState(pos).getMaterial() != Material.AIR) continue;
                cubeAir++;
                if (world.canSeeSky(pos)) cubeExposed++;
            }
            air += cubeAir;
            exposed += cubeExposed;
            perCube.append(' ').append(cy).append(':').append(cubeExposed).append('/').append(cubeAir);
        }
        log("%s: %d of %d air blocks in deep cubes report canSeeSky (per cube exposed/air:%s)", label, exposed, air,
                perCube);
    }

    private static void logTops(IHeightMap heights, String label) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < 256; i++) {
            int top = heights.getTopBlockY(i & 15, i >> 4);
            min = Math.min(min, top);
            max = Math.max(max, top);
        }
        log("%s: opacity-index top Y min %d max %d", label, min, max);
    }

    private static long ms(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void log(String format, Object... args) {
        SolarApocalypse.LOGGER.info("SOLAR PROBE " + String.format(format, args));
    }
}
