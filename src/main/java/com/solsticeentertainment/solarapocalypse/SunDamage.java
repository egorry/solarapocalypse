package com.solsticeentertainment.solarapocalypse;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * Burns mobs: direct sun (sky visible from the eyes: the zombie rule, optionally by day only) and background heat (wherever sky light
 * reaches, day or night, so overhangs and trees are no refuge). Each living entity is checked once per interval,
 * staggered by entity id.
 */
public final class SunDamage {

    /** Not fire damage, so Fire Resistance only protects as entities.fireResistance says. */
    public static final DamageSource SUN = new DamageSource(Tags.MOD_ID + ".sun").setDamageBypassesArmor();

    private SunDamage() {}

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (event.phase != TickEvent.Phase.END || world.isRemote || !SolarApocalypse.isActive(world)) return;
        long progress = ApocalypseClock.progress();
        int phase = SolarApocalypse.timeline().phaseAt(progress);
        if (phase < 0) return;
        SolarConfig.Phase p = SolarConfig.phases[phase];
        if (p.sunDamage <= 0 && p.sunFireSeconds <= 0 && p.backgroundDamage <= 0 && p.backgroundFireSeconds <= 0) return;
        boolean dayOnly = SolarConfig.sunNeedsDaytime && (SolarConfig.sunAtNightFromPhase <= 0 || phase + 1 < SolarConfig.sunAtNightFromPhase);
        boolean sunUp = !dayOnly || world.isDaytime();
        int interval = SolarConfig.damageIntervalTicks;
        long tick = world.getTotalWorldTime();
        List<Entity> entities = world.loadedEntityList;
        for (int i = 0; i < entities.size(); i++) { // index loop: deaths can spawn drops into the list
            Entity e = entities.get(i);
            if (e instanceof EntityLivingBase && (e.getEntityId() + tick) % interval == 0 && !e.isDead) {
                burn(world, (EntityLivingBase) e, p, sunUp);
            }
        }
    }

    private static void burn(World world, EntityLivingBase e, SolarConfig.Phase p, boolean sunUp) {
        if (e instanceof EntityArmorStand) return;
        if (e instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) e;
            if (!SolarConfig.affectPlayers || player.isCreative() || player.isSpectator()) return;
        }
        if (SolarConfig.spareFireImmune && e.isImmuneToFire()) return;
        if (!SolarConfig.entityBlacklist.isEmpty()) {
            ResourceLocation id = EntityList.getKey(e);
            if (id != null && SolarConfig.entityBlacklist.contains(id.toString())) return;
        }
        BlockPos eyes = new BlockPos(e.posX, e.posY + e.getEyeHeight(), e.posZ);
        boolean direct = (p.sunDamage > 0 || p.sunFireSeconds > 0) && sunUp && Sky.at(world, eyes) == Sky.EXPOSED;
        boolean background = (p.backgroundDamage > 0 || p.backgroundFireSeconds > 0) && Sky.heat(world, eyes);
        if (e.isPotionActive(MobEffects.FIRE_RESISTANCE)) {
            SolarConfig.FireResistance fr = SolarConfig.fireResistance;
            if (fr == SolarConfig.FireResistance.DIRECT || fr == SolarConfig.FireResistance.BOTH) direct = false;
            if (fr == SolarConfig.FireResistance.BACKGROUND || fr == SolarConfig.FireResistance.BOTH) background = false;
        }
        double damage = (direct ? p.sunDamage : 0) + (background ? p.backgroundDamage : 0);
        int fire = Math.max(direct ? p.sunFireSeconds : 0, background ? p.backgroundFireSeconds : 0);
        if (fire > 0) e.setFire(fire);
        if (damage > 0) e.attackEntityFrom(SUN, (float) damage);
    }
}
