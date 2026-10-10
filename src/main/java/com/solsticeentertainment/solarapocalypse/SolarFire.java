package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * The sun's fire: vanilla fire in looks, sound, light and burning entities that touch it, but it never ticks, so it never
 * spreads, burns out or burns blocks. The apocalypse places and removes it; AGE holds the epoch it was placed for
 * (phase, or layer in infinite phases) so stale fire can be recognised. Players put it out like vanilla fire (punching,
 * water, thrown water bottles); it comes back on the engine's next look at the spot.
 */
public final class SolarFire extends BlockFire {

    public static SolarFire BLOCK;
    /** Light given off: the server's blocks.solarFireLight (set on every config load; clients get it on login and reload). */
    public static int light = 15;

    /**
     * Vanilla fire sounds like wool (SoundType.CLOTH). An entity's step plays the sound of the block 0.2 below its centre,
     * which is the fire when the centre hangs over a fire beside a ledge: silent here, as air would be. The crackle stays.
     */
    private static final SoundType SILENT = new SoundType(0.0F, 1.0F, SoundEvents.BLOCK_CLOTH_BREAK, SoundEvents.BLOCK_CLOTH_STEP,
            SoundEvents.BLOCK_CLOTH_PLACE, SoundEvents.BLOCK_CLOTH_HIT, SoundEvents.BLOCK_CLOTH_FALL);

    private SolarFire() {
        setTickRandomly(false);
        setHardness(0.0F);
        setLightLevel(1.0F);
        setSoundType(SILENT);
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
    public int getLightValue(IBlockState state) {
        return light;
    }

    /** The flames look as bright as vanilla fire's, whatever light they give off. */
    @Override
    @SideOnly(Side.CLIENT)
    public int getPackedLightmapCoords(IBlockState state, IBlockAccess source, BlockPos pos) {
        return source.getCombinedLight(pos, 15);
    }

    /** Vanilla sets what stands in fire alight for 8 seconds: a death from it then counts as the sun's (no drops). */
    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && entity instanceof EntityLivingBase) SunDamage.burnt((EntityLivingBase) entity, 8 * 20);
    }

    @Override
    public boolean requiresUpdates() {
        return false;
    }

    /** Burns what touches it like vanilla fire (World.isFlammableWithin only knows Blocks.FIRE): mobs catch fire, items burn up. */
    @Override
    public boolean isBurning(IBlockAccess world, BlockPos pos) {
        return true;
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

    /** A thrown water bottle (splash or lingering) puts solar fire out where it puts out vanilla fire: the spot it hits and the four beside it. */
    @SubscribeEvent
    public static void onSplash(ProjectileImpactEvent.Throwable event) {
        if (!(event.getThrowable() instanceof EntityPotion)) return;
        EntityPotion potion = (EntityPotion) event.getThrowable();
        RayTraceResult hit = event.getRayTraceResult();
        ItemStack bottle = potion.getPotion();
        if (potion.world.isRemote || hit.typeOfHit != RayTraceResult.Type.BLOCK || PotionUtils.getPotionFromItem(bottle) != PotionTypes.WATER
                || !PotionUtils.getEffectsFromStack(bottle).isEmpty()) return;
        BlockPos center = hit.getBlockPos().offset(hit.sideHit);
        douse(potion.world, center);
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) douse(potion.world, center.offset(side));
    }

    private static void douse(World world, BlockPos pos) {
        if (world.isBlockLoaded(pos) && is(world.getBlockState(pos))) {
            world.playEvent(null, 1009, pos, 0);
            world.setBlockToAir(pos);
        }
    }
}
