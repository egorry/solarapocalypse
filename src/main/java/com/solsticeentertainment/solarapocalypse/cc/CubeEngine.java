package com.solsticeentertainment.solarapocalypse.cc;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.CubeEvent;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
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
        if (storage == null || storage.isEmpty()) return BlockChanges.NEVER;
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
                int top = heights.getHeightValue(lx, lz) - 1;
                for (int ly = 15; ly >= 0; ly--) {
                    IBlockState from = storage.get(lx, ly, lz);
                    if (from.getMaterial() == Material.AIR) continue;
                    int y = minY + ly;
                    int sky = y < top ? BlockChanges.SHADED
                            : CubicSky.knownClear(y, loadedUpTo) ? BlockChanges.EXPOSED : BlockChanges.UNKNOWN;
                    pos.setPos((cx << 4) + lx, y, (cz << 4) + lz);
                    IBlockState to = changes.evaluate(from, pos, sky, progress);
                    wake = Math.min(wake, changes.wake());
                    if (to == from) continue;
                    int xz = lz << 4 | lx;
                    if (!edited.get(xz) && edited.cardinality() >= MAX_COLUMN_EDITS_PER_TICK) return PARTIAL;
                    edited.set(xz);
                    BlockChanges.apply(world, pos, from, to);
                    blocksChanged++;
                    if (from.getLightOpacity() != to.getLightOpacity()) {
                        top = heights.getHeightValue(lx, lz) - 1;
                        if (top < minY) openedBelow = true;
                    }
                }
            }
        }
        if (openedBelow && cubes.getLoadedCube(cx, cy - 1, cz) != null) later.add(new CubePos(cx, cy - 1, cz));
        return wake;
    }
}
