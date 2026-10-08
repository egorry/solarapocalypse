package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/**
 * The sun's fire: vanilla fire in looks, sound, light and burning entities that touch it, but it never ticks, so it never
 * spreads, burns out or burns blocks. The apocalypse places and removes it; AGE holds the epoch it was placed for
 * (phase, or layer in infinite phases) so stale fire can be recognised. Players put it out like vanilla fire.
 */
public final class SolarFire extends BlockFire {

    public static SolarFire BLOCK;

    private SolarFire() {
        setTickRandomly(false);
        setHardness(0.0F);
        setLightLevel(1.0F);
        setSoundType(SoundType.CLOTH);
        setTranslationKey(Tags.MOD_ID + ".solar_fire");
        setRegistryName(Tags.MOD_ID, "solar_fire");
        disableStats();
    }

    public static boolean is(IBlockState state) {
        return state.getBlock() == BLOCK;
    }

    public static IBlockState forEpoch(int epoch) {
        return BLOCK.getDefaultState().withProperty(AGE, epoch & 15);
    }

    public static int epochOf(IBlockState state) {
        return state.getValue(AGE);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        // never spreads, ages or burns anything
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        // no portal lighting, no scheduled tick
    }

    /** Goes only with its ground: anything that blocks movement holds it (vanilla fire also needs a solid top face). */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.getBlockState(pos.down()).getMaterial().blocksMovement()) world.setBlockToAir(pos);
    }

    @Override
    public boolean requiresUpdates() {
        return false;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BLOCK = new SolarFire());
    }

    /** Punching the block a fire stands on puts it out, as with vanilla fire (World.extinguishFire only knows Blocks.FIRE). */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || event.getFace() == null) return;
        BlockPos fire = event.getPos().offset(event.getFace());
        if (is(world.getBlockState(fire))) {
            world.playEvent(event.getEntityPlayer(), 1009, fire, 0);
            world.setBlockToAir(fire);
        }
    }
}
