package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSand;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Sand, red sand and gravel that never fall (the user's idea, turn 9): what conversions place instead of vanilla sand and
 * gravel while blocks.blockPhysics is off, so converted ground over a cave stays put (vanilla's own check when a block is
 * placed would drop it, flags or not). Looks, sounds, breaks and drops as the vanilla block (gravel still gives flint);
 * no item of its own (pick block and silk touch give the vanilla one). Every rule treats it as the vanilla block
 * (BlockRules matches {@link #real}).
 */
public final class SettledBlock extends Block {

    public enum Type implements IStringSerializable {
        SAND, RED_SAND, GRAVEL;

        @Override
        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final PropertyEnum<Type> TYPE = PropertyEnum.create("type", Type.class);
    public static SettledBlock BLOCK;

    private SettledBlock() {
        super(Material.SAND);
        setHardness(0.5F);
        setHarvestLevel("shovel", 0);
        setTranslationKey(Tags.MOD_ID + ".settled_block");
        setRegistryName(Tags.MOD_ID, "settled_block");
    }

    /** The vanilla block a settled one stands for; any other state as it is. */
    public static IBlockState real(IBlockState state) {
        if (state.getBlock() != BLOCK) return state;
        switch (state.getValue(TYPE)) {
            case GRAVEL:
                return Blocks.GRAVEL.getDefaultState();
            case RED_SAND:
                return Blocks.SAND.getDefaultState().withProperty(BlockSand.VARIANT, BlockSand.EnumType.RED_SAND);
            default:
                return Blocks.SAND.getDefaultState();
        }
    }

    /** What a conversion places for a target: vanilla sand and gravel become settled ones unless blocks.blockPhysics. */
    public static IBlockState placed(IBlockState state) {
        if (SolarConfig.blockPhysics || BLOCK == null) return state;
        if (state.getBlock() == Blocks.GRAVEL) return BLOCK.getDefaultState().withProperty(TYPE, Type.GRAVEL);
        if (state.getBlock() != Blocks.SAND) return state;
        return BLOCK.getDefaultState().withProperty(TYPE,
                state.getValue(BlockSand.VARIANT) == BlockSand.EnumType.RED_SAND ? Type.RED_SAND : Type.SAND);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, TYPE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(TYPE, Type.values()[meta % Type.values().length]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(TYPE).ordinal();
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        IBlockState real = real(state);
        real.getBlock().getDrops(drops, world, pos, real, fortune);
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        IBlockState real = real(state);
        return new ItemStack(real.getBlock(), 1, real.getBlock().damageDropped(real));
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return getItem(null, null, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
        return real(state).getBlockHardness(world, pos);
    }

    @Override
    public SoundType getSoundType(IBlockState state, World world, BlockPos pos, @Nullable Entity entity) {
        return real(state).getBlock().getSoundType();
    }

    @Override
    @SuppressWarnings("deprecation")
    public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
        return real(state).getMapColor(world, pos);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BLOCK = new SettledBlock());
    }
}
