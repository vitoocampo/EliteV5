package com.elitebosses.events;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.JudgeBeamEntity;
import com.elitebosses.entity.JudgeEntity;
import com.elitebosses.net.JudgeHudPacket;
import com.elitebosses.net.ModNetwork;
import com.elitebosses.registry.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La condena del Juicio, paso a paso (todo en el servidor):
 *  0 CONTANDO : cada entidad que matas suma (cualquier mob, de cualquier mod). Dormir o morir lo reinicia.
 *               30 -> contador rojo 15 s | 50 -> JudgeWarn1 + 30 s | 100 -> JudgeWarn2 + 2 min | 150 -> condena.
 *  1 MARCADO  : "Tus dias estan contados". La proxima vez que te acuestes suena JudgeStalk y no puedes
 *               levantarte durante 10 s.
 *  2 CAZADO   : la proxima vez que te alejes 250 bloques (en X/Z) de tu punto de reaparicion: Lentitud V 1 min,
 *               JudgeSpawn y un rayo de luz que te sigue. Al terminar el minuto aparece el Juicio.
 *  3 JUICIO   : el Juicio esta en el mundo. Si lo matas, todo vuelve a 0. Si te mata, tambien (sentencia cumplida).
 * Los datos van en getPersistentData() del jugador y se copian al reaparecer.
 */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID)
public final class JudgmentEvents {

    // ------------------------------------------------------------------ AJUSTES
    public static final int WARN_FIRST = 30, WARN_SECOND = 50, WARN_THIRD = 100, KILLS_TO_DOOM = 150;
    private static final int SHOW_FIRST = 15 * 20, SHOW_SECOND = 30 * 20, SHOW_THIRD = 120 * 20, SHOW_DOOM = 5 * 20;
    private static final int BED_LOCK_TICKS = 10 * 20;
    public static final double HUNT_DISTANCE = 250.0D;
    private static final int MANIFEST_TICKS = 60 * 20;  // 1 minuto de Lentitud V con el rayo encima
    private static final double BEAM_AHEAD = 6.0D;

    public static final int STAGE_COUNTING = 0, STAGE_MARKED = 1, STAGE_HUNTED = 2, STAGE_JUDGMENT = 3;

    private static final String TAG = "elitebosses_judgment";
    private static final String K_KILLS = "Kills", K_STAGE = "Stage";
    public static final String BURN_KEY = "elitebosses_holy_burn";

    /** Rayo de luz activo por jugador. */
    private static final Map<UUID, Manifest> MANIFESTS = new HashMap<>();
    /** Jugadores que no pueden levantarse de la cama (ticks restantes + cama). */
    private static final Map<UUID, BedLock> BED_LOCKS = new HashMap<>();
    /** Ultimo tick del servidor en que un Juicio vivo persiguio a este jugador. */
    private static final Map<UUID, Integer> JUDGE_SEEN = new HashMap<>();

    private record BedLock(int ticks, BlockPos bed) {
    }

    private static final class Manifest {
        int ticks;
        UUID beamId;
    }

    private JudgmentEvents() {
    }

