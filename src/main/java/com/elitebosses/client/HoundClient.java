package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.elitebosses.net.HoundEscapePacket;
import com.elitebosses.net.ModNetwork;
import com.elitebosses.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cliente del Sabueso: musica (HoundLurk (120) / HoundChase (70) y musica del Stalker, ordenada
 * por el servidor) y deteccion de ESPACIO cuando te tiene agarrado.
 */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID, value = Dist.CLIENT)
public final class HoundClient {
    private static int musicMode = 0;
    private static boolean grabbed = false;
    private static boolean jumpWasDown = false;

    private static HoundLoop loop;
    private static int loopMode = 0;
    private static int ticksSinceStart = 0;

    private HoundClient() {
    }

    public static void setMusicMode(int mode) {
        musicMode = mode;
    }

    public static void setGrabbed(boolean value) {
        grabbed = value;
        jumpWasDown = true; // no cuenta una pulsacion que ya estaba mantenida
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        // --- ESPACIO para zafarse del agarre ---
        if (grabbed) {
            boolean down = mc.options.keyJump.isDown();
            if (down && !jumpWasDown) {
                ModNetwork.CHANNEL.sendToServer(new HoundEscapePacket());
            }
            jumpWasDown = down;
        }

        // --- musica ---
        if (musicMode == 0) {
            stopLoop();
            return;
        }
        ticksSinceStart++;
        boolean changed = loop == null || loopMode != musicMode;
        boolean died = loop != null && ticksSinceStart > 40 && !mc.getSoundManager().isActive(loop);
        if (changed || died) {
            stopLoop();
            SoundEvent sound = switch (musicMode) {
                case 2 -> ModSounds.HOUNDCHASE.get();
                case 3 -> ModSounds.STALKERSTALK1.get();
                case 4 -> ModSounds.STALKERSTALK2.get();
                case 5 -> ModSounds.STALKERCHASE.get();
                case 6 -> ModSounds.JUDGECHASE.get();
                default -> ModSounds.HOUNDLURK.get();
            };
            loop = new HoundLoop(sound);
            loopMode = musicMode;
            ticksSinceStart = 0;
            mc.getSoundManager().play(loop);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        musicMode = 0;
        grabbed = false;
        stopLoop();
    }

    private static void stopLoop() {
        if (loop != null) {
            loop.halt();
            Minecraft.getInstance().getSoundManager().stop(loop);
            loop = null;
        }
        loopMode = 0;
        ticksSinceStart = 0;
    }

    private static final class HoundLoop extends AbstractSoundInstance implements TickableSoundInstance {
        private boolean stopped = false;

        HoundLoop(SoundEvent sound) {
            super(sound, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void halt() {
            this.stopped = true;
            this.looping = false;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void tick() {
        }
    }
}
