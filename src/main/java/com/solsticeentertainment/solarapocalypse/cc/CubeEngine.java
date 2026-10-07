package com.solsticeentertainment.solarapocalypse.cc;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.Sky;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import com.solsticeentertainment.solarapocalypse.SurfaceRecord;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.CubeEvent;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Applies the apocalypse to loaded cubes of Cubic Chunks worlds. Cubes are queued when they load (new or reloaded:
 * that is the retroactive catch-up), when a phase starts, and when the time a block in them is due arrives. Queued
 * cubes are processed once ready (populated, lit, surface-tracked), within the per-tick time budget. Processing a cube
 * evaluates {@link BlockChanges} for each block from the top down, so a removal can expose the block below in the same
 * pass. Nothing here loads or generates a cube.
 */
public final class CubeEngine {

    private static final class State {
        final Set<CubePos> queue = new LinkedHashSet<>();
        final Set<CubePos> notReady = new HashSet<>(); // looked at again every 20 ticks
        final Map<CubePos, Long> wake = new HashMap<>();
        final Map<Long, BitSet> columnEdits = new HashMap<>(); // x/z edited this tick, per column
    }

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

    /** Queue every loaded cube of a world (phase start, time jump, config reload). */
    public static void queueAll(WorldServer world) {
        State state = state(world);
        for (Chunk column : world.getChunkProvider().getLoadedChunks()) {
            for (ICube cube : ((IColumn) column).getLoadedCubes()) state.queue.add(cube.getCoords());
        }
        state.wake.clear();
    }

    /** Ready cubes waiting to be processed (not counting cubes not yet lit or scheduled for a later time). */
    public static int queued(World world) {
        State state = STATES.get(world);
        return state == null ? 0 : state.queue.size();
    }

    /** Totals since server start, for /solar status and tests. */
    public static long nanos, cubesProcessed, blocksChanged;

    @SubscribeEvent
    public static void onCubeLoad(CubeEvent.Load event) {
        World world = event.getWorld();
        if (!world.isRemote && SolarApocalypse.isActive(world)) state(world).queue.add(event.getCube().getCoords());
    }

