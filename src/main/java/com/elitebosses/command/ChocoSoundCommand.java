package com.elitebosses.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.elitebosses.EliteBosses;
import com.elitebosses.registry.ModSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /chocosound chocochase1|chocochase2|chocospawn|stop [jugadores]
 * Tambien funciona el comando normal: /playsound elitebosses:chocospawn master @s
 */
public final class ChocoSoundCommand {
    private static final List<String> NAMES = List.of("chocochase1", "chocochase2", "chocospawn", "stop");

    private ChocoSoundCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chocosound")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nombre", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(NAMES, builder))
                        .executes(ctx -> run(ctx.getSource(),
                                StringArgumentType.getString(ctx, "nombre"),
                                List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .executes(ctx -> run(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "nombre"),
                                        EntityArgument.getPlayers(ctx, "jugadores"))))));
    }

    private static int run(CommandSourceStack source, String rawName, Collection<ServerPlayer> targets) {
        final String name = rawName.toLowerCase(Locale.ROOT);

        if (name.equals("stop")) {
            for (ServerPlayer player : targets) {
                for (String sound : List.of("chocochase1", "chocochase2", "chocospawn")) {
                    player.connection.send(new ClientboundStopSoundPacket(
                            new ResourceLocation(EliteBosses.MODID, sound), SoundSource.MASTER));
                }
            }
            source.sendSuccess(() -> Component.literal("Sonidos de Chocolate detenidos."), true);
            return targets.size();
        }

        RegistryObject<SoundEvent> sound = switch (name) {
            case "chocochase1" -> ModSounds.CHOCOCHASE1;
            case "chocochase2" -> ModSounds.CHOCOCHASE2;
            case "chocospawn" -> ModSounds.CHOCOSPAWN;
            default -> null;
        };
        if (sound == null) {
            source.sendFailure(Component.literal("Sonido desconocido. Usa: chocochase1, chocochase2, chocospawn o stop"));
            return 0;
        }

        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.get());
        for (ServerPlayer player : targets) {
            player.connection.send(new ClientboundSoundEntityPacket(holder, SoundSource.MASTER, player,
                    1.0F, 1.0F, player.getRandom().nextLong()));
        }
        source.sendSuccess(() -> Component.literal("Reproduciendo " + name + " para " + targets.size() + " jugador(es)."), true);
        return targets.size();
    }
}
