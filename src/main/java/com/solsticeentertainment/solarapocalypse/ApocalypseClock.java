package com.solsticeentertainment.solarapocalypse;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The apocalypse's own clock: progress units (24000 per day) saved in the overworld's data/solarapocalypse.dat. Advanced
 * at the end of every server tick, from the overworld's worldTime (SUN) or from ticks (TICKS). Skipping time (sleeping,
 * /time add, /time set) moves it forward; nothing rewinds it. Also saved: each dimension's engine clock (the time the
 * block engine has brought that world up to, behind progress while it cannot keep up), so a restart carries on evenly.
 */
public final class ApocalypseClock extends WorldSavedData {

    private static final String NAME = Tags.MOD_ID;

    private static ApocalypseClock current;

    private long progress;
    private boolean paused;
    private long lastWorldTime = Long.MIN_VALUE;
    private long tickRemainder;
    private final Map<Integer, Long> engine = new HashMap<>();

    public ApocalypseClock(String name) {
        super(name);
    }

    /** Progress of the running server, or -1 with no server. */
    public static long progress() {
        return current == null ? -1 : current.progress;
    }

    public static boolean isPaused() {
        return current != null && current.paused;
    }

    public static void attach(MinecraftServer server) {
        WorldServer overworld = server.getWorld(0);
        ApocalypseClock clock = (ApocalypseClock) overworld.getMapStorage().getOrLoadData(ApocalypseClock.class, NAME);
        if (clock == null) {
            clock = new ApocalypseClock(NAME);
            overworld.getMapStorage().setData(NAME, clock);
        }
        current = clock;
    }

    public static void detach() {
        current = null;
    }

    public static void set(long value) {
        current.progress = Math.max(0, value);
        current.markDirty();
    }

    /** A dimension's saved engine clock, or -1 if none. */
    public static long engineClock(int dimension) {
        Long clock = current == null ? null : current.engine.get(dimension);
        return clock == null ? -1 : clock;
    }

    public static void setEngineClock(int dimension, long value) {
        if (current == null) return;
        current.engine.put(dimension, value);
        current.markDirty();
    }

    public static void setPaused(boolean value) {
        current.paused = value;
        current.markDirty();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || current == null) return;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        current.advance(server.getWorld(0).getWorldTime(), server.getCurrentPlayerCount() > 0);
        timeCommand = false;
    }

    private void advance(long worldTime, boolean playersOnline) {
        long previous = lastWorldTime;
        lastWorldTime = worldTime;
        if (paused || !playersOnline && !SolarConfig.progressWhileEmpty) return;
        long delta;
        if (SolarConfig.clockMode == SolarConfig.ClockMode.SUN) {
            delta = previous == Long.MIN_VALUE ? 0 : sunDelta(previous, worldTime);
            if (delta == 0) return;
        } else {
            tickRemainder += Timeline.DAY;
            delta = tickRemainder / SolarConfig.ticksPerDay;
            tickRemainder %= SolarConfig.ticksPerDay;
            if (delta == 0) return;
        }
        long from = progress;
        progress += delta;
        markDirty();
        if (delta >= MIN_SKIP) SolarApocalypse.skipped(from, progress, timeCommand);
    }

    /** A forward step this large in one tick is a skip (sleeping, /time), not the sun moving. */
    private static final long MIN_SKIP = 100;
    /** Whether a /time command ran this tick: its skip may catch up faster (performance.skip*). */
    private static boolean timeCommand;

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getCommand().getName().equals("time")) timeCommand = true;
    }

    /** Setting the time of day back by less than this counts nothing (a day-length mod could step the time back a little). */
    static final long MIN_SET_BACK = 100;

    /**
     * Progress for a step of worldTime: forward as is (/time add of any size counts in full); a step back (/time set day)
     * counts as the skip forward to that time of day, unless it sets the time of day back by less than MIN_SET_BACK.
     */
    static long sunDelta(long previous, long now) {
        long delta = now - previous;
        if (delta < 0) {
            long forward = Math.floorMod(delta, Timeline.DAY);
            delta = forward > Timeline.DAY - MIN_SET_BACK ? 0 : forward;
        }
        return delta;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        progress = nbt.getLong("progress");
        paused = nbt.getBoolean("paused");
        NBTTagCompound engines = nbt.getCompoundTag("engine");
        for (String key : engines.getKeySet()) engine.put(Integer.valueOf(key), engines.getLong(key));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("progress", progress);
        nbt.setBoolean("paused", paused);
        NBTTagCompound engines = new NBTTagCompound();
        for (Map.Entry<Integer, Long> e : engine.entrySet()) engines.setLong(String.valueOf(e.getKey()), e.getValue());
        nbt.setTag("engine", engines);
        return nbt;
    }
}
