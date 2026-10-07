package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.SolarConfig.Phase;
import com.solsticeentertainment.solarapocalypse.SolarConfig.Speed;

/**
 * Phase boundaries and the sun's depth as pure functions of progress (1 day = {@link #DAY} units), built from the config.
 * Depth counts layers below the reference Y: a block at y is reached once depth >= topY - y + 1 (layer 1 is topY itself).
 * Everything that happens to a block is a function of (block, position, progress), so terrain loaded late is caught
 * up by evaluating the same functions.
 */
public final class Timeline {

    public static final long DAY = 24000;
    public static final long NEVER = Long.MAX_VALUE;

    private final long[] start;     // phase i starts at start[i]
    private final long[] end;       // and its effects (and the phase) last until end[i]
    private final double[] fromDepth, toDepth; // depth at the start and the end of the phase; toDepth infinite = grows forever
    private final double[] rate;    // layers per progress unit while the depth grows
    private final Speed[] speed;
    private final long[] convertStart; // conversions start once the phase's destruction is done
    private final long[] convertSpread;

    public Timeline(double safeDays, Phase[] phases) {
        int n = phases.length;
        start = new long[n];
        end = new long[n];
        fromDepth = new double[n];
        toDepth = new double[n];
        rate = new double[n];
        speed = new Speed[n];
        convertStart = new long[n];
        convertSpread = new long[n];
        long t = days(safeDays);
        double depth = 0;
        boolean infinite = false;
        boolean destroys = false;
        for (int i = 0; i < n; i++) {
            Phase p = phases[i];
            infinite |= p.depth == SolarConfig.INFINITE; // once infinite, every later phase is too
            boolean ownDestroy = p.destroy != null && p.destroy.length > 0;
            destroys = ownDestroy || destroys && SolarConfig.ruleMode == SolarConfig.RuleMode.CARRY;
            double target = infinite ? Double.POSITIVE_INFINITY : Math.max(depth, p.depth);
            long length = days(p.days > 0 ? p.days : SolarConfig.baseDays * scale(i + 1));
            double perUnit = Math.max(p.layersPerDay, 0) / DAY;
            if (infinite && perUnit == 0 && i > 0) perUnit = rate[i - 1]; // an inherited infinite depth keeps the last rate
            long effects; // how long the depth takes to descend this phase
            if (infinite) {
                speed[i] = Speed.RATE;
                effects = length;
            } else if (p.speed == Speed.RATE && target > depth) {
                speed[i] = Speed.RATE;
                effects = perUnit > 0 ? (long) Math.ceil((target - depth) / perUnit) : NEVER / 4;
            } else {
                speed[i] = p.speed;
                effects = p.speed == Speed.INSTANT ? 0 : length;
            }
            start[i] = t;
            fromDepth[i] = depth;
            toDepth[i] = target;
            rate[i] = speed[i] == Speed.PHASE ? (length > 0 ? (target - depth) / length : 0) : perUnit;
            // Tasks: destruction from the start, then conversion. The days are the minimum; the phase lasts until both are done.
            boolean destroying = !infinite && destroys && target > depth;
            convertStart[i] = destroying ? t + effects : t;
            if (infinite) convertSpread[i] = p.convertDays > 0 ? days(p.convertDays) : 0;
            else convertSpread[i] = p.convertDays < 0 ? Math.max(0, t + length - convertStart[i]) : days(p.convertDays);
            end[i] = infinite ? t + length : Math.max(t + length, convertStart[i] + convertSpread[i]);
            // an infinite depth keeps growing; the next phase continues from where this one got to
            depth = infinite ? depth + rate[i] * (end[i] - t) : target;
            t = end[i];
        }
    }

    private static double scale(int n) {
        switch (SolarConfig.scaling) {
            case LINEAR: return n;
            case QUADRATIC: return (double) n * n;
            case EXPONENTIAL: return Math.pow(SolarConfig.scalingFactor, n - 1);
            default: return 1;
        }
    }

    public static long days(double days) {
        return (long) Math.max(0, Math.round(days * DAY));
    }

    public int phaseCount() {
        return start.length;
    }

    /** 0-based index of the phase running at progress p, -1 during the safe phase. */
    public int phaseAt(long p) {
        int i = start.length - 1;
        while (i >= 0 && start[i] > p) i--;
        return i;
    }

    public long start(int phase) {
        return start[phase];
    }

    public long end(int phase) {
        return end[phase];
    }

    /** Time over which a phase spreads blocks it reached before it started (0 = all at the phase start). */
    public long spread(int phase) {
        return speed[phase] == Speed.INSTANT ? 0 : end[phase] - start[phase];
    }

    /** Whether a phase's depth is infinite (it descends for ever; it and every later phase). */
    public boolean infinite(int phase) {
        return Double.isInfinite(toDepth[phase]);
    }

    /** When a phase's conversions begin: after its destruction is done (at the start if it destroys nothing or is infinite). */
    public long convertStart(int phase) {
        return convertStart[phase];
    }

    /** Time over which a phase spreads its conversions after convertStart (0 = all at once). */
    public long convertSpread(int phase) {
        return convertSpread[phase];
    }

    /** Layers per day of a phase that descends at a rate (RATE speed or infinite), else 0. */
    public double layersPerDay(int phase) {
        return speed[phase] == Speed.RATE ? rate[phase] * DAY : 0;
    }

    /** Layers reached at progress p. */
    public double depthAt(long p) {
        int i = phaseAt(p);
        if (i < 0) return 0;
        if (speed[i] == Speed.INSTANT) return toDepth[i];
        return Math.min(fromDepth[i] + rate[i] * (p - start[i]), toDepth[i]);
    }

    /** Whether a block at y is reached at progress p, for reference Y topY. */
    public boolean reached(int y, int topY, long p) {
        return p >= firstStart() && depthAt(p) >= (double) topY - y + 1;
    }

    /** First progress at which a block at y is reached, or NEVER. */
    public long reachTime(int y, int topY) {
        double need = (double) topY - y + 1;
        for (int i = 0; i < start.length; i++) {
            double from = fromDepth[i];
            if (from >= need) return start[i];
            if (speed[i] == Speed.INSTANT) {
                if (toDepth[i] >= need) return start[i];
                continue;
            }
            if (toDepth[i] < need || rate[i] <= 0) continue;
            long t = start[i] + (long) Math.ceil((need - from) / rate[i]);
            return Math.max(t, start[i]);
        }
        return NEVER;
    }

    public long firstStart() {
        return start.length == 0 ? NEVER : start[0];
    }

    /** Start of a 1-based phase number from the config (0 or out of range = never). */
    public long startOfPhaseNumber(int number) {
        return number >= 1 && number <= start.length ? start[number - 1] : NEVER;
    }

    /** Deterministic value in [0, 1) for a position and phase. */
    public static double hash(int x, int y, int z, int phase) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ phase * 0xD6E8FEB86659FD93L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
