package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * What the apocalypse does to one block at a given progress, for one world: a pure function of (block, position, sky,
 * progress), so the same evaluation serves live progression and catch-up of terrain loaded later.
 *
 * - destroy rules remove blocks the depth line has reached: a block reached during the rule's phase goes when the line
 *   reaches it (layer by layer); one reached earlier goes at a random but fixed time within the phase.
 * - convert rules change blocks that are sun-exposed or reached by the depth line, at a random but fixed time within the
 *   phase (INSTANT phases: at the start).
 * - evaporation removes sun-exposed liquids from their phase on (LAYERS mode: once the descending level passes them).
 * Rules of every started phase apply in phase order, each to the result of the previous one.
 */
public final class BlockChanges {

    public static final int SHADED = 0, EXPOSED = 1, UNKNOWN = 2;
    public static final long NEVER = Timeline.NEVER;
    /** How soon to look again at a block whose sky is unknown, and the shortest gap between two looks at a cube. */
    private static final long RECHECK = Timeline.DAY / 20;
    public static final long MIN_WAKE = RECHECK;
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private final Timeline timeline;
    private final BlockRules rules;
    public final int topY;
    public final int evaporationTopY;
    private long wake;

    public BlockChanges(Timeline timeline, BlockRules rules, int topY, int evaporationTopY) {
        this.timeline = timeline;
        this.rules = rules;
        this.topY = topY;
        this.evaporationTopY = evaporationTopY;
    }

    /** When the block last passed to {@link #evaluate} next needs a look (NEVER if it will not change by itself). */
    public long wake() {
        return wake;
    }

    public IBlockState evaluate(IBlockState state, BlockPos pos, int sky, long progress) {
        wake = NEVER;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int current = timeline.phaseAt(progress);
        for (int q = 0; q < timeline.phaseCount(); q++) {
            if (q > current) {
                if (rules.hasRule(q, state)) wake = Math.min(wake, timeline.start(q));
                break;
            }
            if (rules.destroys(q, state)) {
                long reach = timeline.reachTime(y, topY);
                long due = reach == NEVER ? NEVER : reach >= timeline.start(q) ? reach : spreadTime(x, y, z, q);
                if (progress >= due) return AIR;
                wake = Math.min(wake, due);
            }
            IBlockState to = rules.convert(q, state);
            if (to == null) continue;
            long due = spreadTime(x, y, z, q);
            if (sky != EXPOSED && !timeline.reached(y, topY, progress)) {
                long reach = timeline.reachTime(y, topY);
                wake = Math.min(wake, sky == UNKNOWN ? progress + RECHECK : reach == NEVER ? NEVER : Math.max(due, reach));
            } else if (progress < due) {
                wake = Math.min(wake, due);
            } else {
                state = to;
            }
        }
        return evaporate(state, y, sky, progress);
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
        if (sky == UNKNOWN) {
            wake = Math.min(wake, Math.max(due, progress + RECHECK));
            return state;
        }
        if (sky == SHADED) return state;
        if (progress >= due) return AIR;
        wake = Math.min(wake, due);
        return state;
    }

    private long spreadTime(int x, int y, int z, int phase) {
        return timeline.start(phase) + (long) (Timeline.hash(x, y, z, phase) * timeline.spread(phase));
    }

    /** Writes a change: no neighbour updates unless blocks.blockPhysics, no drops unless blocks.dropItems. */
    public static void apply(World world, BlockPos pos, IBlockState from, IBlockState to) {
        if (!SolarConfig.dropItems && from.getBlock().hasTileEntity(from)) world.removeTileEntity(pos); // no container spill
        if (SolarConfig.dropItems && to.getMaterial() == Material.AIR) from.getBlock().dropBlockAsItem(world, pos, from, 0);
        world.setBlockState(pos, to, SolarConfig.blockPhysics ? 3 : 2 | 16);
    }
}
