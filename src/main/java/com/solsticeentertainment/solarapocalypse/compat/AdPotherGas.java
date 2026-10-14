package com.solsticeentertainment.solarapocalypse.compat;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Pollution of the Realms' own call for placing its gas (Pollution keeps AdPother's classes out of its own code). */
final class AdPotherGas {
    private AdPotherGas() {}

    /** One unit of a gas at a spot, or none if the spot is taken (radius 0: AdPother looks nowhere else). */
    static boolean place(Block gas, World world, BlockPos pos) {
        return ((Pollutant<?>) gas).generateAt(world, pos, 1, 0) > 0;
    }
}
