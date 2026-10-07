package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * What the apocalypse does to one block at a given progress: a pure function of (block, position, the column's
 * reference and current surface, sky, progress), so the same evaluation serves live progression and the catch-up of
 * terrain loaded later.
 *
 * - destroy: blocks matching an active destroy rule go once the phase depth reaches them. Depth counts layers below the
 *   column's reference (its terrain surface, or world.topY): a block reached during the rule's phase goes when the depth
 *   passes it (layer by layer); blocks above the reference (trees, buildings) go top down early in the phase; other
 *   blocks reached before the phase go at a random but fixed moment within it.
 * - convert: blocks in the top convertDepth layers of the column's current surface change, once the phase's destruction
 *   is done, at a random but fixed moment over the rest of the phase. A chain of rules is followed to its end.
 * - evaporation: sun-exposed liquids go from their phase on (LAYERS mode: once a descending level passes them).
 */
public final class BlockChanges {

    public static final int NO_Y = Integer.MIN_VALUE;
    public static final long NEVER = Timeline.NEVER;
    /** How soon to look again at a block whose column is not known yet, and the shortest gap between two looks at a cube. */
    public static final long RECHECK = Timeline.DAY / 20;
    public static final long MIN_WAKE = RECHECK;
    private static final int MAX_CHAIN = 16;
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    /** Totals since server start (/solar status, tests). */
    public static long changed, solarFireLit, vanillaFireLit;

    private final Timeline timeline;
    private final BlockRules rules;
    public final int evaporationTopY;
    private long wake;

    public BlockChanges(Timeline timeline, BlockRules rules, int evaporationTopY) {
        this.timeline = timeline;
        this.rules = rules;
        this.evaporationTopY = evaporationTopY;
    }

    /** When the block last passed to {@link #evaluate} next needs a look (NEVER if it will not change by itself). */
    public long wake() {
        return wake;
    }

    /**
     * @param ground  the column's terrain surface, the reference of the SURFACE depth line, or NO_Y if not known yet
     * @param topY    world.topY, the reference of the TOP depth line
     * @param surface the column's current surface (topmost block that blocks movement or is liquid), or NO_Y if not known
     * @param sky     Sky.EXPOSED / SHADED / UNKNOWN for the block itself
     */
    public IBlockState evaluate(IBlockState state, BlockPos pos, int ground, int topY, int surface, int sky, long progress) {
        wake = NEVER;
        int phase = timeline.phaseAt(progress);
        if (phase < 0) return state;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        int destroyedBy = rules.destroyPhase(phase, state);
        if (destroyedBy >= 0) {
            long due = destroyDue(x, y, z, topY, Timeline.TOP, destroyedBy);
            if (timeline.uses(Timeline.SURFACE)) {
                if (ground == NO_Y) later(progress + RECHECK); // the surface line may get here once the ground is known
                else due = Math.min(due, destroyDue(x, y, z, ground, Timeline.SURFACE, destroyedBy));
            }
            if (progress >= due) return AIR;
            later(due);
        }

        for (int n = 0; n < MAX_CHAIN; n++) {
            BlockRules.Step step = rules.convert(phase, state);
            IBlockState target = step == null ? null : step.pick(x, y, z, state);
            if (target == null) break; // no rule, or this block's share stays
            if (surface == NO_Y) {
                later(progress + RECHECK);
                break;
            }
            if (y <= (long) surface - SolarConfig.phases[step.phase].convertDepth) break; // below the surface layer
            long due = spread(timeline.convertStart(step.phase), timeline.convertSpread(step.phase), x, y, z, step.phase);
            if (progress < due) {
                later(due);
                break;
            }
            state = target;
        }
        return evaporate(state, y, sky, progress);
    }

    /** When a depth line removes a block that a phase's destroy rule matches (NEVER if the line never gets there). */
    private long destroyDue(int x, int y, int z, int ref, int line, int destroyedBy) {
        if (!timeline.uses(line)) return NEVER;
        long reach = timeline.reachTime(y, ref, line);
        long start = timeline.start(destroyedBy), length = timeline.spread(destroyedBy);
        if (reach == NEVER) return NEVER;
        if (reach >= start) return reach; // reached while the phase runs: layer by layer
        if (y > ref) return start + aboveSurface(y - ref, length); // trees, buildings: top down
        return spread(start, length, x, y, z, destroyedBy);
    }

