package com.solsticeentertainment.solarapocalypse.client;

import com.solsticeentertainment.solarapocalypse.Announcer;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.IOException;

/**
 * Client only. The phase splash: the title big in the splash font, flickering between two colours, the phase's message
 * small underneath; fades in, stays and fades out like a vanilla title.
 */
public final class SplashOverlay {

    private static Announcer.Splash shown;
    private static int shownAt;
    private static FontRenderer titleFont, messageFont;

    private SplashOverlay() {}

    public static void show(Announcer.Splash splash) {
        shown = splash;
        shownAt = Minecraft.getMinecraft().ingameGUI.getUpdateCounter();
        titleFont = font(SolarConfig.splashTitleFont);
        messageFont = font(SolarConfig.splashMessageFont);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        Announcer.Splash s = shown;
        if (s == null || event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        float age = Minecraft.getMinecraft().ingameGUI.getUpdateCounter() - shownAt + event.getPartialTicks();
        float total = s.fadeIn + s.stay + s.fadeOut;
        if (age >= total) {
            shown = null;
            return;
        }
        float fade = age < s.fadeIn ? age / s.fadeIn : age > s.fadeIn + s.stay ? (total - age) / s.fadeOut : 1;
        int alpha = MathHelper.clamp((int) (fade * 255), 0, 255);
        if (alpha <= 8) return; // FontRenderer draws an alpha below 4 as opaque
        float flicker = (MathHelper.sin(age * 0.37F) + MathHelper.sin(age * 0.83F)) / 4 + 0.5F;
        ScaledResolution res = event.getResolution();
        GlStateManager.pushMatrix();
        GlStateManager.translate(res.getScaledWidth() / 2F, res.getScaledHeight() / 2F, 0);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.pushMatrix();
        GlStateManager.scale(4, 4, 4);
        titleFont.drawString(s.title, -titleFont.getStringWidth(s.title) / 2F, -10, blend(s.titleColor, s.flickerColor, flicker) | alpha << 24, true);
        GlStateManager.popMatrix();
        if (!s.message.isEmpty()) {
            float scale = 1.5F;
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, scale);
            float y = 5;
            for (String line : messageFont.listFormattedStringToWidth(s.message, (int) (res.getScaledWidth() / scale) - 20)) {
                messageFont.drawString(line, -messageFont.getStringWidth(line) / 2F, y, s.messageColor | alpha << 24, true);
                y += messageFont.FONT_HEIGHT + 1;
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static int blend(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** A font from a texture like minecraft:textures/font/ascii.png; Minecraft's own if empty or missing. */
    private static FontRenderer font(String texture) {
        Minecraft mc = Minecraft.getMinecraft();
        if (texture.isEmpty()) return mc.fontRenderer;
        ResourceLocation location = new ResourceLocation(texture);
        try (IResource ignored = mc.getResourceManager().getResource(location)) {
            FontRenderer font = new FontRenderer(mc.gameSettings, location, mc.getTextureManager(), false);
            font.onResourceManagerReload(mc.getResourceManager());
            return font;
        } catch (IOException e) {
            return mc.fontRenderer;
        }
    }
}
