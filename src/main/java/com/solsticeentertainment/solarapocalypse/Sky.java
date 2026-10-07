package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.cc.CubicSky;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

/**
 * Sun and heat at a position, by the vanilla rule (what burns zombies): a position is in the sun when nothing with
 * light opacity above 0 is over it (glass lets the sun through; leaves, water and ice do not). Never loads terrain.
 */
public final class Sky {

    public static final int SHADED = 0, EXPOSED = 1, UNKNOWN = 2;

    private Sky() {}

    /** Whether a position (an entity's eyes) is in the sun: EXPOSED, SHADED, or UNKNOWN when the terrain above is not known. */
    public static int at(World world, BlockPos pos) {
        if (pos.getY() < SolarConfig.sunFloorY) return SHADED;
        if (SolarApocalypse.isCubic(world)) return CubicSky.at(world, pos);
        if (!world.isBlockLoaded(pos)) return UNKNOWN;
        return world.canSeeSky(pos) ? EXPOSED : SHADED;
    }

    /** Whether background heat reaches a position: sky light of at least entities.backgroundMinSkyLight, terrain above known. */
    public static boolean heat(World world, BlockPos pos) {
        if (pos.getY() < SolarConfig.sunFloorY || !world.isBlockLoaded(pos)) return false;
        if (world.getLightFor(EnumSkyBlock.SKY, pos) < SolarConfig.backgroundMinSkyLight) return false;
        return !SolarApocalypse.isCubic(world) || CubicSky.columnTopKnown(world, pos);
    }
}
