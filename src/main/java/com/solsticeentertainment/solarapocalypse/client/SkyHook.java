package com.solsticeentertainment.solarapocalypse.client;

import com.solsticeentertainment.solarapocalypse.Announcer;

/**
 * Client only. Minecraft's sky drawing calls this for the sun's half-size (30 blocks at 100 away), through the coremod's
 * patch of RenderGlobal.renderSky (core.SolarCore), and draws the sun as big as it returns (sun.grow). It follows the size
 * the server sends about once a second, each step spread over a second; a size sent on joining or changing world shows
 * at once.
 */
public final class SkyHook {
    private static float shown = 1;
    private static long last;

    private SkyHook() {}

    public static float sun(float halfSize) {
        long now = System.nanoTime();
        float target = Announcer.SunSize.size;
        if (Announcer.SunSize.snap) {
            Announcer.SunSize.snap = false;
            shown = target;
        } else {
            shown += (target - shown) * (float) Math.min(1, (now - last) * 1.0E-9);
        }
        last = now;
        return halfSize * shown;
    }
}
