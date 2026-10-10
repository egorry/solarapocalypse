package com.solsticeentertainment.solarapocalypse.client;

import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import com.solsticeentertainment.solarapocalypse.Tags;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client only. Solar fire's look from blocks.solarFireModel: blockstates/solar_fire.json (vanilla fire's) or
 * solar_fire_simple.json (its crossed flames only). Read whenever models bake, so F3+T applies a changed setting.
 */
@SideOnly(Side.CLIENT)
public final class SolarFireModel {

    private SolarFireModel() {}

    @SubscribeEvent
    public static void register(ModelRegistryEvent event) {
        ModelLoader.setCustomStateMapper(SolarFire.BLOCK, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                String name = SolarConfig.solarFireModel == SolarConfig.FireModel.SIMPLE ? "solar_fire_simple" : "solar_fire";
                return new ModelResourceLocation(Tags.MOD_ID + ":" + name, getPropertyString(state.getProperties()));
            }
        });
    }
}
