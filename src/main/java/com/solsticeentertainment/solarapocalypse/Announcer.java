package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.client.SplashOverlay;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
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
 * several phases crossed at once are announced one after another; a player who joins later gets the running phase's.
 */
public final class Announcer {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);

    private Announcer() {}

    public static void register() {
        CHANNEL.registerMessage(SplashHandler.class, Splash.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(FireLight.class, FireLight.class, 1, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(Announcer.class);
    }

    /** Phases still to announce to everyone, one after another (a skip can cross several). */
    private static final Deque<Integer> QUEUE = new ArrayDeque<>();
    private static int wait;
    /** Players who just joined, ticks until they get the running phase's announcement if they missed it. */
    private static final Map<UUID, Integer> JOINED = new HashMap<>();
    private static final String SEEN = Tags.MOD_ID + ":announcedPhase";

    /** Phases first..last (0-based) have just started, in order; each is announced once the previous splash is over. */
    public static void phasesStarted(int first, int last, long progress) {
        Timeline timeline = SolarApocalypse.timeline();
        for (int phase = first; phase <= last; phase++) {
            SolarConfig.Phase p = SolarConfig.phases[phase];
            long day = phase == last ? progress : timeline.start(phase);
            SolarApocalypse.LOGGER.info("Solar apocalypse phase {} begins on day {}{}", phase + 1,
                    String.format("%.2f", day / (double) Timeline.DAY), p.message.isEmpty() ? "" : ": " + p.message);
            QUEUE.add(phase);
        }
    }

    /** Every server tick: the next queued phase announcement, and those owed to players who joined. */
    public static void tick(MinecraftServer server) {
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
        }
    }

    /** A player who missed the running phase's announcement (offline when it started) gets it shortly after joining. */
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        JOINED.put(event.player.getUniqueID(), 60); // once the client shows the world
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
        if (!p.sound.isEmpty()) {
            player.connection.sendPacket(new SPacketCustomSound(p.sound, SoundCategory.MASTER, player.posX, player.posY, player.posZ, 1, 1));
        }
        if (!p.splash.isEmpty()) CHANNEL.sendTo(new Splash(format(p.splash), format(p.message)), player);
    }

    /** Solar fire's light level to every player (after /solar reload). */
    public static void sendFireLight() {
        CHANNEL.sendToAll(new FireLight());
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
