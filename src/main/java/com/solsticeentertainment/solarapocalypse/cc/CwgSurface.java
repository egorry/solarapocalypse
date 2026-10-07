package com.solsticeentertainment.solarapocalypse.cc;

import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.CustomTerrainGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.builder.IBuilder;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Terrain surface of a CubicWorldGen CustomCubic world, computed from the generator's density function without
 * generating anything: CWG makes a block solid where the density (evaluated on a 4x8x4 grid, interpolated) is above 0,
 * and caves, ravines and decoration only change that afterwards. The result is each column's highest solid terrain
 * block before caves and decoration (trees and structures stand above it).
 *
 * Reads CWG 0.0.152 internals by reflection (CustomTerrainGenerator.terrainBuilder). Presets with cube areas, and
 * terrain floating more than 64 blocks above the rest, are not supported (no model: the recorded surface is used).
 * Server thread only (CWG's density caches are not thread-safe). Only loaded when CubicWorldGen is installed.
 */
public final class CwgSurface {

    public static final int NONE = Integer.MIN_VALUE;
    private static final int GAP_LEVELS = 8;      // 64 blocks without terrain above the highest found = open sky
    private static final int MAX_LEVELS = 1024;   // give up after 8192 blocks
    private static final int MAX_COLUMNS = 8192;  // cached columns before the caches are dropped

    private final IBuilder density;
    private final int hint;
    private final Long2ObjectOpenHashMap<int[]> columns = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Int2DoubleOpenHashMap> corners = new Long2ObjectOpenHashMap<>();

    private CwgSurface(IBuilder density, int hint) {
        this.density = density;
        this.hint = hint;
    }

    /** The model for a world, or null if its generator is not a plain CustomCubic one. */
    public static CwgSurface of(World world) {
        ICubeGenerator generator = ((ICubicWorldServer) world).getCubeGenerator();
        if (!(generator instanceof CustomTerrainGenerator)) return null;
        try {
            if (!((Map<?, ?>) field("areaGenerators").get(generator)).isEmpty()) {
                SolarApocalypse.LOGGER.info("{}: the CubicWorldGen preset uses cube areas, so terrain surfaces are recorded instead of computed",
                        world.provider.getDimensionType().getName());
                return null;
            }
            IBuilder builder = (IBuilder) field("terrainBuilder").get(generator);
            return new CwgSurface(builder, (int) ((CustomTerrainGenerator) generator).getConfig().heightOffset);
        } catch (ReflectiveOperationException | RuntimeException e) {
            SolarApocalypse.LOGGER.warn("Cannot read the CubicWorldGen generator; terrain surfaces are recorded instead", e);
            return null;
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = CustomTerrainGenerator.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Highest solid terrain block of the column at x, z, or NONE. */
    public int top(int x, int z) {
        long key = (long) (x >> 4) << 32 | ((z >> 4) & 0xFFFFFFFFL);
        int[] tops = columns.get(key);
        if (tops == null) {
            if (columns.size() >= MAX_COLUMNS) {
                columns.clear();
                corners.clear();
            }
            tops = new int[256];
            int x0 = x & ~15, z0 = z & ~15;
            for (int i = 0; i < 256; i++) tops[i] = computeTop(x0 + (i & 15), z0 + (i >> 4));
            columns.put(key, tops);
        }
        return tops[(z & 15) << 4 | (x & 15)];
    }

    private int computeTop(int x, int z) {
        int start = Math.floorDiv(hint, 8) * 8;
        int found = NONE;
        if (at(x, start, z) > 0) found = start;
        for (int y = start, gap = 0, n = 0; gap < GAP_LEVELS && n < MAX_LEVELS; n++) {
            y += 8;
            if (at(x, y, z) > 0) {
                found = y;
                gap = 0;
            } else {
                gap++;
            }
        }
        for (int y = start, n = 0; found == NONE && n < MAX_LEVELS; n++) {
            y -= 8;
            if (at(x, y, z) > 0) found = y;
        }
        if (found == NONE) return NONE;
        // the density is linear in y between grid levels: highest y below the next level that is still positive
        double low = at(x, found, z), high = at(x, found + 8, z);
        for (int dy = 7; dy > 0; dy--) {
            if (low + (high - low) * dy / 8 > 0) return found + dy;
        }
        return found;
    }

    /** Density at block x, z and grid level y (a multiple of 8), interpolated between the 4 surrounding grid columns. */
    private double at(int x, int y, int z) {
        int gx = x & ~3, gz = z & ~3;
        double fx = (x - gx) / 4.0, fz = (z - gz) / 4.0;
        double d00 = corner(gx, y, gz), d10 = corner(gx + 4, y, gz), d01 = corner(gx, y, gz + 4), d11 = corner(gx + 4, y, gz + 4);
        double d0 = d00 + (d10 - d00) * fx, d1 = d01 + (d11 - d01) * fx;
        return d0 + (d1 - d0) * fz;
    }

    private double corner(int gx, int y, int gz) {
        long key = (long) (gx >> 2) << 32 | ((gz >> 2) & 0xFFFFFFFFL);
        Int2DoubleOpenHashMap levels = corners.get(key);
        if (levels == null) corners.put(key, levels = new Int2DoubleOpenHashMap());
        if (levels.containsKey(y)) return levels.get(y);
        double d = density.get(gx, y, gz);
        levels.put(y, d);
        return d;
    }
}
