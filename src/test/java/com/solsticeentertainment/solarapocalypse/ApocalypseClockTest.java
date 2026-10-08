package com.solsticeentertainment.solarapocalypse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApocalypseClockTest {

    @Test
    void timeSkipsCount() {
        long max = 24000;
        assertEquals(1, ApocalypseClock.sunDelta(100, 101, max));                  // a tick
        assertEquals(11000, ApocalypseClock.sunDelta(13000, 24000, max));         // sleeping through the night
        assertEquals(12000, ApocalypseClock.sunDelta(5 * 24000 + 13000, 1000, max)); // /time set day at night: on to morning
        assertEquals(0, ApocalypseClock.sunDelta(5000, 4999, max));               // a day-length mod stepping back
        assertEquals(24000, ApocalypseClock.sunDelta(0, 100000, max));            // /time add beyond maxSunJump
    }
}
