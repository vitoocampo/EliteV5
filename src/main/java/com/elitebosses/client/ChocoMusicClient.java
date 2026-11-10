package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.ChocolateEntity;
import com.elitebosses.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Musica del Chocolate: suena mientras haya uno a 32 bloques o menos de ti,
 * con volumen fijo (no baja con la distancia). Fase 1 = chocochase1, fase 2 = chocochase2.
 * La fase 2 no se reinicia: mientras el mob siga en fase 2 la cancion continua.
 */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID, value = Dist.CLIENT)
public final class ChocoMusicClient {
    private static final double RANGE_SQR = ChocolateEntity.MUSIC_RANGE * ChocolateEntity.MUSIC_RANGE;

    private static ChocoLoop loop;
    private static int loopPhase = 0;
    private static int ticksSinceStart = 0;

    private ChocoMusicClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopLoop();
            return;
        }

        int wanted = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ChocolateEntity mob && mob.isAlive()
                    && mob.distanceToSqr(mc.player) <= RANGE_SQR) {
                wanted = Math.max(wanted, mob.getPhase());
            }
        }

        if (wanted == 0) {
            stopLoop();
            return;
        }

        ticksSinceStart++;
        boolean phaseChanged = loop == null || loopPhase != wanted;
        boolean died = loop != null && ticksSinceStart > 40 && !mc.getSoundManager().isActive(loop);
        if (phaseChanged || died) {
            stopLoop();
            SoundEvent sound = wanted >= 2 ? ModSounds.CHOCOCHASE2.get() : ModSounds.CHOCOCHASE1.get();
            loop = new ChocoLoop(sound);
            loopPhase = wanted;
            ticksSinceStart = 0;
            mc.getSoundManager().play(loop);
        }
    }

    private static void stopLoop() {
        if (loop != null) {
            loop.halt();
            Minecraft.getInstance().getSoundManager().stop(loop);
            loop = null;
        }
        loopPhase = 0;
        ticksSinceStart = 0;
    }

    private static final class ChocoLoop extends AbstractSoundInstance implements TickableSoundInstance {
        private boolean stopped = false;

        ChocoLoop(SoundEvent sound) {
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
            // Lo controla ChocoMusicClient
        }
    }
}
