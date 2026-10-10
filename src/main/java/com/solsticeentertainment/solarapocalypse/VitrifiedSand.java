package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSand;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMultiTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/**
 * Sand the sun has melted in place: rough, cloudy glass, opaque (more sand than glass: no light or sight through it).
 * Glass material (sound, no tool needed, no mob spawns on it, as on glass). Has vanilla sand's {@code variant} property
 * (sand, red_sand), shows as its sand on maps, and breaks back into one sand of the same variant (silk touch keeps the
 * block).
 */
public final class VitrifiedSand extends Block {

    public static VitrifiedSand BLOCK;

    private VitrifiedSand() {
        super(Material.GLASS);
        setDefaultState(blockState.getBaseState().withProperty(BlockSand.VARIANT, BlockSand.EnumType.SAND));
        setHardness(0.5F);
        setSoundType(SoundType.GLASS);
        setTranslationKey(Tags.MOD_ID + ".vitrified_sand");
        setRegistryName(Tags.MOD_ID, "vitrified_sand");
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BlockSand.VARIANT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BlockSand.VARIANT, BlockSand.EnumType.byMetadata(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockSand.VARIANT).getMetadata();
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(Blocks.SAND);
    }

    /** The sand's meta for the drop; also the pick-block item's meta. */
    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (BlockSand.EnumType type : BlockSand.EnumType.values()) items.add(new ItemStack(this, 1, type.getMetadata()));
    }

    @Override
    public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(BlockSand.VARIANT).getMapColor();
    }

    /** Opaque although glass material: also makes it a full block with light opacity 255 (set in Block's constructor). */
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return true;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BLOCK = new VitrifiedSand());
    }

    /** Subtype item: meta = variant, names tile.solarapocalypse.vitrified_sand.(sand|red_sand).name. */
    @SubscribeEvent
    public static void registerItem(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemMultiTexture(BLOCK, BLOCK,
                stack -> BlockSand.EnumType.byMetadata(stack.getMetadata()).getName()).setRegistryName(BLOCK.getRegistryName()));
    }
}
