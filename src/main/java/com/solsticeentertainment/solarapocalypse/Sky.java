package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.cc.CubicSky;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * What lies above a position, by the vanilla rule (blocks with light opacity 0, such as glass, let the sun through;
 * leaves, water and ice do not). Never loads terrain.
 */
public final class Sky {

    public static final int UNKNOWN = -1;

    private Sky() {}

    /**
     * 0 if the position sees the sky, else the number of sun-blocking blocks above it (counting stops after cap + 1),
     * or UNKNOWN when the terrain above is not loaded (Cubic Chunks: not known to be clear for world.skyClearance blocks).
     */
    public static int cover(World world, BlockPos pos, int cap) {
        if (pos.getY() < SolarConfig.sunFloorY) return cap + 1;
        if (SolarApocalypse.isCubic(world)) return CubicSky.cover(world, pos, cap);
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return UNKNOWN;
        int x = pos.getX() & 15, z = pos.getZ() & 15;
        int top = chunk.getHeightValue(x, z);
        if (pos.getY() >= top) return 0;
        int count = 0;
        for (int y = top - 1; y > pos.getY() && count <= cap; y--) {
            if (chunk.getBlockState(x, y, z).getLightOpacity() > 0) count++;
        }
        return Math.max(count, 1);
    }
}
