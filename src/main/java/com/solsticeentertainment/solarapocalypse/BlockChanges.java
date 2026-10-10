package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockFalling;
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
 *   passes it (layer by layer); blocks above the reference (trees, buildings, the sea over the terrain) go top down
 *   early in the phase, also in the first phase on a line; other blocks reached before the phase go at a random but
 *   fixed moment within it.
 * - convert: blocks in the layers of the column's current surface a rule acts in (BlockRules.Step) change, once the
 *   phase's destruction is done, at a random but fixed moment over the rest of the phase. A chain of rules is followed to
 *   its end.
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

    /** The deepest layer below a column's surface that a conversion acts in. */
    public int deepestLayer() {
        return rules.deepestLayer();
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
            if (step == null) break;
            if (surface == NO_Y) {
                later(progress + RECHECK);
                break;
            }
            int layer = (int) Math.max(1, Math.min(Integer.MAX_VALUE, (long) surface - y + 1)); // 1 = the surface block and what is on it
            int rule = step.pick(x, y, z, state, layer, phase);
            if (rule < 0) { // no rule acts in this layer, or this block's share stays
                int next = step.nextLayer(layer, phase);
                if (next > 0) laterNear(y + next, ground, topY, phase, progress); // look again once the surface is that close
                break;
            }
            int timing = step.timing(rule, layer, phase);
            long due = spread(timeline.convertStart(timing), timeline.convertSpread(timing), x, y, z, timing);
            if (progress < due) {
                later(due);
                break;
            }
            state = step.target(rule);
        }
        return evaporate(state, y, sky, progress);
    }

    /**
     * Asks for another look once the running phase's depth line has removed the block at y (so the surface has come down
     * to y - 1 or below): a deeper block then enters the layers of a depth-staged conversion.
     */
    private void laterNear(int y, int ground, int topY, int phase, long progress) {
        int line = timeline.track(phase);
        int ref = line == Timeline.TOP ? topY : ground;
        if (ref == NO_Y) {
            later(progress + RECHECK);
            return;
        }
        long reach = timeline.reachTime(y, ref, line);
        if (reach != NEVER) later(Math.max(reach, progress + 1));
    }

    /** When a depth line removes a block that a phase's destroy rule matches (NEVER if the line never gets there). */
    private long destroyDue(int x, int y, int z, int ref, int line, int destroyedBy) {
        if (!timeline.uses(line)) return NEVER;
        long reach = timeline.reachTime(y, ref, line);
        long start = timeline.start(destroyedBy);
        if (reach == NEVER) return NEVER;
        if (y > ref) { // trees, buildings, the sea over the terrain: top down, early in the first phase that has the line there
            long from = Math.max(reach, start);
            return from + aboveSurface(y - ref, timeline.spread(reach > start ? timeline.phaseAt(reach) : destroyedBy));
        }
        if (reach >= start) return reach; // reached while the phase runs: layer by layer
        return spread(start, timeline.spread(destroyedBy), x, y, z, destroyedBy);
    }

    /**
     * The fire that belongs above a column's surface block now: solar fire, vanilla fire (flammable surface), or null.
     * Fire comes after the phase's conversions (none while the surface block still has a conversion pending). Outside
     * infinite phases one roll serves every phase, so a phase's percent is the total alight (25 % then 50 % keeps the
     * first 25 %) and lit fire is left alone. In an infinite phase each surface block rolls once: when the erosion takes
     * it, the block below rolls for the new surface; fire on a surface the line has not reached stays as it is. Solar fire
     * is tagged with the depth it was lit at (/solar fire). With blocks.nightDousesFire there is no fire at night (see dark).
     */
    public IBlockState fire(World world, BlockPos surfacePos, IBlockState surface, long progress) {
        wake = NEVER;
        int phase = timeline.phaseAt(progress);
        if (phase < 0) return null;
        SolarConfig.Phase p = SolarConfig.phases[phase];
        if (p.ignitePercent <= 0 && p.igniteFlammablePercent <= 0) return null;
        int x = surfacePos.getX(), y = surfacePos.getY(), z = surfacePos.getZ();
        BlockRules.Step pending = rules.convert(phase, surface);
        if (pending != null && pending.pick(x, y, z, surface, 1, phase) >= 0) return null;
        boolean infinite = timeline.infinite(phase);
        int line = timeline.track(phase);
        int epoch = infinite ? (int) timeline.depthAt(progress, line) : phase;
        boolean flammable = burns(world, surface, surfacePos, EnumFacing.UP);
        double percent = flammable ? p.igniteFlammablePercent : p.ignitePercent;
        if (Timeline.hash(x, y, z, infinite ? FIRE_SALT : TOTAL_FIRE_SALT) * 100 >= percent) return null;
        if (!flammable && !surface.getMaterial().blocksMovement()) return null; // paths, glass, slabs too; not liquids
        long due = infinite ? progress : spread(timeline.convertStart(phase), timeline.convertSpread(phase), x, y, z, FIRE_SALT - epoch);
        if (progress < due) {
            later(due);
            return null;
        }
        if (SolarConfig.nightDousesFire && dark(world, x, y, z, progress)) return null;
        return flammable ? Blocks.FIRE.getDefaultState() : SolarFire.forEpoch(epoch);
    }

    /** Time of day: sunset starts, sunrise starts; each spot's fire goes out (comes back) within FADE of them. */
    private static final long DUSK = 12000, DAWN = 23000, FADE = 2000;
    private static final int NIGHT_SALT = 0x419E7000;

    /**
     * Whether night has put out a spot's fire (blocks.nightDousesFire): each spot goes dark at its own moment over the
     * sunset and lights again (the same spot) at its own moment over the sunrise, from the world's time of day.
     */
    private boolean dark(World world, int x, int y, int z, long progress) {
        long sinceDusk = Math.floorMod(world.getWorldTime() - DUSK, Timeline.DAY);
        long out = (long) (Timeline.hash(x, y, z, NIGHT_SALT) * FADE);
        long back = DAWN - DUSK + (long) (Timeline.hash(x, y, z, NIGHT_SALT + 1) * FADE);
        boolean dark = sinceDusk >= out && sinceDusk < back;
        long ticks = sinceDusk < out ? out - sinceDusk : dark ? back - sinceDusk : Timeline.DAY - sinceDusk + out;
        // progress follows the sun one to one in SUN mode; in TICKS mode it runs at its own rate
        double perTick = SolarConfig.clockMode == SolarConfig.ClockMode.SUN ? 1 : (double) Timeline.DAY / SolarConfig.ticksPerDay;
        later(progress + (long) Math.ceil(ticks * perTick));
        return dark;
    }

    private static final int FIRE_SALT = 1 << 20, TOTAL_FIRE_SALT = 0x7F1E0000;

    /**
     * Whether solar fire that should not be there now is removed: in infinite phases (its ground went, or the phase's
     * roll differs from the one before), and with blocks.nightDousesFire (night). Otherwise fire, once lit, is left alone.
     */
    public boolean managesFire(long progress) {
        int phase = timeline.phaseAt(progress);
        return SolarConfig.nightDousesFire || phase >= 0 && timeline.infinite(phase);
    }

    /** The shortest gap between two looks at a cube, and the engine clock's step: MIN_WAKE, or a layer's time in an infinite phase. */
    public long minWake(long progress) {
        int phase = timeline.phaseAt(progress);
        double perDay = phase < 0 || !timeline.infinite(phase) ? 0 : timeline.layersPerDay(phase);
        return perDay <= 0 ? MIN_WAKE : Math.max(1, Math.min(MIN_WAKE, (long) (Timeline.DAY / perDay)));
    }


    /** Whether vanilla fire from the sun burns a block (with doFireTick false it would neither spread nor burn out). */
    private boolean burns(World world, IBlockState state, BlockPos pos, EnumFacing face) {
        return world.getGameRules().getBoolean("doFireTick") && !rules.noVanillaFire(state) && state.getBlock().isFlammable(world, pos, face);
    }

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
     * Writes a change: no neighbour updates unless blocks.blockPhysics (but see settle), no drops unless
     * blocks.dropItems, also from blocks that pop off. Every change the apocalypse makes goes through here (counted for
     * performance.maxBlockChangesPerTick).
     */
    public static void apply(World world, BlockPos pos, IBlockState from, IBlockState to) {
        if (!SolarConfig.dropItems && from.getBlock().hasTileEntity(from)) world.removeTileEntity(pos); // no container spill
        if (SolarConfig.dropItems && to.getMaterial() == Material.AIR) from.getBlock().dropBlockAsItem(world, pos, from, 0);
        boolean restoring = world.restoringBlockSnapshots;
        world.restoringBlockSnapshots |= !SolarConfig.dropItems; // Forge's no-drops switch (used when it restores blocks)
        try {
            world.setBlockState(pos, to, SolarConfig.blockPhysics ? 3 : 2 | 16);
            if (to.getBlock() instanceof BlockBush && world.isBlockLoaded(pos.down()) && !((BlockBush) to.getBlock()).canBlockStay(world, pos, to)) {
                // a plant made where it cannot live (a dead bush on grass) would pop off on its next random tick, with drops
                world.setBlockState(pos, AIR, SolarConfig.blockPhysics ? 3 : 2 | 16);
                to = AIR;
            }
            if (!SolarConfig.blockPhysics) settle(world, pos, to);
        } finally {
            world.restoringBlockSnapshots = restoring;
        }
        changed++;
        if (SolarFire.is(to)) solarFireLit++;
        else if (to.getBlock() == Blocks.FIRE) vanillaFireLit++;
    }

    /**
     * Without block physics, what sits on top of or hangs on the side of a changed block still gets its neighbour update,
     * so what the new block cannot hold pops off now instead of later, with drops (plants and crops on paths, torches on
     * top or on the side, the top half of tall plants and doors, ladders, fire). Full blocks, sand and gravel, and liquids
     * are not told: nothing falls or flows.
     */
    private static void settle(World world, BlockPos pos, IBlockState to) {
        for (EnumFacing side : SETTLE) {
            BlockPos n = pos.offset(side);
            if (!world.isBlockLoaded(n)) continue;
            IBlockState state = world.getBlockState(n);
            if (state.getMaterial() == Material.AIR || state.isFullCube() || state.getBlock() instanceof BlockFalling
                    || BlockRules.isLiquid(state) || !aroundLoaded(world, n)) continue;
            state.neighborChanged(world, n, to.getBlock(), pos);
        }
    }

    private static final EnumFacing[] SETTLE = {EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    /** Whether a block's six neighbours are loaded (its update may look at them: generate nothing). */
    private static boolean aroundLoaded(World world, BlockPos pos) {
        for (EnumFacing side : EnumFacing.values()) if (!world.isBlockLoaded(pos.offset(side))) return false;
        return true;
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
