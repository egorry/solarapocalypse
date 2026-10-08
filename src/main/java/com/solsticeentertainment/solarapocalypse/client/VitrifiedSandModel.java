package com.solsticeentertainment.solarapocalypse.client;

import com.solsticeentertainment.solarapocalypse.VitrifiedSand;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client only. The vitrified sand item's model (models/item/vitrified_sand.json). */
@SideOnly(Side.CLIENT)
public final class VitrifiedSandModel {

    private VitrifiedSandModel() {}

    @SubscribeEvent
    public static void register(ModelRegistryEvent event) {
        Item item = Item.getItemFromBlock(VitrifiedSand.BLOCK);
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