    @SubscribeEvent
    public static void onCubeUnload(CubeEvent.Unload event) {
        State state = STATES.get(event.getWorld());
        if (state == null) return;
        CubePos pos = event.getCube().getCoords();
        state.queue.remove(pos);
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
        if (world.getTotalWorldTime() % 20 == 0) {
            state.queue.addAll(state.notReady);
            state.notReady.clear();
            for (Iterator<Map.Entry<CubePos, Long>> it = state.wake.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<CubePos, Long> e = it.next();
                if (e.getValue() <= progress) {
                    state.queue.add(e.getKey());
                    it.remove();
                }
            }
        }
        if (state.queue.isEmpty()) return;
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockChanges changes = SolarApocalypse.changes(world);
        List<CubePos> later = new ArrayList<>();
        for (Iterator<CubePos> it = state.queue.iterator(); it.hasNext() && SolarApocalypse.hasBudget(); ) {
            CubePos pos = it.next();
            it.remove();
            ICube cube = cubes.getLoadedCube(pos);
            if (cube == null) continue;
            if (!cube.isFullyPopulated() || !cube.isInitialLightingDone() || !cube.isSurfaceTracked()) {
                state.notReady.add(pos);
                continue;
            }
            long t0 = System.nanoTime();
            long wake = process(world, cubes, cube, changes, progress, state, later);
            nanos += System.nanoTime() - t0;
            cubesProcessed++;
            if (wake == PARTIAL) later.add(pos);
            // ponytail: one look per cube per MIN_WAKE at most (spread conversions land in batches); per-block timers if that shows
            else if (wake != BlockChanges.NEVER) state.wake.put(pos, Math.max(wake, progress + BlockChanges.MIN_WAKE));
        }
        state.queue.addAll(later);
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
        long wake = BlockChanges.NEVER;
        boolean openedBelow = false;
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
                int reference = reference(world, cubes, column, lx, lz, x, z, surface, surfaceKnown);
                int fireY = BlockChanges.NO_Y; // solar fire found in this column of the cube
                for (int ly = 15; ly >= 0 && !empty; ly--) {
                    IBlockState from = storage.get(lx, ly, lz);
                    if (from.getMaterial() == Material.AIR) continue;
                    int y = minY + ly;
                    if (SolarFire.is(from)) {
                        if (fireY == BlockChanges.NO_Y) fireY = y;
                        else world.setBlockState(pos.setPos(x, y, z), AIR, 2 | 16); // a second one is always stale
                        continue;
                    }
                    int sky = y < SolarConfig.sunFloorY || y < top ? Sky.SHADED
                            : CubicSky.knownClear(y, loadedUpTo, ceiling) ? Sky.EXPOSED : Sky.UNKNOWN;
                    pos.setPos(x, y, z);
                    IBlockState to = changes.evaluate(from, pos, reference, surfaceKnown ? surface : BlockChanges.NO_Y, sky, progress);
                    wake = Math.min(wake, changes.wake());
                    if (to == from) continue;
                    int xz = lz << 4 | lx;
                    if (!edited.get(xz) && edited.cardinality() >= MAX_COLUMN_EDITS_PER_TICK) return PARTIAL;
                    edited.set(xz);
                    BlockChanges.apply(world, pos, from, to);
                    blocksChanged++;
                    if (from.getLightOpacity() != to.getLightOpacity() || BlockChanges.isSurface(from) != BlockChanges.isSurface(to)) {
                        top = heights.getHeightValue(lx, lz) - 1;
                        surface = surface(cubes, cx, cz, lx, lz, top);
                        surfaceKnown = CubicSky.knownClear(surface, loadedUpTo, ceiling);
                        if (top < minY) openedBelow = true;
                    }
                }
                // Fire: removed when its epoch is over, placed on the surface once the phase's conversions are done.
                IBlockState fire = null;
                if (surfaceKnown && Coords.blockToCube(surface + 1) == cy) {
                    IBlockState below = blockAt(cubes, cx, cz, lx, surface, lz);
                    if (below != null) {
                        fire = changes.fire(world, pos.setPos(x, surface, z).toImmutable(), below, progress);
                        wake = Math.min(wake, changes.wake());
                    }
                }
                boolean keep = fire != null && SolarFire.is(fire) && fireY == surface + 1
                        && SolarFire.epochOf(storage.get(lx, Coords.blockToLocal(fireY), lz)) == SolarFire.epochOf(fire);
                if (fireY != BlockChanges.NO_Y && !keep) {
                    world.setBlockState(pos.setPos(x, fireY, z), AIR, 2 | 16);
                    blocksChanged++;
                }
                if (fire != null && !keep) {
                    pos.setPos(x, surface + 1, z);
                    IBlockState there = world.getBlockState(pos); // loaded: the cube being processed
                    if (there.getMaterial() == Material.AIR) {
                        int xz = lz << 4 | lx;
                        if (!edited.get(xz) && edited.cardinality() >= MAX_COLUMN_EDITS_PER_TICK) return PARTIAL;
                        edited.set(xz);
                        BlockChanges.apply(world, pos, there, fire);
                        blocksChanged++;
                    }
                }
            }
        }
        if (openedBelow && cubes.getLoadedCube(cx, cy - 1, cz) != null) later.add(new CubePos(cx, cy - 1, cz));
        return wake;
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

    /** Depth reference of a column: world.topY, the CubicWorldGen terrain surface, or the recorded one (NO_Y if unknown). */
    private static int reference(WorldServer world, ICubeProvider cubes, Chunk column, int lx, int lz, int x, int z, int surface,
                                 boolean surfaceKnown) {
        if (SolarConfig.depthReference == SolarConfig.DepthReference.TOP_Y) return SolarApocalypse.topY(world);
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

    /** The depth reference at x, z without recording one (for /solar status), or NO_Y. */
    public static int referenceAt(World world, int x, int z) {
        if (SolarConfig.depthReference == SolarConfig.DepthReference.TOP_Y) return SolarApocalypse.topY(world);
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

    /** A block of a loaded cube, or null if its cube is not loaded. */
    private static IBlockState blockAt(ICubeProvider cubes, int cx, int cz, int lx, int y, int lz) {
        ICube cube = cubes.getLoadedCube(cx, Coords.blockToCube(y), cz);
        if (cube == null) return null;
        ExtendedBlockStorage storage = cube.getStorage();
        return storage == null ? AIR : storage.get(lx, Coords.blockToLocal(y), lz);
    }

    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
}