    // ================================================================== datos del jugador
    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) {
            root.put(TAG, new CompoundTag());
        }
        return root.getCompound(TAG);
    }

    public static int getKills(Player p) {
        return data(p).getInt(K_KILLS);
    }

    public static int getStage(Player p) {
        return data(p).getInt(K_STAGE);
    }

    private static void setKills(Player p, int v) {
        data(p).putInt(K_KILLS, v);
    }

    private static void setStage(Player p, int v) {
        data(p).putInt(K_STAGE, v);
    }

    private static void send(ServerPlayer p, int type, int a, int b) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new JudgeHudPacket(type, a, b));
    }

    /** Reinicia todo (contador y condena). */
    public static void resetAll(ServerPlayer p) {
        setKills(p, 0);
        setStage(p, STAGE_COUNTING);
        cancelManifest(p, false);
        BED_LOCKS.remove(p.getUUID());
        send(p, JudgeHudPacket.CLEAR, 0, 0);
    }

    // ================================================================== 1) contar muertes
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (victim instanceof ServerPlayer dead) {
            onPlayerDeath(dead);
        }
        if (victim instanceof ArmorStand || victim instanceof JudgeEntity) {
            return;
        }
        ServerPlayer killer = null;
        Entity src = event.getSource().getEntity();
        if (src instanceof ServerPlayer sp) {
            killer = sp;
        } else if (victim.getKillCredit() instanceof ServerPlayer sp) {
            killer = sp; // trampas, caidas, lava... despues de que el jugador lo golpeara
        }
        if (killer == null || killer == victim || !countsFor(killer)) {
            return;
        }
        addKill(killer);
    }

    private static boolean countsFor(ServerPlayer p) {
        GameType gm = p.gameMode.getGameModeForPlayer();
        return gm == GameType.SURVIVAL || gm == GameType.ADVENTURE;
    }

    public static void addKill(ServerPlayer p) {
        if (getStage(p) != STAGE_COUNTING) {
            return;
        }
        int kills = getKills(p) + 1;
        setKills(p, kills);
        if (kills >= KILLS_TO_DOOM) {
            setStage(p, STAGE_MARKED);
            send(p, JudgeHudPacket.COUNTER, kills, -1);
            send(p, JudgeHudPacket.DOOM, kills, SHOW_DOOM);
        } else if (kills == WARN_THIRD) {
            send(p, JudgeHudPacket.SOUND, 2, 0);
            send(p, JudgeHudPacket.COUNTER, kills, SHOW_THIRD);
        } else if (kills == WARN_SECOND) {
            send(p, JudgeHudPacket.SOUND, 1, 0);
            send(p, JudgeHudPacket.COUNTER, kills, SHOW_SECOND);
        } else if (kills == WARN_FIRST) {
            send(p, JudgeHudPacket.COUNTER, kills, SHOW_FIRST);
        } else if (kills > WARN_FIRST) {
            send(p, JudgeHudPacket.COUNTER, kills, 0); // si esta visible, se actualiza
        }
    }

    private static void onPlayerDeath(ServerPlayer p) {
        p.getPersistentData().remove(BURN_KEY);
        int stage = getStage(p);
        if (stage == STAGE_COUNTING) {
            if (getKills(p) > 0) {
                setKills(p, 0);
                send(p, JudgeHudPacket.COUNTER, 0, -1);
            }
        } else if (stage == STAGE_JUDGMENT) {
            if (MANIFESTS.containsKey(p.getUUID())) {
                cancelManifest(p, false);
                setStage(p, STAGE_HUNTED); // morir bajo el rayo no te salva: volvera
            } else {
                // el Juicio cumplio su sentencia
                for (JudgeEntity j : judgesFor(p.serverLevel(), p.getUUID())) {
                    j.onTargetDied(p.serverLevel());
                }
                setKills(p, 0);
                setStage(p, STAGE_COUNTING);
                JUDGE_SEEN.remove(p.getUUID());
            }
        }
        BED_LOCKS.remove(p.getUUID());
        send(p, JudgeHudPacket.CLEAR, 0, 0);
    }

    /** La condena sobrevive a la muerte (solo el contador de la etapa 0 se pierde). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(TAG)) {
            event.getEntity().getPersistentData().put(TAG, old.getCompound(TAG).copy());
        }
    }

    // ================================================================== 2) dormir, distancia, rayo
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) {
            return;
        }
        tickBedLock(p);
        tickBurn(p);
        int stage = getStage(p);
        if (stage == STAGE_COUNTING) {
            if (p.isSleeping() && getKills(p) > 0) {
                setKills(p, 0); // dormir reinicia el contador
                send(p, JudgeHudPacket.COUNTER, 0, -1);
            }
        } else if (stage == STAGE_MARKED) {
            if (p.isSleeping() && p.getSleepingPos().isPresent()) {
                setStage(p, STAGE_HUNTED);
                BED_LOCKS.put(p.getUUID(), new BedLock(BED_LOCK_TICKS, p.getSleepingPos().get()));
                send(p, JudgeHudPacket.SOUND, 3, 0);
                send(p, JudgeHudPacket.BED_LOCK, BED_LOCK_TICKS, 0);
            }
        } else if (stage == STAGE_HUNTED) {
            if (p.tickCount % 20 == 0 && JudgeEntity.isValid(p) && farFromSpawn(p)) {
                startManifest(p);
            }
        } else if (stage == STAGE_JUDGMENT) {
            Manifest m = MANIFESTS.get(p.getUUID());
            if (m != null) {
                tickManifest(p, m);
            } else if (p.tickCount % 20 == 0) {
                // si no hay rayo ni Juicio persiguiendolo desde hace 10 s, la caceria vuelve a empezar
                Integer seen = JUDGE_SEEN.get(p.getUUID());
                int now = p.server.getTickCount();
                if (seen == null || now - seen > 200) {
                    if (judgesFor(p.serverLevel(), p.getUUID()).isEmpty()) {
                        setStage(p, STAGE_HUNTED);
                        JUDGE_SEEN.remove(p.getUUID());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- cama
    private static void tickBedLock(ServerPlayer p) {
        BedLock lock = BED_LOCKS.get(p.getUUID());
        if (lock == null) {
            return;
        }
        int left = lock.ticks() - 1;
        if (left <= 0 || !p.isAlive()) {
            BED_LOCKS.remove(p.getUUID());
            send(p, JudgeHudPacket.BED_LOCK, 0, 0);
            return;
        }
        BED_LOCKS.put(p.getUUID(), new BedLock(left, lock.bed()));
        if (!p.isSleeping() && p.level().getBlockState(lock.bed()).isBed(p.level(), lock.bed(), p)) {
            p.startSleeping(lock.bed()); // no te puedes levantar: vuelves a la cama
        }
    }

    /** Mientras esta bloqueado, el dia no lo despierta. */
    @SubscribeEvent
    public static void onSleepTimeCheck(SleepingTimeCheckEvent event) {
        if (BED_LOCKS.containsKey(event.getEntity().getUUID())) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    // ---------------------------------------------------------- 250 bloques
    private static boolean farFromSpawn(ServerPlayer p) {
        ResourceKey<Level> dim = p.getRespawnDimension();
        BlockPos spawn = p.getRespawnPosition();
        if (spawn == null) {
            dim = Level.OVERWORLD;
            ServerLevel ow = p.server.getLevel(Level.OVERWORLD);
            if (ow == null) {
                return false;
            }
            spawn = ow.getSharedSpawnPos();
        }
        if (!p.level().dimension().equals(dim)) {
            return false;
        }
        double dx = p.getX() - (spawn.getX() + 0.5D);
        double dz = p.getZ() - (spawn.getZ() + 0.5D);
        return dx * dx + dz * dz >= HUNT_DISTANCE * HUNT_DISTANCE;
    }

    public static void startManifest(ServerPlayer p) {
        cancelManifest(p, false);
        setStage(p, STAGE_JUDGMENT);
        Manifest m = new Manifest();
        m.ticks = MANIFEST_TICKS;
        Vec3 at = beamSpot(p);
        JudgeBeamEntity beam = JudgeBeamEntity.spawn(p.serverLevel(), at.x, at.y, at.z, JudgeBeamEntity.MODE_FOLLOW, 0);
        m.beamId = beam.getUUID();
        MANIFESTS.put(p.getUUID(), m);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MANIFEST_TICKS, 4, false, true, true));
        send(p, JudgeHudPacket.SOUND, 4, 0);
        markJudgeActive(p);
    }

    private static Vec3 beamSpot(ServerPlayer p) {
        Vec3 look = Vec3.directionFromRotation(0.0F, p.getYRot());
        double x = p.getX() + look.x * BEAM_AHEAD;
        double z = p.getZ() + look.z * BEAM_AHEAD;
        BlockPos ground = JudgeEntity.findSurface(p.serverLevel(), x, z);
        double y = ground != null && Math.abs(ground.getY() - p.getY()) < 8.0D ? ground.getY() : p.getY();
        return new Vec3(x, y, z);
    }

    private static void tickManifest(ServerPlayer p, Manifest m) {
        ServerLevel level = p.serverLevel();
        Entity e = m.beamId == null ? null : level.getEntity(m.beamId);
        Vec3 at = beamSpot(p);
        if (e == null) {
            JudgeBeamEntity beam = JudgeBeamEntity.spawn(level, at.x, at.y, at.z, JudgeBeamEntity.MODE_FOLLOW, 0);
            m.beamId = beam.getUUID();
        } else {
            // se desliza hacia el frente del jugador (suave, no salta)
            Vec3 cur = e.position();
            Vec3 next = cur.add(at.subtract(cur).scale(0.35D));
            e.setPos(next.x, next.y, next.z);
        }
        markJudgeActive(p);
        if (--m.ticks <= 0) {
            MANIFESTS.remove(p.getUUID());
            Vec3 spot = e != null ? e.position() : at;
            if (e != null) {
                e.discard();
            }
            spawnJudge(p, spot);
        }
    }

    /** Aparece el Juicio donde estaba el rayo. */
    public static JudgeEntity spawnJudge(ServerPlayer p, Vec3 spot) {
        ServerLevel level = p.serverLevel();
        JudgeEntity judge = ModEntities.JUDGE.get().create(level);
        if (judge == null) {
            return null;
        }
        BlockPos ground = JudgeEntity.findSurface(level, spot.x, spot.z);
        double y = ground != null && Math.abs(ground.getY() - spot.y) < 10.0D ? ground.getY() : spot.y;
        float yaw = (float) (Mth.atan2(p.getZ() - spot.z, p.getX() - spot.x) * (180.0D / Math.PI)) - 90.0F;
        judge.moveTo(spot.x, y, spot.z, yaw, 0.0F);
        judge.setYHeadRot(yaw);
        judge.setYBodyRot(yaw);
        judge.setSentenced(p);
        level.addFreshEntity(judge);
        JudgeBeamEntity.spawn(level, spot.x, y, spot.z, JudgeBeamEntity.MODE_DESCENT, 50);
        setStage(p, STAGE_JUDGMENT);
        markJudgeActive(p);
        return judge;
    }

    private static void cancelManifest(ServerPlayer p, boolean keepEffect) {
        Manifest m = MANIFESTS.remove(p.getUUID());
        if (m != null && m.beamId != null) {
            Entity e = p.serverLevel().getEntity(m.beamId);
            if (e != null) {
                e.discard();
            }
            if (!keepEffect) {
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                send(p, JudgeHudPacket.SOUND, 0, 0);
            }
        }
    }

    public static void markJudgeActive(ServerPlayer p) {
        JUDGE_SEEN.put(p.getUUID(), p.server.getTickCount());
        if (getStage(p) == STAGE_HUNTED) {
            setStage(p, STAGE_JUDGMENT); // un Juicio ya lo persigue (p. ej. tras recargar el mundo)
        }
    }

    private static List<JudgeEntity> judgesFor(ServerLevel level, UUID player) {
        List<JudgeEntity> out = new ArrayList<>();
        for (JudgeEntity j : level.getEntities(EntityTypeTest.forClass(JudgeEntity.class), Entity::isAlive)) {
            if (player.equals(j.getTargetId())) {
                out.add(j);
            }
        }
        return out;
    }

    /** El Juicio murio: la condena se levanta. */
    public static void onJudgeDefeated(MinecraftServer server, UUID playerId) {
        ServerPlayer p = server.getPlayerList().getPlayer(playerId);
        JUDGE_SEEN.remove(playerId);
        if (p != null) {
            resetAll(p);
        }
    }

    // ---------------------------------------------------------- quemadura sagrada
    public static void applyHolyBurn(ServerPlayer p) {
        p.getPersistentData().putBoolean(BURN_KEY, true);
        p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 100));
    }

    /** No se apaga sola: solo con agua (o lluvia). */
    private static void tickBurn(ServerPlayer p) {
        if (p.tickCount % 5 != 0 || !p.getPersistentData().getBoolean(BURN_KEY)) {
            return;
        }
        if (p.isInWaterRainOrBubble() || !p.isAlive()) {
            p.getPersistentData().remove(BURN_KEY);
            p.clearFire();
            return;
        }
        if (p.getRemainingFireTicks() < 40) {
            p.setRemainingFireTicks(100);
        }
    }

    // ---------------------------------------------------------- conexion
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && getStage(p) == STAGE_JUDGMENT) {
            setStage(p, STAGE_HUNTED); // el rayo/Juicio no espera: volvera cuando te alejes de nuevo
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            cancelManifest(p, true);
            BED_LOCKS.remove(p.getUUID());
            JUDGE_SEEN.remove(p.getUUID());
            if (getStage(p) == STAGE_JUDGMENT) {
                setStage(p, STAGE_HUNTED);
            }
        }
    }

    // ================================================================== comando de pruebas
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("juicio").requires(s -> s.hasPermission(2));
        root.then(Commands.literal("estado").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            String[] names = {"contando", "marcado (duerme)", "cazado (alejate 250)", "juicio en curso"};
            int st = Mth.clamp(getStage(p), 0, 3);
            ctx.getSource().sendSuccess(() -> Component.literal("Juicio: muertes=" + getKills(p) + " | etapa=" + names[st])
                    .withStyle(ChatFormatting.GOLD), false);
            return 1;
        }));
        root.then(Commands.literal("contador").then(Commands.argument("n", IntegerArgumentType.integer(0, KILLS_TO_DOOM - 1))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    resetAll(p);
                    setKills(p, IntegerArgumentType.getInteger(ctx, "n"));
                    send(p, JudgeHudPacket.COUNTER, getKills(p), SHOW_FIRST);
                    return 1;
                })));
        root.then(Commands.literal("marcar").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            resetAll(p);
            setKills(p, KILLS_TO_DOOM - 1);
            addKill(p);
            return 1;
        }));
        root.then(Commands.literal("cazar").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            resetAll(p);
            setKills(p, KILLS_TO_DOOM);
            setStage(p, STAGE_HUNTED);
            ctx.getSource().sendSuccess(() -> Component.literal("Ahora alejate " + (int) HUNT_DISTANCE
                    + " bloques de tu punto de reaparicion.").withStyle(ChatFormatting.GOLD), false);
            return 1;
        }));
        root.then(Commands.literal("invocar").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            setKills(p, KILLS_TO_DOOM);
            startManifest(p);
            return 1;
        }));
        root.then(Commands.literal("aparecer").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            cancelManifest(p, false);
            setKills(p, KILLS_TO_DOOM);
            Vec3 look = Vec3.directionFromRotation(0.0F, p.getYRot());
            spawnJudge(p, new Vec3(p.getX() + look.x * 8.0D, p.getY(), p.getZ() + look.z * 8.0D));
            return 1;
        }));
        root.then(Commands.literal("reset").executes(ctx -> {
            resetAll(ctx.getSource().getPlayerOrException());
            ctx.getSource().sendSuccess(() -> Component.literal("Condena borrada.").withStyle(ChatFormatting.GOLD), false);
            return 1;
        }).then(Commands.argument("jugador", EntityArgument.player()).executes(ctx -> {
            resetAll(EntityArgument.getPlayer(ctx, "jugador"));
            return 1;
        })));
        d.register(root);
    }
}
