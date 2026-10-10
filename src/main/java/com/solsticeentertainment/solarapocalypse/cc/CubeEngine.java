package com.solsticeentertainment.solarapocalypse.cc;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.Sky;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import com.solsticeentertainment.solarapocalypse.SurfaceRecord;
import com.solsticeentertainment.solarapocalypse.Timeline;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.CubeEvent;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * Applies the apocalypse to loaded cubes of Cubic Chunks worlds. Cubes are queued when they load (new or reloaded:
 * that is the retroactive catch-up), when a phase starts, and when the time a block in them is due arrives. Queued
 * cubes are processed once ready (populated, lit, surface-tracked), within the per-tick time budget. Processing a cube
 * evaluates {@link BlockChanges} for each block from the top down, so a removal can expose the block below in the same
 * pass. Nothing here loads or generates a cube.
 *
 * One clock per world: every cube is brought up to the world's engine clock, which moves on only once every cube due
 * by it has been processed (a round). While the engine keeps up the clock is the present. While it cannot, the clock
 * moves one step (a layer in an infinite phase) per round, so the whole world comes down evenly at the speed the engine
 * manages, and cubes that load or wake meanwhile join at the same layer as their neighbours. The clock steps into each
 * phase at its start, and is saved, so a restart carries on where the engine was. /time and /solar time commands bring
 * it straight to the present; sleeping does not. Each round goes nearest the players first. A pass the budget cuts
 * short resumes first on the next tick.
 */
public final class CubeEngine {

    private static final class State {
        final Set<CubePos> queue = new LinkedHashSet<>();   // this round
        final Set<CubePos> resume = new LinkedHashSet<>();  // passes cut short: before the queue on the next tick
        final Set<CubePos> next = new LinkedHashSet<>();    // loaded, placed into or ready since the round began: the next round
        final Set<CubePos> notReady = new HashSet<>();      // looked at again every 20 ticks
        final Map<CubePos, Long> wake = new HashMap<>();
        final Map<Long, BitSet> columnEdits = new HashMap<>(); // x/z edited this tick, per column
        long clock = UNSET; // the time every cube is brought up to
    }

    private static final long UNSET = Long.MIN_VALUE;

    /** CC's client heightmap packet counts changed x/z in a byte: keep a column under 256 per tick. */
    private static final int MAX_COLUMN_EDITS_PER_TICK = 255;

    private static final Map<World, State> STATES = new WeakHashMap<>();

