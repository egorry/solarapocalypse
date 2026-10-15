package com.solsticeentertainment.solarapocalypse.client;

import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import paulscode.sound.SoundSystem;

import java.lang.reflect.Field;

/**
 * Client only. A phase sound played as music (splash.soundAsMusic; the user's stingers, turn 27): in the music category
 * (its volume slider: at 0 no stinger is heard), heard alike anywhere, and always streamed, so it plays on one of the
 * sound system's 4 streaming channels, which block sounds (many fires crackling) never take. Vanilla music playing fades
 * out over splash.musicFadeOutTicks (this client's config) and no vanilla music starts until the stinger has ended
 * (Minecraft then waits its usual pause before the next track). Jukeboxes (another category) play on.
 */
public final class Stinger {

    private static Track stinger;
    private static int age; // ticks since the stinger started
    /** The vanilla music started last, its sound system source, and the manager playing it (from Forge's events). */
    private static ISound music;
    private static String musicSource;
    private static SoundManager manager;
    private static int fade, fadeTicks; // ticks left of the music's fade out, of fadeTicks
    private static Field soundSystem;

    private Stinger() {}

    public static void play(String name) {
        SoundHandler sounds = Minecraft.getMinecraft().getSoundHandler();
        ResourceLocation id = new ResourceLocation(name);
        SoundEventAccessor accessor = sounds.getAccessor(id);
        if (accessor == null || accessor.getWeight() == 0) { // unknown, or its files missing: nothing to play, the music stays
            SolarApocalypse.LOGGER.warn("Phase sound {} is not a sound this client can play (no sounds.json entry, or its files are missing)", name);
            return;
        }
        if (stinger != null) sounds.stopSound(stinger);
        if (fade == 0 && music != null && sounds.isSoundPlaying(music)) {
            fade = fadeTicks = SolarConfig.musicFadeOutTicks;
            if (fade == 0) sounds.stopSound(music);
        }
        stinger = new Track(id); // always a new one: the sound manager keys playing sounds by instance
        age = 0;
        sounds.playSound(stinger);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || stinger == null && fade == 0 || mc.isGamePaused()) return;
        // a sound counts as playing only once the sound system has started it, within the first second
        if (stinger != null && ++age > 20 && !mc.getSoundHandler().isSoundPlaying(stinger)) stinger = null;
        if (fade == 0) return;
        float full = MathHelper.clamp(music.getVolume() * mc.gameSettings.getSoundLevel(SoundCategory.MUSIC), 0, 1);
        if (--fade == 0 || !volume(full * fade / fadeTicks)) {
            fade = 0;
            mc.getSoundHandler().stopSound(music);
            music = null;
        }
    }

    /** Sets the fading music's volume; false if it cannot (no access to the sound system: it stops at once instead). */
    private static boolean volume(float volume) {
        try {
            if (soundSystem == null) soundSystem = ReflectionHelper.findField(SoundManager.class, "sndSystem", "field_148620_e");
            ((SoundSystem) soundSystem.get(manager)).setVolume(musicSource, volume); // read each time: a resource reload replaces it
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            SolarApocalypse.LOGGER.debug("Vanilla music cannot fade, it stops at once", e);
            return false;
        }
    }

    /** No vanilla music starts while a stinger plays. */
    @SubscribeEvent
    public static void onPlay(PlaySoundEvent event) {
        ISound sound = event.getResultSound();
        if (stinger != null && sound != null && !(sound instanceof Track) && sound.getCategory() == SoundCategory.MUSIC) event.setResultSound(null);
    }

    /** Remembers the vanilla music started last and its source (streamed or not), to fade it when a stinger starts. */
    @SubscribeEvent
    public static void onSource(SoundEvent.SoundSourceEvent event) {
        if (event.getSound() instanceof Track || event.getSound().getCategory() != SoundCategory.MUSIC) return;
        music = event.getSound();
        musicSource = event.getUuid();
        manager = event.getManager();
    }

    /** A stinger: music, streamed whatever its sounds.json says, and kept on the listener (a mono file would pan otherwise). */
    private static final class Track extends PositionedSound implements ITickableSound {
        Track(ResourceLocation id) {
            super(id, SoundCategory.MUSIC);
            attenuationType = ISound.AttenuationType.NONE;
            update();
        }

        @Override
        public SoundEventAccessor createAccessor(SoundHandler handler) {
            SoundEventAccessor accessor = super.createAccessor(handler);
            if (sound != SoundHandler.MISSING_SOUND && !sound.isStreaming()) {
                sound = new Sound(sound.getSoundLocation().toString(), sound.getVolume(), sound.getPitch(), sound.getWeight(), sound.getType(), true);
            }
            return accessor;
        }

        // ponytail: the listener's position at the tick, not the frame; a mono stinger may pan a little while moving fast
        @Override
        public void update() {
            Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
            if (viewer == null) return;
            xPosF = (float) viewer.posX;
            yPosF = (float) (viewer.posY + viewer.getEyeHeight());
            zPosF = (float) viewer.posZ;
        }

        @Override
        public boolean isDonePlaying() {
            return false;
        }
    }
}
