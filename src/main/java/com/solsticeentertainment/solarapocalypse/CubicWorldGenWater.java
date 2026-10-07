package com.solsticeentertainment.solarapocalypse;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/** Water level of a CubicWorldGen CustomCubic world (read by reflection: CWG is not a dependency), else the sea level. */
final class CubicWorldGenWater {

    private CubicWorldGenWater() {}

    static int level(World world) {
        if (Loader.isModLoaded("cubicgen") && "CustomCubic".equals(world.getWorldType().getName())) {
            try {
                Class<?> settings = Class.forName("io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.CustomGeneratorSettings");
                Object preset = settings.getMethod("getFromWorld", World.class).invoke(null, world);
                return settings.getField("waterLevel").getInt(preset);
            } catch (ReflectiveOperationException | RuntimeException e) {
                SolarApocalypse.LOGGER.warn("Could not read the CubicWorldGen water level, using the sea level", e);
            }
        }
        return world.getSeaLevel();
    }
}
