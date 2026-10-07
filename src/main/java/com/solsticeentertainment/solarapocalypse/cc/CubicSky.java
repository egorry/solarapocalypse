package com.solsticeentertainment.solarapocalypse.cc;

import com.solsticeentertainment.solarapocalypse.Sky;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Loader;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sky queries for Cubic Chunks worlds. Only loaded when CC is installed.
 *
 * CC counts never-generated cubes as air, so "nothing opaque above" is only trusted when the cubes up to
 * world.skyClearance blocks above are loaded, or the position is above the sky ceiling: world.skyCeilingY, or in
 * CubicWorldGen worlds the computed terrain surface + world.surfaceMargin. Nothing here loads or generates a cube.
 */
public final class CubicSky {

    private static final boolean CUBIC_WORLD_GEN = Loader.isModLoaded("cubicgen");
    private static final Map<World, CwgSurface> MODELS = new WeakHashMap<>();

    private CubicSky() {}

    public static boolean isCubic(World world) {
        return ((ICubicWorld) world).isCubicWorld();
    }

    public static int maxGenerationHeight(World world) {
        return ((ICubicWorld) world).getMaxGenerationHeight();
    }

    /** The CubicWorldGen surface model of a world, or null (other generator, unsupported preset, CWG not installed). */
    public static CwgSurface model(World world) {
        if (!CUBIC_WORLD_GEN || world.isRemote) return null;
        if (!MODELS.containsKey(world)) MODELS.put(world, CwgSurface.of(world));
        return MODELS.get(world);
    }

    /** Forgets the models (config reload, server stop). */
    public static void reset() {
        MODELS.clear();
    }

    /** Y above which nothing generated can cover the sun at x, z. */
    public static int ceiling(World world, int x, int z) {
        int ceiling = SolarConfig.skyCeilingY == SolarConfig.AUTO ? Integer.MAX_VALUE : SolarConfig.skyCeilingY;
        CwgSurface model = model(world);
        if (model != null) {
            int top = model.top(x, z);
            if (top != CwgSurface.NONE) ceiling = (int) Math.min(ceiling, (long) top + SolarConfig.surfaceMargin);
        }
        return ceiling;
    }

    public static int at(World world, BlockPos pos) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        int cx = Coords.blockToCube(pos.getX()), cz = Coords.blockToCube(pos.getZ());
        Chunk column = cubes.getLoadedColumn(cx, cz);
        if (column == null) return Sky.UNKNOWN;
        int y = pos.getY();
        int top = ((IColumn) column).getHeightValue(Coords.blockToLocal(pos.getX()), Coords.blockToLocal(pos.getZ())) - 1;
        if (y <= top) return Sky.SHADED;
        return knownClear(y, highestLoadedAbove(cubes, cx, Coords.blockToCube(y), cz), ceiling(world, pos.getX(), pos.getZ()))
                ? Sky.EXPOSED : Sky.UNKNOWN;
    }

    /** Whether the column's topmost opaque block at x, z is known to be the real top. */
    public static boolean columnTopKnown(World world, BlockPos pos) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        int cx = Coords.blockToCube(pos.getX()), cz = Coords.blockToCube(pos.getZ());
        Chunk column = cubes.getLoadedColumn(cx, cz);
        if (column == null) return false;
        int top = ((IColumn) column).getHeightValue(Coords.blockToLocal(pos.getX()), Coords.blockToLocal(pos.getZ())) - 1;
        return knownClear(top, highestLoadedAbove(cubes, cx, Coords.blockToCube(top), cz), ceiling(world, pos.getX(), pos.getZ()));
    }

    /** Highest cube Y such that every cube from cubeY + 1 up to it is loaded (cubeY itself if the next one is not). */
    public static int highestLoadedAbove(ICubeProvider cubes, int cx, int cubeY, int cz) {
        int limit = cubeY + (SolarConfig.skyClearance >> 4) + 2;
        int c = cubeY;
        while (c < limit && cubes.getLoadedCube(cx, c + 1, cz) != null) c++;
        return c;
    }

    /** Whether nothing unknown can lie above y, given the highest loaded cube of the run above y and the sky ceiling. */
    public static boolean knownClear(int y, int loadedUpTo, int ceiling) {
        if (y >= ceiling) return true;
        int needed = (int) Math.min((long) y + SolarConfig.skyClearance, ceiling);
        return Coords.blockToCube(needed) <= loadedUpTo;
    }
}
