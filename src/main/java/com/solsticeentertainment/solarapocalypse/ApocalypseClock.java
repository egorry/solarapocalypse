package com.solsticeentertainment.solarapocalypse;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The apocalypse's own clock: progress units (24000 per day) saved in the overworld's data/solarapocalypse.dat. Advanced
 * at the end of every server tick, from the overworld's worldTime (SUN) or from ticks (TICKS); /time set cannot rewind it.
 */
public final class ApocalypseClock extends WorldSavedData {

    private static final String NAME = Tags.MOD_ID;

    private static ApocalypseClock current;

    private long progress;
    private boolean paused;
    private long lastWorldTime = Long.MIN_VALUE;
    private long tickRemainder;

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

    public static void setPaused(boolean value) {
        current.paused = value;
        current.markDirty();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || current == null) return;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        current.advance(server.getWorld(0).getWorldTime(), server.getCurrentPlayerCount() > 0);
    }

    private void advance(long worldTime, boolean playersOnline) {
        long previous = lastWorldTime;
        lastWorldTime = worldTime;
        if (paused || !playersOnline && !SolarConfig.progressWhileEmpty) return;
        long delta;
        if (SolarConfig.clockMode == SolarConfig.ClockMode.SUN) {
            delta = previous == Long.MIN_VALUE ? 0 : worldTime - previous;
            if (delta <= 0 || delta > SolarConfig.maxSunJump) return;
        } else {
            tickRemainder += Timeline.DAY;
            delta = tickRemainder / SolarConfig.ticksPerDay;
            tickRemainder %= SolarConfig.ticksPerDay;
            if (delta == 0) return;
        }
        progress += delta;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        progress = nbt.getLong("progress");
        paused = nbt.getBoolean("paused");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("progress", progress);
        nbt.setBoolean("paused", paused);
        return nbt;
    }
}
