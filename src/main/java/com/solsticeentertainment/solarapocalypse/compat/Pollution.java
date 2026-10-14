package com.solsticeentertainment.solarapocalypse.compat;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Pollution of the Realms (AdPother), optional (the pollution section): blocks the erosion destroys leave dust, solar fire
 * gives off carbon and sulfur when lit, and from pollutionEndsPhase on nothing pollutes and the apocalypse clears the gas
 * it comes across (CubeEngine). Without AdPother none of this runs. Its gas blocks are found by name; AdPotherGas places
 * them through AdPother's own code (density, spreading).
 */
public final class Pollution {
    /** AdPother is installed and its dust, carbon and sulfur blocks were found. */
    public static boolean on;
    private static Block dust, carbon, sulfur;
    /** Since server start (/solar status, the self-test): gas units placed for destroyed blocks and for solar fire, gas blocks cleared. */
    public static long dustPlaced, smokePlaced, cleared;

    private Pollution() {}

    /** Finds the gas blocks, at server start (AdPother registers them during its own init, which can come after this mod's). */
    public static void init() {
        if (!Loader.isModLoaded("adpother")) return;
        dust = gas("dust");
        carbon = gas("carbon");
        sulfur = gas("sulfur");
        on = dust != null && carbon != null && sulfur != null;
        if (!on) SolarApocalypse.LOGGER.warn("Pollution of the Realms is installed but its dust, carbon or sulfur block is missing: the apocalypse does not pollute");
    }

    private static Block gas(String name) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("adpother", name));
        return block == null || block == Blocks.AIR ? null : block;
    }

    /** Whether a block is Pollution of the Realms' dust, carbon or sulfur (any density). */
    public static boolean isGas(IBlockState state) {
        if (!on) return false;
        Block block = state.getBlock();
        return block == dust || block == carbon || block == sulfur;
    }

    /** Whether a phase (0-based, -1 before the first) is at or past pollutionEndsPhase: nothing pollutes, gas is cleared. */
    public static boolean clears(int phase) {
        return on && SolarConfig.pollutionEndsPhase > 0 && phase + 1 >= SolarConfig.pollutionEndsPhase;
    }

    /** BlockChanges.apply, for every change the apocalypse makes (destroyed: the block became air or fire). */
    public static void changed(World world, BlockPos pos, IBlockState from, IBlockState to, boolean destroyed) {
        if (world.isRemote || isGas(from)) return;
        int phase = SolarApocalypse.timeline().phaseAt(ApocalypseClock.progress());
        if (phase < 0 || phase + 1 < SolarConfig.pollutionStartsPhase || clears(phase)) return;
        if (destroyed && SolarConfig.destructionPollution) {
            double share = SolarApocalypse.changes(world).dustShare(from);
            BlockPos at = to.getMaterial() == Material.AIR ? pos : pos.up(); // fire in its place: above it
            // AdPother looks at the blocks around the spot (unloaded cubes would be generated)
            if (share > 0 && world.rand.nextDouble() < share && BlockChanges.aroundLoaded(world, at) && AdPotherGas.place(dust, world, at)) dustPlaced++;
        }
        if (SolarConfig.solarFirePollution && SolarFire.is(to) && !SolarFire.is(from)
                && world.rand.nextDouble() * 100 < SolarConfig.solarFirePollutionPercent) {
            BlockPos above = pos.up(), higher = above.up(); // one kind of gas per block
            if (BlockChanges.aroundLoaded(world, above) && AdPotherGas.place(carbon, world, above)) smokePlaced++;
            if (BlockChanges.aroundLoaded(world, higher) && AdPotherGas.place(sulfur, world, higher)) smokePlaced++;
        }
    }
}
