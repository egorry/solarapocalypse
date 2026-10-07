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
     * @param reference the column's reference Y for depths, or NO_Y if not known yet
     * @param surface   the column's current surface (topmost block that blocks movement or is liquid), or NO_Y if not known
     * @param sky       Sky.EXPOSED / SHADED / UNKNOWN for the block itself
     */
    public IBlockState evaluate(IBlockState state, BlockPos pos, int reference, int surface, int sky, long progress) {
        wake = NEVER;
        int phase = timeline.phaseAt(progress);
        if (phase < 0) return state;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        int destroyedBy = rules.destroyPhase(phase, state);
        if (destroyedBy >= 0) {
            if (reference == NO_Y) {
                later(progress + RECHECK);
            } else {
                long reach = timeline.reachTime(y, reference);
                long start = timeline.start(destroyedBy), length = timeline.spread(destroyedBy);
                long due;
                if (reach == NEVER) due = NEVER;
                else if (reach >= start) due = reach; // reached while the phase runs: layer by layer
                else if (y > reference) due = start + aboveSurface(y - reference, length); // trees, buildings: top down
                else due = spread(start, length, x, y, z, destroyedBy);
                if (progress >= due) return AIR;
                later(due);
            }
        }

        for (int n = 0; n < MAX_CHAIN; n++) {
            BlockRules.Step step = rules.convert(phase, state);
            if (step == null) break;
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
            state = step.target;
        }
        return evaporate(state, y, sky, progress);
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
        if (rules.convert(phase, surface) != null) return null;
        int x = surfacePos.getX(), y = surfacePos.getY(), z = surfacePos.getZ();
        boolean infinite = timeline.infinite(phase);
        int epoch = infinite ? (int) timeline.depthAt(progress) : phase;
        if (infinite) later(timeline.reachTime(-epoch, 0)); // the next layer redraws the fire
        boolean flammable = surface.getBlock().isFlammable(world, surfacePos, EnumFacing.UP);
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
        BlockRules.Liquid liquid = BlockRules.liquid(state);
        if (liquid == BlockRules.Liquid.NONE) return state;
        int number = liquid == BlockRules.Liquid.WATER ? SolarConfig.waterPhase
                : liquid == BlockRules.Liquid.LAVA ? SolarConfig.lavaPhase : SolarConfig.otherLiquidsPhase;
        long start = timeline.startOfPhaseNumber(number);
        if (start == NEVER) return state;
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

    /** Writes a change: no neighbour updates unless blocks.blockPhysics, no drops unless blocks.dropItems. */
    public static void apply(World world, BlockPos pos, IBlockState from, IBlockState to) {
        if (!SolarConfig.dropItems && from.getBlock().hasTileEntity(from)) world.removeTileEntity(pos); // no container spill
        if (SolarConfig.dropItems && to.getMaterial() == Material.AIR) from.getBlock().dropBlockAsItem(world, pos, from, 0);
        world.setBlockState(pos, to, SolarConfig.blockPhysics ? 3 : 2 | 16);
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
