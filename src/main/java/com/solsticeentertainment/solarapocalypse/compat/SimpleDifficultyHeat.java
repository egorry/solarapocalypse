package com.solsticeentertainment.solarapocalypse.compat;

import com.charles445.simpledifficulty.api.temperature.ITemperatureModifier;
import com.charles445.simpledifficulty.api.temperature.TemperatureRegistry;
import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.Sky;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SunDamage;
import com.solsticeentertainment.solarapocalypse.Tags;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Simple Difficulty (soft dependency, loaded only with it): the sun warms the temperature there, as it damages mobs:
 * phase_n.sunTemperature in direct sunlight (as sunDamage: by day unless the phase has the sun at night) plus
 * phase_n.backgroundTemperature wherever background heat reaches (as backgroundDamage). Each has a global switch
 * (entities.simpleDifficultySunTemperature, simpleDifficultyBackgroundTemperature).
 */
public final class SimpleDifficultyHeat implements ITemperatureModifier {

    public static void register() {
        TemperatureRegistry.registerModifier(new SimpleDifficultyHeat());
    }

    public static boolean registered() {
        return TemperatureRegistry.modifiers.containsKey(Tags.MOD_ID + ":sun");
    }

    /** Degrees the apocalypse adds at a position (Simple Difficulty's scale: 11-14 normal, 15-19 hot, 20-25 burning). */
    public static float heat(World world, BlockPos pos) {
        if (world.isRemote || !SolarApocalypse.isActive(world)) return 0;
        int phase = SolarApocalypse.timeline().phaseAt(ApocalypseClock.progress());
        if (phase < 0) return 0;
        SolarConfig.Phase p = SolarConfig.phases[phase];
        boolean direct = SolarConfig.simpleDifficultySunTemperature && p.sunTemperature != 0 && SunDamage.sunUp(world, phase) && Sky.at(world, pos) == Sky.EXPOSED;
        boolean background = SolarConfig.simpleDifficultyBackgroundTemperature && p.backgroundTemperature != 0 && Sky.heat(world, pos);
        return (float) ((direct ? p.sunTemperature : 0) + (background ? p.backgroundTemperature : 0));
    }

    @Override
    public float getWorldInfluence(World world, BlockPos pos) {
        return heat(world, pos);
    }

    @Override
    public float getPlayerInfluence(EntityPlayer player) {
        return 0;
    }

    @Override
    public String getName() {
        return Tags.MOD_ID + ":sun";
    }
}
