package com.solsticeentertainment.solarapocalypse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApocalypseClockTest {

    @Test
    void timeSkipsCount() {
        assertEquals(1, ApocalypseClock.sunDelta(100, 101));                  // a tick
        assertEquals(11000, ApocalypseClock.sunDelta(13000, 24000));         // sleeping through the night
        assertEquals(12000, ApocalypseClock.sunDelta(5 * 24000 + 13000, 1000)); // /time set day at night: on to morning
        assertEquals(0, ApocalypseClock.sunDelta(5000, 4999));               // a day-length mod stepping back
        assertEquals(0, ApocalypseClock.sunDelta(5 * 24000 + 23550, 23500)); // /time set 50 before the time of day
        assertEquals(23200, ApocalypseClock.sunDelta(24000 + 300, 23500));  // 23500 again just after the rollover
        assertEquals(22500, ApocalypseClock.sunDelta(6 * 24000 + 1000, 23500)); // the user's /time set 23500 routine
        assertEquals(100000, ApocalypseClock.sunDelta(0, 100000));               // /time add: no cap
    }
}
