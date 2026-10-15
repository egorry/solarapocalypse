package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.client.SplashOverlay;
import com.solsticeentertainment.solarapocalypse.client.Stinger;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Phase starts: one log line, and from the phase's config a chat message, a sound and a splash title for every player;
 * of several phases crossed at once (a skip, /solar phase) only the last is announced (each is logged); a player who
 * joins later gets the running phase's. Also the mod's network channel: solar fire's light and the sun's size for clients.
 */
public final class Announcer {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);

    private Announcer() {}

    public static void register() {
        CHANNEL.registerMessage(SplashHandler.class, Splash.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(FireLight.class, FireLight.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(SunSize.class, SunSize.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(PhaseSoundHandler.class, PhaseSound.class, 3, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(Announcer.class);
    }

    /** Phases still to announce to everyone, each once the previous splash is over. */
    private static final Deque<Integer> QUEUE = new ArrayDeque<>();
    private static int wait;
    /** Players who just joined, ticks until they get the running phase's announcement if they missed it. */
    private static final Map<UUID, Integer> JOINED = new HashMap<>();
    private static final String SEEN = Tags.MOD_ID + ":announcedPhase";

    /** Phases first..last (0-based) have just started, in order: all logged, only the last announced (the user, turn 27). */
    public static void phasesStarted(int first, int last, long progress) {
        Timeline timeline = SolarApocalypse.timeline();
        for (int phase = first; phase <= last; phase++) {
            SolarConfig.Phase p = SolarConfig.phases[phase];
            long day = phase == last ? progress : timeline.start(phase);
            SolarApocalypse.LOGGER.info("Solar apocalypse phase {} begins on day {}{}", phase + 1,
                    String.format("%.2f", day / (double) Timeline.DAY), p.message.isEmpty() ? "" : ": " + p.message);
        }
        QUEUE.clear(); // one not shown yet is passed already
        QUEUE.add(last);
    }

    /** Every server tick: the next queued phase announcement, those owed to players who joined, and the sun's size. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCounter() % 20 == 0) sendSunSizes(server);
        if (wait > 0) wait--;
        else if (!QUEUE.isEmpty()) {
            int phase = QUEUE.poll();
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) announce(player, phase);
            wait = SolarConfig.splashFadeInTicks + SolarConfig.splashStayTicks + SolarConfig.splashFadeOutTicks;
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = JOINED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            if (e.getValue() > 0) {
                e.setValue(e.getValue() - 1);
                continue;
            }
            it.remove();
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(e.getKey());
            int phase = SolarApocalypse.timeline().phaseAt(ApocalypseClock.progress());
            if (player != null && phase >= 0 && seen(player).getInteger(SEEN) < phase + 1) announce(player, phase);
            String warning = SolarApocalypse.hookWarning();
            if (player != null && warning != null && (player.getName().equals(server.getServerOwner())
                    || server.getPlayerList().canSendCommands(player.getGameProfile()))) { // the host or an operator
                player.sendMessage(new TextComponentString("\u00a76[Solar Apocalypse]\u00a7r " + warning));
            }
        }
    }

    /** A player who missed the running phase's announcement (offline when it started) gets it shortly after joining. */
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        JOINED.put(event.player.getUniqueID(), 60); // once the client shows the world
        SUN_SENT.remove(event.player.getUniqueID()); // a new client: the size goes again
        CHANNEL.sendTo(new FireLight(), (EntityPlayerMP) event.player);
    }

    /** The player's persisted data, which survives death; holds the last phase (1-based) announced to them. */
    private static NBTTagCompound seen(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    /** Sends a phase's message, sound and splash to one player (also /solar announce). */
    public static void announce(EntityPlayerMP player, int phase) {
        NBTTagCompound seen = seen(player);
        seen.setInteger(SEEN, Math.max(seen.getInteger(SEEN), phase + 1));
        SolarConfig.Phase p = SolarConfig.phases[phase];
        if (!p.message.isEmpty()) player.sendMessage(new TextComponentString(format(p.message)));
        if (!p.sound.isEmpty() && SolarConfig.soundAsMusic) CHANNEL.sendTo(new PhaseSound(p.sound), player); // the client plays it as music
        else if (!p.sound.isEmpty()) player.connection.sendPacket(new SPacketCustomSound(p.sound, SoundCategory.MASTER, player.posX, player.posY, player.posZ, 1, 1));
        if (!p.splash.isEmpty()) CHANNEL.sendTo(new Splash(format(p.splash), format(p.message)), player);
    }

    /** Solar fire's light level to every player (after /solar reload). */
    public static void sendFireLight() {
        CHANNEL.sendToAll(new FireLight());
    }

    /** Per player: the sun's size last sent and the dimension it was for. */
    private static final Map<UUID, float[]> SUN_SENT = new HashMap<>();

    /** Once a second: the sun's size (sun.grow) to each player for whom it changed visibly, and once to each who joined. */
    private static void sendSunSizes(MinecraftServer server) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            float size = sunSize(player.world);
            float[] last = SUN_SENT.get(player.getUniqueID());
            boolean moved = last == null || last[1] != player.dimension;
            if (!moved && Math.abs(last[0] - size) < 0.001F) continue;
            SUN_SENT.put(player.getUniqueID(), new float[]{size, player.dimension});
            CHANNEL.sendTo(new SunSize(size, moved), player);
        }
    }

    /** The sun's size players see in a world (1 = vanilla): grown over the phases with sun.grow in the apocalypse's dimensions. */
    public static float sunSize(World world) {
        long progress = ApocalypseClock.progress();
        if (!SolarConfig.sunGrow || progress < 0 || !SolarApocalypse.isActive(world)) return 1;
        return (float) (1 + (SolarConfig.sunMaxSize - 1) * SolarApocalypse.timeline().sunGrowth(progress));
    }

    /** & formatting codes to Minecraft's section-sign codes. */
    static String format(String text) {
        return text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    /** The splash a client shows: texts from the phase, looks from the server's splash section. */
    public static final class Splash implements IMessage {
        public String title, message;
        public int titleColor, flickerColor, messageColor, fadeIn, stay, fadeOut;

        public Splash() {}

        Splash(String title, String message) {
            this.title = title;
            this.message = message;
            titleColor = SolarConfig.splashTitleColor;
            flickerColor = SolarConfig.splashFlickerColor;
            messageColor = SolarConfig.splashMessageColor;
            fadeIn = SolarConfig.splashFadeInTicks;
            stay = SolarConfig.splashStayTicks;
            fadeOut = SolarConfig.splashFadeOutTicks;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, title);
            ByteBufUtils.writeUTF8String(buf, message);
            for (int i : new int[]{titleColor, flickerColor, messageColor, fadeIn, stay, fadeOut}) buf.writeInt(i);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            title = ByteBufUtils.readUTF8String(buf);
            message = ByteBufUtils.readUTF8String(buf);
            titleColor = buf.readInt();
            flickerColor = buf.readInt();
            messageColor = buf.readInt();
            fadeIn = buf.readInt();
            stay = buf.readInt();
            fadeOut = buf.readInt();
        }
    }

    /** Client side only: registered for Side.CLIENT, so a dedicated server never runs it. */
    public static final class SplashHandler implements IMessageHandler<Splash, IMessage> {
        @Override
        public IMessage onMessage(Splash splash, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> SplashOverlay.show(splash));
            return null;
        }
    }

    /** A phase sound to play as music (splash.soundAsMusic; client.Stinger). */
    public static final class PhaseSound implements IMessage {
        public String sound;

        public PhaseSound() {}

        PhaseSound(String sound) {
            this.sound = sound;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, sound);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            sound = ByteBufUtils.readUTF8String(buf);
        }
    }

    /** Client side only, like SplashHandler. */
    public static final class PhaseSoundHandler implements IMessageHandler<PhaseSound, IMessage> {
        @Override
        public IMessage onMessage(PhaseSound message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> Stinger.play(message.sound));
            return null;
        }
    }

    /** The sun's size for the player's world (sun.grow; client.SkyHook draws it); snap: show it at once (joined, changed world). */
    public static final class SunSize implements IMessage, IMessageHandler<SunSize, IMessage> {
        /** Client: the size last received, and whether to show it at once rather than grow to it. */
        public static volatile float size = 1;
        public static volatile boolean snap = true;
        private float value;
        private boolean jump;

        public SunSize() {}

        SunSize(float value, boolean jump) {
            this.value = value;
            this.jump = jump;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeFloat(value);
            buf.writeBoolean(jump);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            value = buf.readFloat();
            jump = buf.readBoolean();
        }

        @Override
        public IMessage onMessage(SunSize message, MessageContext ctx) {
            size = message.value;
            if (message.jump) snap = true;
            return null;
        }
    }

    /** The server's solar fire light level: clients light the world themselves too, and must light it alike. */
    public static final class FireLight implements IMessage, IMessageHandler<FireLight, IMessage> {
        private int light = SolarFire.light;

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(light);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            light = buf.readByte();
        }

        @Override
        public IMessage onMessage(FireLight message, MessageContext ctx) {
            SolarFire.light = message.light;
            return null;
        }
    }
}
