package com.elitebosses.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Optional;

/** Castigo por tomar leche con la velocidad del Chocolate: 10 s de ceguera, ruido y textos, y muerte. */
public final class ChocoPunishment {
    private static final String[] WORDS = {"TRAMPOSO", "MENTIROSO", "TRAMPOSO"};
    private static final int DURATION_TICKS = 200;
    private static final int STEP_TICKS = 10;

    private ChocoPunishment() {
    }

    public static void punish(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DURATION_TICKS + 20, 0, false, false, false));

        int steps = DURATION_TICKS / STEP_TICKS;
        for (int i = 0; i < steps; i++) {
            final int index = i;
            ChocoScheduler.runLater(i * STEP_TICKS, () -> step(player, index));
        }
        ChocoScheduler.runLater(DURATION_TICKS, () -> {
            if (!player.isRemoved() && player.isAlive()) {
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            }
        });
    }

    private static void step(ServerPlayer player, int index) {
        if (player.isRemoved() || !player.isAlive()) {
            return;
        }
        String word = WORDS[index % WORDS.length];

        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 14, 2));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal(word).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(gibberish(player.getRandom(), 14))
                        .withStyle(ChatFormatting.RED, ChatFormatting.OBFUSCATED)));

        if (index % 3 == 1) {
            player.sendSystemMessage(Component.literal("<Chocolate> " + word).withStyle(ChatFormatting.RED));
        }
        playRandomSound(player);
    }

    private static String gibberish(RandomSource random, int length) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&?";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static void playRandomSound(ServerPlayer player) {
        RandomSource random = player.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            Optional<Holder.Reference<SoundEvent>> pick = BuiltInRegistries.SOUND_EVENT.getRandom(random);
            if (pick.isEmpty()) {
                return;
            }
            Holder.Reference<SoundEvent> holder = pick.get();
            String path = holder.key().location().getPath();
            if (path.startsWith("music") || path.contains("record") || path.contains("ambient")) {
                continue;
            }
            player.connection.send(new ClientboundSoundEntityPacket(holder, SoundSource.MASTER, player,
                    1.0F, 0.6F + random.nextFloat() * 0.9F, random.nextLong()));
            return;
        }
    }
}
