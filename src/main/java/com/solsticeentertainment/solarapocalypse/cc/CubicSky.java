package com.solsticeentertainment.solarapocalypse.cc;

import com.solsticeentertainment.solarapocalypse.Sky;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.IHeightMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Sky queries for Cubic Chunks worlds. Only loaded when CC is installed.
 *
 * CC counts never-generated cubes as air, so "nothing opaque above" is only trusted when the cubes up to
 * world.skyClearance blocks above are loaded (or the position is above world.skyCeilingY). Everything here reads
 * CC's heightmaps and loaded cubes only; nothing loads or generates a cube.
 */
public final class CubicSky {

    private CubicSky() {}

    public static boolean isCubic(World world) {
        return ((ICubicWorld) world).isCubicWorld();
    }

    public static int maxGenerationHeight(World world) {
        return ((ICubicWorld) world).getMaxGenerationHeight();
    }

    public static int cover(World world, BlockPos pos, int cap) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        int cx = Coords.blockToCube(pos.getX()), cz = Coords.blockToCube(pos.getZ());
        Chunk column = cubes.getLoadedColumn(cx, cz);
        if (column == null) return Sky.UNKNOWN;
        int x = Coords.blockToLocal(pos.getX()), z = Coords.blockToLocal(pos.getZ()), y = pos.getY();
        int top = ((IColumn) column).getHeightValue(x, z) - 1; // heightmap of tracked cubes plus loaded untracked ones
        if (y > top) return knownClear(y, highestLoadedAbove(cubes, cx, Coords.blockToCube(y), cz)) ? 0 : Sky.UNKNOWN;
        if (!knownClear(top, highestLoadedAbove(cubes, cx, Coords.blockToCube(top), cz))) return Sky.UNKNOWN;
        IHeightMap index = ((IColumn) column).getOpacityIndex();
        int count = 1;
        int below = index.getTopBlockYBelow(x, z, top);
        while (below > y && count <= cap) {
            count++;
            below = index.getTopBlockYBelow(x, z, below);
        }
        return count;
    }

    /** Highest cube Y such that every cube from cubeY + 1 up to it is loaded (cubeY itself if the next one is not). */
    public static int highestLoadedAbove(ICubeProvider cubes, int cx, int cubeY, int cz) {
        int limit = cubeY + (SolarConfig.skyClearance >> 4) + 2;
        int c = cubeY;
        while (c < limit && cubes.getLoadedCube(cx, c + 1, cz) != null) c++;
        return c;
    }

    /** Whether the column above y is known to be clear, given the highest loaded cube of the run above y. */
    public static boolean knownClear(int y, int loadedUpTo) {
        int ceiling = SolarConfig.skyCeilingY == SolarConfig.AUTO ? Integer.MAX_VALUE : SolarConfig.skyCeilingY;
        if (y > ceiling) return true;
        int needed = (int) Math.min((long) y + SolarConfig.skyClearance, ceiling);
        return Coords.blockToCube(needed) <= loadedUpTo;
    }
}
