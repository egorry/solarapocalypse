package com.solsticeentertainment.solarapocalypse;

import com.solsticeentertainment.solarapocalypse.client.SplashOverlay;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Phase starts: one log line, and from the phase's config a chat message, a sound and a splash title for every player. */
public final class Announcer {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);

    private Announcer() {}

    public static void register() {
        CHANNEL.registerMessage(SplashHandler.class, Splash.class, 0, Side.CLIENT);
    }

    /** A phase (0-based) has just started. */
    public static void phaseStarted(MinecraftServer server, int phase, long progress) {
        SolarConfig.Phase p = SolarConfig.phases[phase];
        SolarApocalypse.LOGGER.info("Solar apocalypse phase {} begins on day {}{}", phase + 1,
                String.format("%.2f", progress / (double) Timeline.DAY), p.message.isEmpty() ? "" : ": " + p.message);
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) announce(player, phase);
    }

    /** Sends a phase's message, sound and splash to one player (also /solar announce). */
    public static void announce(EntityPlayerMP player, int phase) {
        SolarConfig.Phase p = SolarConfig.phases[phase];
        if (!p.message.isEmpty()) player.sendMessage(new TextComponentString(format(p.message)));
        if (!p.sound.isEmpty()) {
            player.connection.sendPacket(new SPacketCustomSound(p.sound, SoundCategory.MASTER, player.posX, player.posY, player.posZ, 1, 1));
        }
        if (!p.splash.isEmpty()) CHANNEL.sendTo(new Splash(format(p.splash), format(p.message)), player);
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
}