    private CubeEngine() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(CubeEngine.class);
    }

    private static State state(World world) {
        return STATES.computeIfAbsent(world, w -> new State());
    }

    /**
     * Queue every loaded cube of a world (config load or reload: at the engine's clock), and with jump bring the clock
     * straight to the present (/solar set, add, phase).
     */
    public static void queueAll(WorldServer world, boolean jump) {
        State state = state(world);
        queueLoaded(world, state);
        if (jump) setClock(world, state, ApocalypseClock.progress());
    }

    private static void queueLoaded(WorldServer world, State state) {
        for (Chunk column : world.getChunkProvider().getLoadedChunks()) {
            for (ICube cube : ((IColumn) column).getLoadedCubes()) state.queue.add(cube.getCoords());
        }
        state.wake.clear();
    }

    /** A /time skip: the clock comes straight to the present, without steps (sleeping steps: SolarApocalypse.skipped). */
    public static void jump(WorldServer world, long progress) {
        State state = state(world);
        Timeline timeline = SolarApocalypse.timeline();
        boolean newPhase = state.clock == UNSET || timeline.phaseAt(state.clock) != timeline.phaseAt(progress);
        setClock(world, state, progress);
        if (newPhase) queueLoaded(world, state);
        else wakeUntil(state, progress);
    }

    /** Ready cubes waiting to be processed (not counting cubes not yet lit or scheduled for a later time). */
    public static int queued(World world) {
        State state = STATES.get(world);
        return state == null ? 0 : state.queue.size() + state.resume.size() + state.next.size();
    }

    /** Totals since server start, for /solar status and tests. */
    public static long nanos, cubesProcessed;

    /** The time a world's engine has brought its cubes up to (the present while it keeps up), or -1 before it runs. */
    public static long clock(World world) {
        State state = STATES.get(world);
        return state == null || state.clock == UNSET ? -1 : state.clock;
    }

    /** How far behind the present the engine is in a world, in progress units: 0 while it keeps up (within a step). */
    public static long behind(World world) {
        long clock = clock(world), progress = ApocalypseClock.progress();
        return clock < 0 || progress - clock <= SolarApocalypse.changes(world).minWake(clock) ? 0 : progress - clock;
    }

    private static boolean processing, loadDuringProcessingReported;
    private static long cubeStart;

    @SubscribeEvent
    public static void onCubeLoad(CubeEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || !SolarApocalypse.isActive(world)) return;
        state(world).next.add(event.getCube().getCoords());
        if (processing && !loadDuringProcessingReported) {
            loadDuringProcessingReported = true; // once per game: names the block callback that read an unloaded neighbour
            SolarApocalypse.LOGGER.warn("Cube {} loaded while the apocalypse was changing blocks (reported once)",
                    event.getCube().getCoords(), new Throwable("cube load during a block change"));
        }
    }

    /** A block a player (or wand, enderman...) placed changes in the engine's next round, so placing cannot hold it off. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !SolarApocalypse.isActive(world) || !CubicSky.isCubic(world)) return;
        state(world).next.add(CubePos.fromBlockCoords(event.getPos()));
    }

    @SubscribeEvent
    public static void onCubeUnload(CubeEvent.Unload event) {
        State state = STATES.get(event.getWorld());
        if (state == null) return;
        CubePos pos = event.getCube().getCoords();
        state.queue.remove(pos);
        state.resume.remove(pos);
        state.next.remove(pos);
        state.notReady.remove(pos);
        state.wake.remove(pos);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || !SolarApocalypse.isActive(event.world)) return;
        if (!(event.world instanceof WorldServer) || !CubicSky.isCubic(event.world)) return;
        long progress = ApocalypseClock.progress();
        if (progress < SolarApocalypse.timeline().firstStart()) return;
        run((WorldServer) event.world, progress);
    }

    private static void run(WorldServer world, long progress) {
        State state = state(world);
        state.columnEdits.clear();
        BlockChanges changes = SolarApocalypse.changes(world);
        if (state.clock == UNSET || state.clock > progress) { // first run (the saved clock, else the present), or set back
            long saved = ApocalypseClock.engineClock(world.provider.getDimension());
            state.clock = saved < 0 || saved > progress ? progress : saved;
        }
        boolean second = world.getTotalWorldTime() % 20 == 0;
        if (second) {
            state.next.addAll(state.notReady);
            state.notReady.clear();
        }
        // a round is done: the next one takes what came in meanwhile, at a clock moved on (straight away while behind)
        if (state.queue.isEmpty() && state.resume.isEmpty() && (second || progress - state.clock > changes.minWake(state.clock))) {
            state.queue.addAll(state.next);
            state.next.clear();
            if (state.clock < progress) advance(world, state, changes, progress);
            nearestFirst(world, state.queue);
        }
        if (state.queue.isEmpty() && state.resume.isEmpty()) return;
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        long at = state.clock;
        List<CubePos> later = new ArrayList<>(), cut = new ArrayList<>();
        while ((!state.resume.isEmpty() || !state.queue.isEmpty()) && SolarApocalypse.hasBudget()) {
            // no iterator held across process(): a block's own callbacks can load a cube, which queues it (onCubeLoad)
            Iterator<CubePos> it = (state.resume.isEmpty() ? state.queue : state.resume).iterator();
            CubePos pos = it.next();
            it.remove();
            state.wake.remove(pos);
            ICube cube = cubes.getLoadedCube(pos);
            if (cube == null) continue;
            if (!cube.isFullyPopulated() || !cube.isInitialLightingDone() || !cube.isSurfaceTracked()) {
                state.notReady.add(pos);
                continue;
            }
            long t0 = cubeStart = System.nanoTime();
            processing = true;
            long wake;
            try {
                wake = process(world, cubes, cube, changes, at, state, later);
            } finally {
                processing = false;
            }
            nanos += System.nanoTime() - t0;
            cubesProcessed++;
            if (wake == PARTIAL) {
                cut.add(pos); // not again this tick: the column limit that stopped it holds until the next one
                continue;
            }
            // ponytail: one look per cube per MIN_WAKE (a layer in infinite phases) at most: spread conversions land in
            // batches; per-block timers if that shows
            if (wake != BlockChanges.NEVER) state.wake.put(pos, Math.max(wake, at + changes.minWake(at)));
        }
        state.resume.addAll(cut);
        state.queue.addAll(later); // cubes below opened this round: the same round
    }

    /**
     * Moves a world's clock on after a round: one step (a layer in an infinite phase), or straight to the next look due
     * if that is later, never past the present or into the next phase's start (where every loaded cube is looked at),
     * and queues the cubes due by then.
     */
    private static void advance(WorldServer world, State state, BlockChanges changes, long progress) {
        // ponytail: a scan of every waiting cube per round; a time-ordered map if many short rounds show in the profile
        long first = BlockChanges.NEVER;
        for (long time : state.wake.values()) first = Math.min(first, time);
        long to = Math.min(progress, Math.max(state.clock + changes.minWake(state.clock), first));
        Timeline timeline = SolarApocalypse.timeline();
        int phase = timeline.phaseAt(state.clock);
        boolean newPhase = phase + 1 < timeline.phaseCount() && to >= timeline.start(phase + 1);
        setClock(world, state, newPhase ? timeline.start(phase + 1) : to);
        if (newPhase) queueLoaded(world, state);
        else wakeUntil(state, state.clock);
    }

    /**
     * Orders a round nearest the players first (by column), each column top down, so a round the budget spreads over many
     * ticks reaches the players' surroundings first and spreads out from them.
     */
    private static void nearestFirst(WorldServer world, Set<CubePos> queue) {
        List<EntityPlayer> players = world.playerEntities;
        if (queue.size() < 2 || players.isEmpty()) return;
        int[] px = new int[players.size()], pz = new int[players.size()];
        for (int i = 0; i < px.length; i++) {
            px[i] = Coords.blockToCube(MathHelper.floor(players.get(i).posX));
            pz[i] = Coords.blockToCube(MathHelper.floor(players.get(i).posZ));
        }
        List<CubePos> order = new ArrayList<>(queue);
        Map<CubePos, Long> key = new HashMap<>(order.size() * 2);
        for (CubePos pos : order) {
            long nearest = Long.MAX_VALUE;
            for (int i = 0; i < px.length; i++) {
                long dx = pos.getX() - px[i], dz = pos.getZ() - pz[i];
                nearest = Math.min(nearest, dx * dx + dz * dz);
            }
            key.put(pos, Math.min(nearest, Integer.MAX_VALUE) << 32 | (0xFFFFFFFFL & ~(pos.getY() ^ Integer.MIN_VALUE))); // then higher cubes first
        }
        order.sort(Comparator.comparingLong(key::get));
        queue.clear();
        queue.addAll(order);
    }

    private static void setClock(WorldServer world, State state, long clock) {
        state.clock = clock;
        ApocalypseClock.setEngineClock(world.provider.getDimension(), clock);
    }

    private static void wakeUntil(State state, long time) {
        for (Iterator<Map.Entry<CubePos, Long>> it = state.wake.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<CubePos, Long> e = it.next();
            if (e.getValue() <= time) {
                state.queue.add(e.getKey());
                it.remove();
            }
        }
    }

    private static final long PARTIAL = Long.MIN_VALUE;

    /** Brings one cube up to date. Returns when it next needs a look, NEVER, or PARTIAL if the column limit stopped it. */
    private static long process(WorldServer world, ICubeProvider cubes, ICube cube, BlockChanges changes, long progress, State state,
                                List<CubePos> later) {
        ExtendedBlockStorage storage = cube.getStorage();
        boolean empty = storage == null || storage.isEmpty();
        if (empty && !changes.ignites(progress)) return BlockChanges.NEVER; // an empty cube can only get fire
        int cx = cube.getX(), cy = cube.getY(), cz = cube.getZ();
        Chunk column = cubes.getLoadedColumn(cx, cz);
        if (column == null) return BlockChanges.NEVER;
        IColumn heights = (IColumn) column;
        int loadedUpTo = CubicSky.highestLoadedAbove(cubes, cx, cy, cz);
        int minY = cy << 4;
        boolean needGround = SolarApocalypse.timeline().uses(Timeline.SURFACE);
        int topY = SolarApocalypse.topY(world);
        boolean manageFire = changes.managesFire(progress);
        long wake = BlockChanges.NEVER;
        boolean openedBelow = false;
        int deep = changes.deepestLayer();
        BitSet edited = state.columnEdits.computeIfAbsent(((long) cx << 32) | (cz & 0xFFFFFFFFL), k -> new BitSet(256));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                int ceiling = CubicSky.ceiling(world, x, z);
                int top = heights.getHeightValue(lx, lz) - 1; // topmost opaque block (sky rule)
                if (empty && top != minY - 1) continue; // fire in an empty cube only stands on the cube below
                int surface = surface(cubes, cx, cz, lx, lz, top);
                boolean surfaceKnown = CubicSky.knownClear(surface, loadedUpTo, ceiling);
                int ground = needGround ? ground(world, cubes, column, lx, lz, x, z, surface, surfaceKnown) : BlockChanges.NO_Y;
                int fireY = BlockChanges.NO_Y; // solar fire found in this column of the cube
                boolean litInPlace = false; // the fire at fireY was lit in place of the block the erosion took (checked then)
                boolean liquidSeen = false; // a standing liquid left higher up in this column of the cube
                int liquidOver = 0; // a standing liquid between the cube's top and the column's surface: 1 yes, -1 no, 0 not looked yet
                for (int ly = 15; ly >= 0 && !empty; ly--) {
                    int y = minY + ly;
                    if (y < top && changes.stillBelow(y, ground, topY, surfaceKnown ? surface : BlockChanges.NO_Y, progress)) {
                        wake = Math.min(wake, changes.wake());
                        break; // in an infinite phase most of the cube: the rest of the column waits for the line
                    }
                    IBlockState from = storage.get(lx, ly, lz);
                    if (from.getMaterial() == Material.AIR) continue;
                    if (SolarFire.is(from)) {
                        if (fireY == BlockChanges.NO_Y) fireY = y;
                        else if (!manageFire) continue;
                        else if (limited(edited, lz << 4 | lx)) return PARTIAL;
                        else BlockChanges.apply(world, pos.setPos(x, y, z), from, AIR); // a second one is from an earlier layer
                        continue;
                    }
                    int sky = y < SolarConfig.sunFloorY || y < top ? Sky.SHADED
                            : CubicSky.knownClear(y, loadedUpTo, ceiling) ? Sky.EXPOSED : Sky.UNKNOWN;
                    pos.setPos(x, y, z);
                    IBlockState to = changes.evaluate(from, pos, ground, topY, surfaceKnown ? surface : BlockChanges.NO_Y, sky, progress);
                    wake = Math.min(wake, changes.wake());
                    if (to != from && changes.destroyed() && y < surface) {
                        // under standing liquid (a sea floor and what lies below it): waits for the liquid to go, then
                        // catches up in the pass that takes it, or at the latest RECHECK later
                        if (!liquidSeen && liquidOver == 0) liquidOver = liquidAbove(cubes, cx, cz, lx, lz, minY + 16, surface) ? 1 : -1;
                        if (liquidSeen || liquidOver > 0) {
                            wake = Math.min(wake, progress + BlockChanges.RECHECK);
                            to = from;
                        }
                    }
                    if (standing(to)) liquidSeen = true;
                    if (to == from) continue;
                    if (limited(edited, lz << 4 | lx)) return PARTIAL;
                    if (!to.getMaterial().blocksMovement()) {
                        // the solar fire on it goes first, as a counted change without neighbour updates (it would go anyway,
                        // through its neighbour update)
                        IBlockState above = ly < 15 ? storage.get(lx, ly + 1, lz) : blockAt(cubes, cx, cz, lx, y + 1, lz);
                        if (above != null && SolarFire.is(above)) {
                            BlockChanges.apply(world, pos.setPos(x, y + 1, z), above, AIR);
                            if (fireY == y + 1) fireY = BlockChanges.NO_Y;
                            if (limited(edited, lz << 4 | lx)) return PARTIAL;
                        }
                        // the surface block the erosion takes becomes the new surface's fire in the same change
                        IBlockState fire = to.getMaterial() == Material.AIR && surfaceKnown && surface == y
                                ? fireInPlace(world, cubes, storage, changes, pos, cx, cz, lx, ly, lz, x, y, z, ground, topY, progress) : null;
                        if (fire != null) {
                            to = fire;
                            fireY = y;
                            litInPlace = true;
                        }
                        pos.setPos(x, y, z);
                    }
                    BlockChanges.apply(world, pos, from, to);
                    if (from.getLightOpacity() != to.getLightOpacity() || BlockChanges.isSurface(from) != BlockChanges.isSurface(to)) {
                        top = heights.getHeightValue(lx, lz) - 1;
                        surface = surface(cubes, cx, cz, lx, lz, top);
                        surfaceKnown = CubicSky.knownClear(surface, loadedUpTo, ceiling);
                        // the cube below needs a look once the surface is within the conversions' layers of its top;
                        // queued now, as a pass can stop early (PARTIAL) and never get back to this column
                        if (!openedBelow && (top < minY || surface - minY + 2 <= deep)) {
                            openedBelow = true;
                            if (cubes.getLoadedCube(cx, cy - 1, cz) != null) later.add(new CubePos(cx, cy - 1, cz));
                        }
                    }
                }
                // Fire: placed on the surface once the phase's conversions are done; removed when its ground goes (infinite
                // phases) or at night (blocks.nightDousesFire). Fire that stays where it should is left alone.
                IBlockState fire = null;
                if (surfaceKnown && Coords.blockToCube(surface + 1) == cy) {
                    IBlockState below = blockAt(cubes, cx, cz, lx, surface, lz);
                    if (below != null) {
                        fire = changes.fire(world, pos.setPos(x, surface, z).toImmutable(), below, progress);
                        wake = Math.min(wake, changes.wake());
                        if (fire != null && SolarFire.is(fire) && !(litInPlace && fireY == surface + 1)
                                && nearFlammable(world, cubes, pos, x, surface + 1, z)) fire = null;
                    }
                }
                boolean stale = fireY != BlockChanges.NO_Y && manageFire && !(fire != null && SolarFire.is(fire) && fireY == surface + 1);
                if (stale) {
                    if (limited(edited, lz << 4 | lx)) return PARTIAL;
                    BlockChanges.apply(world, pos.setPos(x, fireY, z), storage.get(lx, Coords.blockToLocal(fireY), lz), AIR);
                }
                if (fire != null && (stale || fireY != surface + 1)) {
                    pos.setPos(x, surface + 1, z);
                    IBlockState there = world.getBlockState(pos); // loaded: the cube being processed
                    // vanilla fire looks at its six neighbours when placed: only where they are loaded
                    if (there.getMaterial() == Material.AIR && (SolarFire.is(fire) || neighboursLoaded(cubes, pos))) {
                        if (limited(edited, lz << 4 | lx)) return PARTIAL;
                        BlockChanges.apply(world, pos, there, fire);
                    }
                }
            }
        }
        return wake;
    }

    /**
     * The solar fire a surface block about to be destroyed can become at once (one change instead of two): when the block
     * under it, the new surface, does not change in this pass and the fire belongs on it. Null otherwise: the block goes
     * and the fire is placed after the column, as usual.
     */
    private static IBlockState fireInPlace(WorldServer world, ICubeProvider cubes, ExtendedBlockStorage storage, BlockChanges changes,
                                           BlockPos.MutableBlockPos pos, int cx, int cz, int lx, int ly, int lz, int x, int y, int z,
                                           int ground, int topY, long progress) {
        IBlockState below = ly > 0 ? storage.get(lx, ly - 1, lz) : blockAt(cubes, cx, cz, lx, y - 1, lz);
        if (below == null || !below.getMaterial().blocksMovement()) return null;
        pos.setPos(x, y - 1, z);
        if (changes.evaluate(below, pos, ground, topY, y - 1, Sky.SHADED, progress) != below) return null; // solid: sky does not matter
        IBlockState fire = changes.fire(world, pos.toImmutable(), below, progress);
        if (fire == null || !SolarFire.is(fire) || nearFlammable(world, cubes, pos, x, y, z)) return null;
        return fire;
    }

    /**
     * Whether a loaded block around a position (diagonals too) can burn. Solar fire is not placed there: it would take the
     * space vanilla fire spreads into, and look odd against wood that never catches (with fire spread off). The block it
     * stands on does not count (a flammable one gets vanilla fire, or solar fire on top when vanilla fire is off for it).
     * Ice and snow nearby are fine: vanilla melts them from the fire's light.
     */
    private static boolean nearFlammable(World world, ICubeProvider cubes, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    pos.setPos(x + dx, y + dy, z + dz);
                    IBlockState s = blockAt(cubes, Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getZ()),
                            Coords.blockToLocal(pos.getX()), pos.getY(), Coords.blockToLocal(pos.getZ()));
                    if (s == null || s.getMaterial() == Material.AIR || s.getMaterial() == Material.FIRE) continue; // fire: flammability 0
                    if (dx == 0 && dz == 0 && dy == -1) continue; // the ground it stands on
                    if (knownNotFlammable(s)) continue;
                    if (s.getBlock().isFlammable(world, pos, EnumFacing.getFacingFromVector(-dx, -dy, -dz))) return true;
                    rememberNotFlammable(s);
                }
            }
        }
        return false;
    }

    // Recently seen blocks that cannot burn: nearFlammable asks up to 25 neighbours per fire placed, nearly all stone and
    // the like. Server thread only. ponytail: never cleared, as Blocks.FIRE's table is filled at startup.
    private static final Block[] NOT_FLAMMABLE = new Block[16];
    private static int notFlammableNext;

    private static boolean knownNotFlammable(IBlockState state) {
        Block block = state.getBlock();
        for (Block b : NOT_FLAMMABLE) if (b == block) return true;
        return false;
    }

    private static void rememberNotFlammable(IBlockState state) {
        if (DEFAULT_FLAMMABILITY.get(state.getBlock().getClass())) NOT_FLAMMABLE[notFlammableNext++ & 15] = state.getBlock();
    }

    /** Whether a block class keeps Forge's flammability (Blocks.FIRE's table, the same at every position), so it can be remembered. */
    private static final ClassValue<Boolean> DEFAULT_FLAMMABILITY = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("isFlammable", IBlockAccess.class, BlockPos.class, EnumFacing.class).getDeclaringClass() == Block.class
                        && type.getMethod("getFlammability", IBlockAccess.class, BlockPos.class, EnumFacing.class).getDeclaringClass() == Block.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private static boolean neighboursLoaded(ICubeProvider cubes, BlockPos pos) {
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos n = pos.offset(side);
            if (cubes.getLoadedCube(Coords.blockToCube(n.getX()), Coords.blockToCube(n.getY()), Coords.blockToCube(n.getZ())) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a change at x/z must wait for the next tick (time budget, performance.maxBlockChangesPerTick, column limit);
     * marks x/z if not. The budget is checked here too, so one cube with many slow changes (lighting) cannot overrun it.
     */
    private static boolean limited(BitSet edited, int xz) {
        if (!SolarApocalypse.hasBudget(System.nanoTime() - cubeStart)) return true;
        if (!edited.get(xz) && edited.cardinality() >= MAX_COLUMN_EDITS_PER_TICK) return true;
        edited.set(xz);
        return false;
    }

    /** Fire in the loaded cubes of the columns within a radius (in columns) of a block position. */
    public static final class FireCensus {
        public int solar, vanilla, columns;
        public final Map<Integer, Integer> epochs = new TreeMap<>();
    }

    public static FireCensus fireCensus(World world, BlockPos center, int radius) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        FireCensus census = new FireCensus();
        int ccx = Coords.blockToCube(center.getX()), ccz = Coords.blockToCube(center.getZ());
        for (int cx = ccx - radius; cx <= ccx + radius; cx++) {
            for (int cz = ccz - radius; cz <= ccz + radius; cz++) {
                Chunk column = cubes.getLoadedColumn(cx, cz);
                if (column == null) continue;
                census.columns++;
                for (ICube cube : new ArrayList<>(((IColumn) column).getLoadedCubes())) {
                    ExtendedBlockStorage storage = cube.getStorage();
                    if (storage == null || storage.isEmpty()) continue;
                    for (int i = 0; i < 4096; i++) {
                        IBlockState s = storage.get(i & 15, i >> 8, (i >> 4) & 15);
                        if (SolarFire.is(s)) {
                            census.solar++;
                            census.epochs.merge(SolarFire.epochOf(s), 1, Integer::sum);
                        } else if (s.getBlock() == Blocks.FIRE) {
                            census.vanilla++;
                        }
                    }
                }
            }
        }
        return census;
    }

    /** The surface conversions count from: the topmost opaque block, raised over glass, liquids and the like stacked on it. */
    private static int surface(ICubeProvider cubes, int cx, int cz, int lx, int lz, int top) {
        for (int n = 0; n < 256; n++) {
            IBlockState above = blockAt(cubes, cx, cz, lx, top + 1, lz);
            if (above == null || !BlockChanges.isSurface(above)) break;
            top++;
        }
        return top;
    }

    /** A column's terrain surface (SURFACE depth reference): from CubicWorldGen, or the recorded one (NO_Y if unknown). */
    private static int ground(WorldServer world, ICubeProvider cubes, Chunk column, int lx, int lz, int x, int z, int surface,
                              boolean surfaceKnown) {
        CwgSurface model = CubicSky.model(world);
        if (model != null) {
            int top = model.top(x, z);
            return top == CwgSurface.NONE ? BlockChanges.NO_Y : top;
        }
        SurfaceRecord record = SurfaceRecord.of(column);
        if (record == null) return BlockChanges.NO_Y;
        int recorded = record.get(lx, lz);
        if (recorded != SurfaceRecord.NONE || !surfaceKnown) return recorded == SurfaceRecord.NONE ? BlockChanges.NO_Y : recorded;
        for (int y = surface; y > surface - 64; y--) { // first sight: the ground under trees, plants and liquids
            IBlockState state = blockAt(cubes, column.x, column.z, lx, y, lz);
            if (state == null) return BlockChanges.NO_Y;
            if (BlockChanges.isGround(state)) {
                record.set(lx, lz, y);
                column.markDirty();
                return y;
            }
        }
        return BlockChanges.NO_Y;
    }

    /** The terrain surface (SURFACE depth reference) at x, z without recording one (for /solar status), or NO_Y. */
    public static int groundAt(World world, int x, int z) {
        CwgSurface model = CubicSky.model(world);
        if (model != null) {
            int top = model.top(x, z);
            return top == CwgSurface.NONE ? BlockChanges.NO_Y : top;
        }
        Chunk column = ((ICubicWorld) world).getCubeCache().getLoadedColumn(Coords.blockToCube(x), Coords.blockToCube(z));
        SurfaceRecord record = column == null ? null : SurfaceRecord.of(column);
        int recorded = record == null ? SurfaceRecord.NONE : record.get(Coords.blockToLocal(x), Coords.blockToLocal(z));
        return recorded == SurfaceRecord.NONE ? BlockChanges.NO_Y : recorded;
    }

    /** Whether a liquid stands in a column from y up to its surface (loaded cubes only: an unloaded one counts as none). */
    private static boolean liquidAbove(ICubeProvider cubes, int cx, int cz, int lx, int lz, int y, int surface) {
        for (; y <= surface; y++) {
            IBlockState state = blockAt(cubes, cx, cz, lx, y, lz);
            if (state == null) return false;
            if (standing(state)) return true;
        }
        return false;
    }

    /**
     * A standing liquid, which the ground under it waits for: a vanilla source block (seas, lakes, pools), or any block of
     * another fluid. Vanilla water flowing into a cut after a pass does not hold the ground: it ebbs by itself, unseen.
     */
    private static boolean standing(IBlockState state) {
        return state.getMaterial().isLiquid() && !(state.getBlock() instanceof BlockLiquid && state.getValue(BlockLiquid.LEVEL) != 0);
    }

    /** A block of a loaded cube, or null if its cube is not loaded. */
    private static IBlockState blockAt(ICubeProvider cubes, int cx, int cz, int lx, int y, int lz) {
        ICube cube = cubes.getLoadedCube(cx, Coords.blockToCube(y), cz);
        if (cube == null) return null;
        ExtendedBlockStorage storage = cube.getStorage();
        return storage == null ? AIR : storage.get(lx, Coords.blockToLocal(y), lz);
    }

    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
}
