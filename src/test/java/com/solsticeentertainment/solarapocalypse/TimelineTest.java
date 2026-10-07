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
        p.destroy = new String[]{"*"};
        p.convertDays = -1;
        return p;
    }

    private static double depth(Timeline t, long p) {
        return t.depthAt(p, Timeline.SURFACE);
    }

    @Test
    void eachReferenceHasItsOwnLine() {
        SolarConfig.scaling = SolarConfig.Scaling.CONSTANT;
        SolarConfig.baseDays = 1;
        SolarConfig.Phase top = phase(1, 20, SolarConfig.Speed.RATE, 10);
        top.depthReference = SolarConfig.DepthReference.TOP_Y;
        SolarConfig.Phase back = phase(1, 8, SolarConfig.Speed.PHASE, 0);
        back.depthReference = SolarConfig.DepthReference.SURFACE;
        Timeline t = new Timeline(0, new SolarConfig.Phase[]{
                phase(1, 5, SolarConfig.Speed.PHASE, 0), // day 0-1: surface line 0 -> 5
                top,                                     // day 1-3: top line 0 -> 20 at 10/day, surface stays at 5
                back});                                  // day 3-4: surface line 5 -> 8, top stays at 20
        long day = Timeline.DAY;
        assertEquals(Timeline.TOP, t.track(1));
        assertEquals(5, t.depthAt(2 * day, Timeline.SURFACE), 1e-9);
        assertEquals(10, t.depthAt(2 * day, Timeline.TOP), 1e-9);
        assertEquals(20, t.depthAt(3 * day + day / 2, Timeline.TOP), 1e-9);
        assertEquals(6.5, t.depthAt(3 * day + day / 2, Timeline.SURFACE), 1e-9);
        assertEquals(day + day / 2, t.reachTime(196, 200, Timeline.TOP)); // layer 5 below Y 200: half a day in
        assertTrue(t.uses(Timeline.TOP) && t.uses(Timeline.SURFACE));
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
                phase(1, 5, SolarConfig.Speed.PHASE, 8)});    // still infinite, now 8/day
        long day = Timeline.DAY;
        assertEquals(-1, t.phaseAt(2 * day - 1));
        assertEquals(0, t.phaseAt(2 * day));
        assertEquals(5, depth(t, 2 * day + day / 2), 1e-9);
        assertEquals(15, depth(t, 3 * day), 1e-9);
        assertEquals(6 * day, t.end(2)); // RATE phase stretched to finish its layers
        assertEquals(6 * day, t.convertStart(2)); // conversions after the destruction
        assertEquals(0, t.convertSpread(2));      // nothing left of the phase's days: at once
        assertEquals(2 * day + day, t.convertStart(0)); // PHASE destruction takes the phase
        assertEquals(35, depth(t, 6 * day), 1e-9);
        assertEquals(39, depth(t, 7 * day), 1e-9);
        assertEquals(47, depth(t, 8 * day), 1e-9);
        assertEquals(4, t.phaseAt(100 * day));
        int top = 100;
        for (int y = top + 5; y > top - 80; y--) { // into the last phase: reach must follow its own rate
            long reach = t.reachTime(y, top, Timeline.SURFACE);
            assertTrue(t.reached(y, top, reach, Timeline.SURFACE), "y " + y);
            assertTrue(reach == t.firstStart() || !t.reached(y, top, reach - 1, Timeline.SURFACE), "y " + y);
        }
    }
}
