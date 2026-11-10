package com.elitebosses.events;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.HoundmasterEntity;
import com.elitebosses.entity.JudgeEntity;
import com.elitebosses.entity.StalkerEntity;
import com.elitebosses.net.HoundMusicPacket;
import com.elitebosses.net.ModNetwork;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Musica del Sabueso, desaparicion al matar, spawn natural y comando. */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID)
public final class HoundEvents {
    private static final Map<UUID, Integer> LAST_MODE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();
    private static boolean spawnedThisStorm = false;

    private HoundEvents() {
    }

    // ---------------------------------------------------------------- musica
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 10 == 0) {
            updateMusic(server, tick);
        }
        if (tick % 100 == 0) {
            naturalSpawn(server);
        }
    }

    private static void updateMusic(MinecraftServer server, int tick) {
        for (ServerLevel level : server.getAllLevels()) {
            List<? extends HoundmasterEntity> hounds =
                    level.getEntities(EntityTypeTest.forClass(HoundmasterEntity.class), Entity::isAlive);
            List<? extends StalkerEntity> stalkers =
                    level.getEntities(EntityTypeTest.forClass(StalkerEntity.class), Entity::isAlive);
            List<? extends JudgeEntity> judges =
                    level.getEntities(EntityTypeTest.forClass(JudgeEntity.class), Entity::isAlive);
            for (ServerPlayer p : level.players()) {
                int mode = 0;
                if (!hounds.isEmpty()) {
                    double min = Double.MAX_VALUE;
                    for (HoundmasterEntity h : hounds) {
                        min = Math.min(min, h.distanceToSqr(p));
                    }
                    double rc = HoundmasterEntity.CHASE_MUSIC_RANGE;
                    double rl = HoundmasterEntity.LURK_MUSIC_RANGE;
                    mode = min <= rc * rc ? 2 : (min <= rl * rl ? 1 : 0); // 2 = HoundChase (<= 70), 1 = HoundLurk (<= 120)
                }
                for (StalkerEntity st : stalkers) {
                    mode = Math.max(mode, st.musicModeFor(p)); // 3, 4, 5: la musica del Stalker tiene prioridad
                }
                for (JudgeEntity j : judges) {
                    // 6 = JudgeChase en bucle para su victima (y quien este a 48 bloques) hasta que muera o desaparezca
                    if (p.getUUID().equals(j.getTargetId()) || j.distanceToSqr(p) <= 48.0D * 48.0D) {
                        mode = 6;
                    }
                }
                Integer prev = LAST_MODE.get(p.getUUID());
                Integer sentAt = LAST_SENT.get(p.getUUID());
                boolean refresh = sentAt == null || tick - sentAt > 400;
                if (prev == null || prev != mode || refresh) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HoundMusicPacket(mode));
                    LAST_MODE.put(p.getUUID(), mode);
                    LAST_SENT.put(p.getUUID(), tick);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MODE.remove(event.getEntity().getUUID());
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        LAST_MODE.remove(event.getEntity().getUUID());
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        LAST_MODE.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------- desaparece al matar
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity killer = event.getSource().getEntity();
        boolean byHound = killer instanceof HoundmasterEntity
                || (killer instanceof Wolf w && w.getPersistentData().hasUUID(HoundmasterEntity.WOLF_KEY));
        if (!byHound && player.getPersistentData().contains(HoundmasterEntity.HIT_KEY)) {
            long since = level.getGameTime() - player.getPersistentData().getLong(HoundmasterEntity.HIT_KEY);
            byHound = since >= 0 && since < 200; // muerte por caida/empuje justo despues de un ataque
        }
        if (!byHound) {
            return;
        }
        // cada Sabueso decide: sin testigos a 90 bloques desaparece (sin drops); con testigos duplica su velocidad
        List<HoundmasterEntity> hounds = new java.util.ArrayList<>(
                level.getEntities(EntityTypeTest.forClass(HoundmasterEntity.class), Entity::isAlive));
        for (HoundmasterEntity h : hounds) {
            h.onKilledPlayer(level, player);
        }
    }

    /** Lobo de la Canalizacion: Lentitud V durante 5 s por golpe. */
    @SubscribeEvent
    public static void onWolfHit(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Wolf w
                && w.getPersistentData().getBoolean(HoundmasterEntity.CANAL_KEY)
                && event.getEntity() instanceof ServerPlayer victim) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4));
        }
    }

    // ------------------------------------------------------ spawn natural
    private static void naturalSpawn(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        if (!overworld.isThundering()) {
            spawnedThisStorm = false;
            return;
        }
        long t = overworld.getDayTime() % 24000L;
        boolean night = t >= 13000L && t <= 23000L;
        if (spawnedThisStorm || !night || server.getPlayerCount() < 5) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.getEntities(EntityTypeTest.forClass(HoundmasterEntity.class), e -> true).isEmpty()) {
                spawnedThisStorm = true;
                return;
            }
        }
        List<ServerPlayer> candidates = overworld.players().stream().filter(HoundmasterEntity::isValid).toList();
        if (candidates.isEmpty()) {
            return;
        }
        ServerPlayer chosen = candidates.get(overworld.random.nextInt(candidates.size()));
        if (HoundSpawner.spawnFar(overworld, chosen, 150.0D) != null) {
            spawnedThisStorm = true;
        }
    }

    // ------------------------------------------------------------ comando
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("houndmaster")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    HoundmasterEntity h = HoundSpawner.spawnFar(player.serverLevel(), player, 300.0D);
                    if (h == null) {
                        ctx.getSource().sendFailure(Component.literal("No se pudo invocar al Sabueso."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "El Sabueso aparecio a 300 bloques de ti y viene por el jugador mas cercano."), true);
                    return 1;
                })
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> {
                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                            HoundmasterEntity h = HoundSpawner.spawnAt(ctx.getSource().getLevel(), pos);
                            if (h == null) {
                                ctx.getSource().sendFailure(Component.literal("No se pudo invocar al Sabueso."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("El Sabueso fue invocado."), true);
                            return 1;
                        })));
    }
}
