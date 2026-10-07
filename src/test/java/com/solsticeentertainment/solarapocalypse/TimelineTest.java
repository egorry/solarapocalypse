package com.solsticeentertainment.solarapocalypse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTest {

    private static SolarConfig.Phase phase(double days, int depth, SolarConfig.Speed speed, double layersPerDay) {
        SolarConfig.Phase p = new SolarConfig.Phase();
        p.days = days;
        p.depth = depth;
        p.speed = speed;
        p.layersPerDay = layersPerDay;
        return p;
    }

    @Test
    void depthAndReachAgree() {
        SolarConfig.scaling = SolarConfig.Scaling.CONSTANT;
        SolarConfig.baseDays = 1;
        Timeline t = new Timeline(2, new SolarConfig.Phase[]{
                phase(1, 10, SolarConfig.Speed.PHASE, 0),     // days 2-3: 0 -> 10 layers
                phase(1, 15, SolarConfig.Speed.INSTANT, 0),   // day 3: 15 at once
                phase(1, 35, SolarConfig.Speed.RATE, 10),     // 20 layers at 10/day: lasts 2 days, not 1
                phase(1, SolarConfig.INFINITE, SolarConfig.Speed.PHASE, 4), // infinite: 4/day forever
                phase(1, 5, SolarConfig.Speed.PHASE, 0)});    // still infinite, 4/day continued
        long day = Timeline.DAY;
        assertEquals(-1, t.phaseAt(2 * day - 1));
        assertEquals(0, t.phaseAt(2 * day));
        assertEquals(5, t.depthAt(2 * day + day / 2), 1e-9);
        assertEquals(15, t.depthAt(3 * day), 1e-9);
        assertEquals(6 * day, t.end(2)); // RATE phase stretched to finish its layers
        assertEquals(35, t.depthAt(6 * day), 1e-9);
        assertEquals(39, t.depthAt(7 * day), 1e-9);
        assertEquals(43, t.depthAt(8 * day), 1e-9);
        assertEquals(4, t.phaseAt(100 * day));
        int top = 100;
        for (int y = top + 5; y > top - 60; y--) {
            long reach = t.reachTime(y, top);
            assertTrue(t.reached(y, top, reach), "y " + y);
            assertTrue(reach == t.firstStart() || !t.reached(y, top, reach - 1), "y " + y);
        }
    }
}
