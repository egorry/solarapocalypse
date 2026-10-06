package com.solsticeentertainment.solarapocalypse;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Cubic Chunks is optional: "after:" only orders loading, it never requires the mod.
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, dependencies = "after:cubicchunks")
public class SolarApocalypse {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        // Dev research probe (docs/RESEARCH.md); CubicProbe references CC classes, so it is only loaded with CC present.
        if (System.getProperty("solarapocalypse.probe") != null && Loader.isModLoaded("cubicchunks")) {
            com.solsticeentertainment.solarapocalypse.debug.CubicProbe.run(FMLCommonHandler.instance().getMinecraftServerInstance());
        }
    }

}