    /**
     * The fire that belongs above a column's surface block now: solar fire, vanilla fire (flammable surface), or null.
     * Fire comes after the phase's conversions (none while the surface block still has a conversion pending) and is
     * tagged with an epoch: the phase, or in an infinite phase the layer, so fire from an earlier epoch can be removed.
     */
    public IBlockState fire(World world, BlockPos surfacePos, IBlockState surface, long progress) {
        wake = NEVER;
        int phase = timeline.phaseAt(progress);
        if (phase < 0) return null;
        SolarConfig.Phase p = SolarConfig.phases[phase];
        if (p.ignitePercent <= 0 && p.igniteFlammablePercent <= 0) return null;
        int x = surfacePos.getX(), y = surfacePos.getY(), z = surfacePos.getZ();
        BlockRules.Step pending = rules.convert(phase, surface);
        if (pending != null && pending.pick(x, y, z, surface) != null) return null;
        boolean infinite = timeline.infinite(phase);
        int line = timeline.track(phase);
        int epoch = infinite ? (int) timeline.depthAt(progress, line) : phase;
        if (infinite) later(timeline.reachTime(-epoch, 0, line)); // the next layer redraws the fire
        // with doFireTick false vanilla fire would neither spread nor burn out
        boolean flammable = world.getGameRules().getBoolean("doFireTick") && !rules.noVanillaFire(surface)
                && surface.getBlock().isFlammable(world, surfacePos, EnumFacing.UP);
        double percent = flammable ? p.igniteFlammablePercent : p.ignitePercent;
        if (Timeline.hash(x, y, z, FIRE_SALT + epoch) * 100 >= percent) return null;
        if (!flammable && !surface.isTopSolid()) return null;
        long due = infinite ? progress : spread(timeline.convertStart(phase), timeline.convertSpread(phase), x, y, z, FIRE_SALT - epoch);
        if (progress < due) {
            later(due);
            return null;
        }
        return flammable ? Blocks.FIRE.getDefaultState() : SolarFire.forEpoch(epoch);
    }

    private static final int FIRE_SALT = 1 << 20;

    /** Whether the running phase sets anything alight. */
    public boolean ignites(long progress) {
        int phase = timeline.phaseAt(progress);
        return phase >= 0 && (SolarConfig.phases[phase].ignitePercent > 0 || SolarConfig.phases[phase].igniteFlammablePercent > 0);
    }

    private void later(long time) {
        wake = Math.min(wake, time);
    }

    private IBlockState evaporate(IBlockState state, int y, int sky, long progress) {
        int phase = rules.evaporationPhase(state);
        if (phase < 0) return state;
        long start = timeline.start(phase);
        long due = start;
        if (SolarConfig.evaporationMode == SolarConfig.EvaporationMode.LAYERS) {
            double layers = (double) evaporationTopY - y + 1;
            if (layers > 0) {
                due = SolarConfig.evaporationLayersPerDay <= 0 ? NEVER
                        : start + (long) Math.ceil(layers * Timeline.DAY / SolarConfig.evaporationLayersPerDay);
            }
        }
        if (sky == Sky.UNKNOWN) {
            later(Math.max(due, progress + RECHECK));
            return state;
        }
        if (sky == Sky.SHADED) return state;
        if (progress >= due) return AIR;
        later(due);
        return state;
    }

    /** Blocks above the reference go top down over the first 1/10 of the phase (world.surfaceMargin blocks high and up first). */
    private static long aboveSurface(int height, long phaseLength) {
        int margin = Math.max(1, SolarConfig.surfaceMargin);
        return (long) ((phaseLength / 10.0) * (1 - Math.min(height, margin) / (double) margin));
    }

    private static long spread(long from, long length, int x, int y, int z, int phase) {
        return from + (long) (Timeline.hash(x, y, z, phase) * length);
    }

    /**
     * Writes a change: no neighbour updates unless blocks.blockPhysics, no drops unless blocks.dropItems. Every change the
     * apocalypse makes goes through here (counted for performance.maxBlockChangesPerTick).
     */
    public static void apply(World world, BlockPos pos, IBlockState from, IBlockState to) {
        if (!SolarConfig.dropItems && from.getBlock().hasTileEntity(from)) world.removeTileEntity(pos); // no container spill
        if (SolarConfig.dropItems && to.getMaterial() == Material.AIR) from.getBlock().dropBlockAsItem(world, pos, from, 0);
        world.setBlockState(pos, to, SolarConfig.blockPhysics ? 3 : 2 | 16);
        changed++;
        if (SolarFire.is(to)) solarFireLit++;
        else if (to.getBlock() == Blocks.FIRE) vanillaFireLit++;
    }

    /** The surface that conversions measure from: blocks movement or is liquid (leaves, glass and water count; plants do not). */
    public static boolean isSurface(IBlockState state) {
        Material m = state.getMaterial();
        return m.blocksMovement() || m.isLiquid();
    }

    /** Terrain for a recorded reference surface: like isSurface, without trees and liquids. */
    public static boolean isGround(IBlockState state) {
        Material m = state.getMaterial();
        return m.blocksMovement() && m != Material.LEAVES && m != Material.WOOD && m != Material.CACTUS && m != Material.GOURD;
    }
}
