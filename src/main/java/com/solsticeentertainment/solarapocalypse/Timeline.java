package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.SolarConfig.Phase;
import com.solsticeentertainment.solarapocalypse.SolarConfig.Speed;

/**
 * Phase boundaries and the sun's depth as pure functions of progress (1 day = {@link #DAY} units), built from the config.
 * Depth counts layers below a reference Y: a block at y is reached once depth >= ref - y + 1 (layer 1 is ref itself).
 * There are two depth lines, one per reference ({@link #SURFACE}: each column's terrain surface, {@link #TOP}: world.topY);
 * each phase moves the line of its depthReference while the other stays where it is.
 * Everything that happens to a block is a function of (block, position, progress), so terrain loaded late is caught
 * up by evaluating the same functions.
 */
public final class Timeline {

    public static final long DAY = 24000;
    public static final long NEVER = Long.MAX_VALUE;
    public static final int SURFACE = 0, TOP = 1;

    private final long[] start;     // phase i starts at start[i]
    private final long[] end;       // and its effects (and the phase) last until end[i]
    private final int[] track;      // the depth line phase i moves
    private final double[][] fromDepth, toDepth; // [line][phase]: depth at the start and the end of the phase; the last
                                                 // phase's infinite depth grows forever
    private final boolean[] infinite;
    private final int[] firstPhase = {-1, -1}; // first phase on each line; a line reaches nothing before it
    private final double[] rate;    // layers per progress unit while the phase's line grows
    private final Speed[] speed;
    private final long[] convertStart; // conversions start once the phase's destruction is done
    private final long[] convertSpread;

    public Timeline(double safeDays, Phase[] phases) {
        int n = phases.length;
        start = new long[n];
        end = new long[n];
        track = new int[n];
        infinite = new boolean[n];
        fromDepth = new double[2][n];
        toDepth = new double[2][n];
        rate = new double[n];
        speed = new Speed[n];
        convertStart = new long[n];
        convertSpread = new long[n];
        long t = days(safeDays);
        double[] line = new double[2];
        int ref = SolarConfig.depthReference == SolarConfig.DepthReference.TOP_Y ? TOP : SURFACE;
        boolean endless = false;
        boolean destroys = false;
        for (int i = 0; i < n; i++) {
            Phase p = phases[i];
            if (p.depthReference != null) ref = p.depthReference == SolarConfig.DepthReference.TOP_Y ? TOP : SURFACE;
            track[i] = ref;
            double depth = line[ref];
            endless |= p.depth == SolarConfig.INFINITE; // once infinite, every later phase is too
            boolean infinite = this.infinite[i] = endless;
            boolean ownDestroy = p.destroy != null && p.destroy.length > 0;
            destroys = ownDestroy || destroys && SolarConfig.destroyRuleMode == SolarConfig.RuleMode.CARRY;
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
            for (int k = 0; k < 2; k++) {
                fromDepth[k][i] = line[k];
                toDepth[k][i] = k == ref ? target : line[k];
            }
            if (firstPhase[ref] < 0) firstPhase[ref] = i;
            rate[i] = speed[i] == Speed.PHASE ? (length > 0 ? (target - depth) / length : 0) : perUnit;
            // Tasks: destruction from the start, then conversion. The days are the minimum; the phase lasts until both are done.
            boolean destroying = !infinite && destroys && target > depth;
            convertStart[i] = destroying ? t + effects : t;
            if (infinite) convertSpread[i] = p.convertDays > 0 ? days(p.convertDays) : 0;
            else convertSpread[i] = p.convertDays < 0 ? Math.max(0, t + length - convertStart[i]) : days(p.convertDays);
            end[i] = infinite ? t + length : Math.max(t + length, convertStart[i] + convertSpread[i]);
            // an infinite depth grows at its rate until the phase ends (for ever in the last phase); the next phase on
            // this line continues from where this one got to
            if (infinite && i < n - 1) toDepth[ref][i] = depth + rate[i] * (end[i] - t);
            line[ref] = infinite ? depth + rate[i] * (end[i] - t) : target;
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
        return infinite[phase];
    }

    /** The depth line (SURFACE or TOP) a phase moves. */
    public int track(int phase) {
        return track[phase];
    }

    /** Whether any phase is on a depth line (if not, its reference is never needed). */
    public boolean uses(int line) {
        return firstPhase[line] >= 0;
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

    /** Layers a depth line has reached at progress p. */
    public double depthAt(long p, int line) {
        int i = phaseAt(p);
        if (i < 0) return 0;
        if (track[i] != line) return fromDepth[line][i];
        if (speed[i] == Speed.INSTANT) return toDepth[line][i];
        return Math.min(fromDepth[line][i] + rate[i] * (p - start[i]), toDepth[line][i]);
    }

    /** Whether a block at y is reached by a depth line at progress p, for reference Y ref. */
    public boolean reached(int y, int ref, long p, int line) {
        return p >= firstStart() && depthAt(p, line) >= (double) ref - y + 1;
    }

    /** First progress at which a depth line reaches a block at y (reference Y ref), or NEVER. */
    public long reachTime(int y, int ref, int line) {
        double need = (double) ref - y + 1;
        if (firstPhase[line] < 0) return NEVER;
        for (int i = firstPhase[line]; i < start.length; i++) {
            double from = fromDepth[line][i];
            if (from >= need) return start[i];
            if (track[i] != line) continue;
            if (speed[i] == Speed.INSTANT) {
                if (toDepth[line][i] >= need) return start[i];
                continue;
            }
            if (toDepth[line][i] < need || rate[i] <= 0) continue;
            long t = start[i] + (long) Math.ceil((need - from) / rate[i]);
            return Math.max(t, start[i]);
        }
        return NEVER;
    }

    public long firstStart() {
        return start.length == 0 ? NEVER : start[0];
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
