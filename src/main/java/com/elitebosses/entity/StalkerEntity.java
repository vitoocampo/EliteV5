package com.elitebosses.entity;

import com.elitebosses.net.HoundGrabPacket;
import com.elitebosses.net.ModNetwork;
import com.elitebosses.registry.ModEffects;
import com.elitebosses.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.UUID;

/**
 * El Stalker (Krasue). Etapa 1: pequeno, inmortal, acosa a un jugador 5 minutos (efecto Obsesion).
 * Etapa 2: jefe volador de 3 bloques con 500 corazones. Toda la logica corre en el servidor.
 */
public class StalkerEntity extends PathfinderMob implements GeoEntity {

    // ------------------------------------------------------------------ AJUSTES
    public static final float MAX_HP = 1000.0F;              // 500 corazones
    public static final int OBSESSION_TICKS = 6000;          // 5 minutos
    public static final double TERROR_RADIUS = 64.0D;        // radio donde se oyen sus temas
    private static final double BASE_SPEED = 0.282D;         // velocidad de un lobo (bloques/tick)
    private static final double STAGE1_SPEED = 0.6D;
    private static final int SPEED_STEP_TICKS = 2400;        // +1 de Velocidad cada 2 min (hasta Velocidad 4)
    private static final float WHIP_DAMAGE = 10.0F;
    private static final float ASCEND_DAMAGE = 15.0F;
    private static final int FINISH_TICKS = 160;             // 8 s
    private static final int RAPTO_TICKS = 300;              // 15 s
    private static final float RAPTO_LASH_DAMAGE = 3.0F;     // Rapto visceral: un latigazo por segundo, 3 PS cada uno
    private static final int PURGE_TICKS = 600;              // 30 s
    private static final int EXEC_TICKS = 200;               // 10 s
    private static final int BRINE_CAP = 200;

    private static final EntityDimensions DIM_STAGE1 = EntityDimensions.fixed(0.6F, 0.6F);
    private static final EntityDimensions DIM_STAGE2 = EntityDimensions.fixed(1.6F, 3.0F);

    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);

    /** Acto actual, sincronizado al cliente (Act.ordinal()) para elegir la animacion. */
    private static final EntityDataAccessor<Integer> DATA_ACT =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);

    private enum Act { HUNT, WHIP, ASCEND, RAPTO, PURGE, EXEC, DRAIN, FINISH }

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.elitebosses.stalker"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private Act act = Act.HUNT;
    private int actTicks;
    private int cooldown = 60;
    private UUID targetId;
    private int offlineTicks;
    private int obsTicks;
    private int stage2Ticks;
    private double angle;
    // ataques
    private AABB ascendBox;
    private int ascendBrine;
    private int cellX, cellY, cellZ;
    private boolean cellBuilt;
    private Vec3 raptoReturn;
    private int purgeCooldown;
    private float purgeStartHealth;
    private UUID drainTargetId;
    private int drainPresses, drainRequired;
    private Vec3 finishPos;

    public StalkerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setPersistenceRequired();
        this.bossEvent.setDarkenScreen(false);
        this.bossEvent.setPlayBossMusic(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STAGE, 1);
        this.entityData.define(DATA_ACT, Act.HUNT.ordinal());
    }

    public int getStage() {
        return this.entityData.get(DATA_STAGE);
    }

    private void setStage(int stage) {
        this.entityData.set(DATA_STAGE, stage);
        this.refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STAGE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return getStage() == 2 ? DIM_STAGE2 : DIM_STAGE1;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return dims.height * 0.85F;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getStage() == 1 && act != Act.FINISH && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false; // etapa 1: inmortal
        }
        return super.hurt(source, amount);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }

    // ------------------------------------------------------------------ animaciones (GeckoLib)
    private static final String ANIM_PREFIX = "animation.stalker.";
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Para el cliente: true mientras se desploma (el modelo deja de seguir con la cabeza). */
    public boolean isFinishing() {
        return this.entityData.get(DATA_ACT) == Act.FINISH.ordinal();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8, this::mainPredicate));
    }

    private PlayState mainPredicate(AnimationState<StalkerEntity> state) {
        int ordinal = this.entityData.get(DATA_ACT);
        Act current = ordinal >= 0 && ordinal < Act.values().length ? Act.values()[ordinal] : Act.HUNT;
        RawAnimation anim;
        switch (current) {
            case WHIP -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "whip");
            case ASCEND -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "ascend");
            case RAPTO -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "rapto");
            case PURGE -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "purge");
            case EXEC -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "exec");
            case DRAIN -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "drain");
            case FINISH -> anim = RawAnimation.begin().thenPlayAndHold(ANIM_PREFIX + "finish");
            default -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + (state.getLimbSwingAmount() > 0.04F || state.isMoving() ? "move" : "idle"));
        }
        return state.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }


    // ------------------------------------------------------------------ cerebro
    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive() && this.level() instanceof ServerLevel serverLevel) {
            this.noPhysics = act != Act.FINISH;
            this.setNoGravity(act != Act.FINISH);
            brain(serverLevel);
            if (this.entityData.get(DATA_ACT) != act.ordinal()) {
                this.entityData.set(DATA_ACT, act.ordinal());
            }
        }
        super.aiStep();
    }

    private void brain(ServerLevel level) {
        if (cellBuilt && act != Act.RAPTO) {
            clearCell(level); // restos de una celda (p. ej. tras recargar el mundo)
        }
        if (act == Act.FINISH) {
            tickFinish(level);
            return;
        }
        if (targetId == null) {
            halt();
            if (this.tickCount % 20 == 0) {
                ServerPlayer best = nearestPlayer(level);
                if (best != null) {
                    targetId = best.getUUID();
                    angle = this.random.nextDouble() * Math.PI * 2.0D;
                    best.addEffect(new MobEffectInstance(ModEffects.OBSESSION.get(), OBSESSION_TICKS - obsTicks, 0, false, true, true));
                }
            }
            return;
        }
        ServerPlayer target = resolve(level, targetId);
        if (target == null) {
            halt();
            if (++offlineTicks > 1200) {
                this.discard();
            }
            return;
        }
        offlineTicks = 0;
        if (cooldown > 0) {
            cooldown--;
        }
        if (purgeCooldown > 0) {
            purgeCooldown--;
        }
        if (getStage() == 1) {
            tickStage1(level, target);
        } else {
            tickStage2(level, target);
        }
    }

    // ------------------------------------------------------------------ etapa 1
    private void tickStage1(ServerLevel level, ServerPlayer target) {
        obsTicks++;
        if (obsTicks >= OBSESSION_TICKS) {
            enterStage2(level, target);
            return;
        }
        if (this.tickCount % 20 == 0) {
            if (!target.hasEffect(ModEffects.OBSESSION.get())) {
                target.addEffect(new MobEffectInstance(ModEffects.OBSESSION.get(), OBSESSION_TICKS - obsTicks, 0, false, true, true));
            }
            if (obsTicks >= 4800) {
                if (obsTicks < 5400) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false, false));
                }
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false, false));
            }
        }
        if (obsTicks >= 4800 && (obsTicks - 4800) % 80 == 0) {
            target.hurt(this.damageSources().magic(), 1.0F); // 0.5 corazones, ignora armadura
        }

        double dist;
        boolean invisibleAllowed;
        int inv, vis;
        if (obsTicks < 2400) {
            dist = 32.0D; inv = 80; vis = 16;
        } else if (obsTicks < 3600) {
            dist = 16.0D; inv = 40; vis = 16;
        } else if (obsTicks < 4800) {
            dist = 8.0D; inv = 40; vis = 16;
        } else {
            dist = 8.0D; inv = Integer.MAX_VALUE; vis = 0;
        }
        boolean invisible = inv == Integer.MAX_VALUE || (obsTicks % (inv + vis)) < inv;
        this.setInvisible(invisible);

        angle += 0.15D / dist;
        double gx = target.getX() + Math.cos(angle) * dist;
        double gz = target.getZ() + Math.sin(angle) * dist;
        double gy = target.getY() + 2.0D + Math.sin(this.tickCount * 0.05D);
        flyTo(gx, gy, gz, STAGE1_SPEED);
        faceTowards(target.getX() - getX(), target.getZ() - getZ());
    }

    private void enterStage2(ServerLevel level, ServerPlayer target) {
        setStage(2);
        this.setInvisible(false);
        this.setHealth(this.getMaxHealth());
        target.removeEffect(ModEffects.OBSESSION.get());
        act = Act.HUNT;
        cooldown = 80;
        stage2Ticks = 0;
        this.xpReward = 500;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 8.0F, 0.6F);
        target.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        target.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("LA KRASUE DESPIERTA").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
    }

    // ------------------------------------------------------------------ etapa 2
    private double speed2() {
        int level = Math.min(4, stage2Ticks / SPEED_STEP_TICKS);
        return BASE_SPEED * (1.0D + 0.2D * level);
    }

    private double hoverHeight() {
        return 5.5D + 2.5D * Math.sin(this.tickCount * 0.03D); // entre 3 y 8 bloques
    }

    private void tickStage2(ServerLevel level, ServerPlayer target) {
        stage2Ticks++;
        updateBossBar(level);
        faceTowards(target.getX() - getX(), target.getZ() - getZ());
        double spd = speed2();
        double dist = this.distanceTo(target);
        switch (act) {
            case HUNT -> {
                chase(target, spd, 6.0D, hoverHeight());
                if (cooldown <= 0) {
                    chooseAttack(level, target, dist);
                }
            }
            case WHIP -> {
                chase(target, spd, 6.0D, hoverHeight());
                if (actTicks == 0) {
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 4.0F, 0.5F);
                }
                if (actTicks == 12) {
                    whipStrike(level, target);
                }
                if (++actTicks >= 20) {
                    endAttack(50);
                }
            }
            case ASCEND -> {
                chase(target, spd, 7.0D, hoverHeight());
                tickAscend(level);
            }
            case RAPTO -> tickRapto(level, target);
            case PURGE -> tickPurge(level, target);
            case EXEC -> tickExec(level, target);
            case DRAIN -> tickDrain(level, target);
            default -> {
            }
        }
    }

    private void chooseAttack(ServerLevel level, ServerPlayer target, double dist) {
        int brine = getBrine(target);
        float frac = this.getHealth() / this.getMaxHealth();
        actTicks = 0;
        if (brine >= 50) {
            act = Act.EXEC;
            return;
        }
        if (brine >= 20 && target.getHealth() < 16.0F && purgeCooldown <= 0) {
            act = Act.PURGE;
            purgeStartHealth = this.getHealth();
            purgeCooldown = 600;
            return;
        }
        if (frac < 0.4F && brine >= 5 && this.random.nextFloat() < 0.5F) {
            startDrain(target);
            return;
        }
        int roll = this.random.nextInt(100);
        if (roll < 40) {
            act = dist <= 32.0D ? Act.WHIP : Act.HUNT;
        } else if (roll < 75) {
            act = Act.ASCEND;
            startAscend(target);
        } else if (dist <= 16.0D && startRapto(level, target)) {
            act = Act.RAPTO;
        } else {
            act = dist <= 32.0D ? Act.WHIP : Act.HUNT;
        }
        if (act == Act.HUNT) {
            cooldown = 20;
        }
    }

    private void endAttack(int pause) {
        act = Act.HUNT;
        actTicks = 0;
        cooldown = pause;
    }

    private void applyHunger(ServerPlayer p) {
        if (this.getHealth() < this.getMaxHealth() * 0.3F) {
            p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 9)); // Hambre 10 por 2 s
        }
    }

    /** Latigo: ataque a distancia, 10 de dano y +1 Salmuera. */
    private void whipStrike(ServerLevel level, ServerPlayer target) {
        Vec3 from = this.position().add(0.0D, this.getBbHeight() * 0.6D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        int steps = (int) Math.max(4, from.distanceTo(to) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, pt.x, pt.y, pt.z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pt.x, pt.y, pt.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 4.0F, 0.6F);
        if (target.hurt(this.damageSources().mobAttack(this), WHIP_DAMAGE)) {
            addBrine(target, 1);
            applyHunger(target);
        }
    }

    // ---- Ascension visceral
    private void startAscend(ServerPlayer target) {
        int brine = getBrine(target);
        int r = brine >= 5 ? 4 : 2;         // 8x8 con 5 cargas, si no 4x4
        ascendBrine = brine >= 5 ? 4 : 2;
        ascendBox = new AABB(target.getX() - r, target.getY() - 1.0D, target.getZ() - r,
                target.getX() + r, target.getY() + 3.0D, target.getZ() + r);
        actTicks = 0;
    }

    private void tickAscend(ServerLevel level) {
        if (ascendBox == null) {
            endAttack(40);
            return;
        }
        if (actTicks % 4 == 0) {
            for (int i = 0; i < 12; i++) {
                double x = Mth.lerp(this.random.nextDouble(), ascendBox.minX, ascendBox.maxX);
                double z = Mth.lerp(this.random.nextDouble(), ascendBox.minZ, ascendBox.maxZ);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, ascendBox.minY + 1.1D, z, 1, 0.0D, 0.05D, 0.0D, 0.01D);
            }
        }
        if (actTicks == 40) { // 2 s despues: los "Espians" emergen
            for (int i = 0; i < 60; i++) {
                double x = Mth.lerp(this.random.nextDouble(), ascendBox.minX, ascendBox.maxX);
                double z = Mth.lerp(this.random.nextDouble(), ascendBox.minZ, ascendBox.maxZ);
                level.sendParticles(ParticleTypes.CRIT, x, ascendBox.minY + 1.0D + this.random.nextDouble() * 2.0D, z, 2, 0.1D, 0.4D, 0.1D, 0.05D);
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, ascendBox.minY + 1.0D, z, 2, 0.1D, 0.6D, 0.1D, 0.02D);
            }
            level.playSound(null, ascendBox.getCenter().x, ascendBox.minY + 1.0D, ascendBox.getCenter().z,
                    SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 5.0F, 0.5F);
            for (ServerPlayer p : level.players()) {
                if (isValid(p) && ascendBox.intersects(p.getBoundingBox())) {
                    if (p.hurt(this.damageSources().mobAttack(this), ASCEND_DAMAGE)) {
                        addBrine(p, ascendBrine);
                        applyHunger(p);
                    }
                }
            }
        }
        if (++actTicks >= 50) {
            ascendBox = null;
            endAttack(60);
        }
    }

    // ---- Rapto visceral
    private boolean startRapto(ServerLevel level, ServerPlayer target) {
        int minY = level.getMinBuildHeight() + 2;
        int maxY = level.getMaxBuildHeight() - 9;
        int start = target.getBlockY() + (level.dimensionType().hasCeiling() ? 6 : 40);
        start = Mth.clamp(start, minY, maxY);
        int ox = Mth.floor(target.getX()) - 3, oz = Mth.floor(target.getZ()) - 3;
        int found = Integer.MIN_VALUE;
        for (int k = 0; k < 16 && found == Integer.MIN_VALUE; k++) {
            int y = start + ((k % 2 == 0) ? 1 : -1) * 8 * ((k + 1) / 2);
            if (y < minY || y > maxY) {
                continue;
            }
            if (cellFree(level, ox, y, oz)) {
                found = y;
            }
        }
        if (found == Integer.MIN_VALUE) {
            return false;
        }
        boolean lava = getBrine(target) >= 15;
        buildCell(level, ox, found, oz, lava);
        cellX = ox; cellY = found; cellZ = oz; cellBuilt = true;
        raptoReturn = target.position();
        addBrine(target, 1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 6.0F, 0.5F);
        target.connection.teleport(ox + 2.5D, found + 1.0D, oz + 3.0D, target.getYRot(), target.getXRot());
        this.moveTo(ox + 4.5D, found + 1.5D, oz + 3.0D);
        this.setDeltaMovement(Vec3.ZERO);
        actTicks = 0;
        return true;
    }

    private void tickRapto(ServerLevel level, ServerPlayer target) {
        halt();
        this.setPos(cellX + 4.5D, cellY + 1.5D, cellZ + 3.0D);
        if (actTicks % 40 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cellX + 3.0D, cellY + 2.0D, cellZ + 3.0D, 12, 1.2D, 1.0D, 1.2D, 0.01D);
        }
        if (target != null && actTicks >= 20 && actTicks % 20 == 0) {
            raptoLash(level, target);
            if (act != Act.RAPTO) {
                return; // el latigazo mato al objetivo: onTargetDied ya cambio el estado a FINISH
            }
        }
        if (++actTicks >= RAPTO_TICKS) {
            endRapto(level, target);
        }
    }

    /** Latigazo dentro de la celda: 3 PS (la armadura lo reduce), sin Salmuera extra. */
    private void raptoLash(ServerLevel level, ServerPlayer target) {
        Vec3 from = this.position().add(0.0D, this.getBbHeight() * 0.6D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        int steps = (int) Math.max(4, from.distanceTo(to) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, pt.x, pt.y, pt.z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 3.0F, 0.7F);
        target.hurt(this.damageSources().mobAttack(this), RAPTO_LASH_DAMAGE);
    }

    private void endRapto(ServerLevel level, ServerPlayer target) {
        if (target != null && raptoReturn != null) {
            target.connection.teleport(raptoReturn.x, raptoReturn.y, raptoReturn.z, target.getYRot(), target.getXRot());
            this.moveTo(raptoReturn.x, raptoReturn.y + 4.0D, raptoReturn.z);
        }
        clearCell(level);
        raptoReturn = null;
        endAttack(100);
    }

    private static boolean cellFree(ServerLevel level, int ox, int oy, int oz) {
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 7; y++) {
                for (int z = 0; z < 6; z++) {
                    BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                    if (!level.hasChunkAt(pos) || !level.isEmptyBlock(pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Celda 6x7x6 (interior 4x5x4): suelo y techo de ladrillos de piedra, paredes de barras de hierro. */
    private static void buildCell(ServerLevel level, int ox, int oy, int oz, boolean lava) {
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                boolean edge = x == 0 || x == 5 || z == 0 || z == 5;
                for (int y = 0; y < 7; y++) {
                    BlockState st;
                    if (y == 0 || y == 6) {
                        st = bricks;
                    } else if (edge) {
                        st = bars;
                    } else if (y == 1 && lava) {
                        st = Blocks.LAVA.defaultBlockState();
                    } else {
                        st = air;
                    }
                    level.setBlock(new BlockPos(ox + x, oy + y, oz + z), st, 3);
                }
            }
        }
    }

    private void clearCell(ServerLevel level) {
        if (!cellBuilt) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 7; y++) {
                for (int z = 0; z < 6; z++) {
                    level.setBlock(new BlockPos(cellX + x, cellY + y, cellZ + z), air, 3);
                }
            }
        }
        cellBuilt = false;
    }

    // ---- Purga
    private void tickPurge(ServerLevel level, ServerPlayer target) {
        flyTo(target.getX(), target.getY() + 8.0D, target.getZ(), speed2());
        if (actTicks % 20 == 0) {
            target.displayClientMessage(Component.literal("Purgando").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, target.getX(), target.getY() + 1.0D, target.getZ(), 10, 0.5D, 0.8D, 0.5D, 0.02D);
        }
        if (purgeStartHealth - this.getHealth() >= this.getMaxHealth() * 0.05F) {
            endAttack(80); // el dano la interrumpe
            return;
        }
        if (++actTicks >= PURGE_TICKS) {
            int charges = getBrine(target);
            target.removeEffect(ModEffects.BRINE.get());
            if (charges > 0) {
                target.hurt(this.damageSources().magic(), 2.0F * charges);
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.0F, 0.6F);
            endAttack(120);
        }
    }

    // ---- Ejecucion
    private void tickExec(ServerLevel level, ServerPlayer target) {
        flyTo(target.getX(), target.getY() + 3.5D, target.getZ(), speed2());
        if (actTicks == 0) {
            target.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
            target.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("Bebe leche").withStyle(ChatFormatting.GRAY)));
            target.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("INFECCION INMINENTE").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0F, 0.6F);
        }
        if (actTicks % 20 == 0) {
            target.displayClientMessage(Component.literal("INFECCION INMINENTE").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + 1.0D, target.getZ(), 20, 0.6D, 1.0D, 0.6D, 0.03D);
        }
        if (++actTicks >= EXEC_TICKS) { // no se puede detener
            if (getBrine(target) >= 50) {
                target.hurt(this.damageSources().genericKill(), Float.MAX_VALUE);
            }
            endAttack(120);
        }
    }

    // ---- Drenaje
    private void startDrain(ServerPlayer target) {
        act = Act.DRAIN;
        drainTargetId = target.getUUID();
        drainPresses = 0;
        drainRequired = 5 + this.random.nextInt(6); // 5 a 10
        actTicks = 0;
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> target), new HoundGrabPacket(true));
    }

    private void tickDrain(ServerLevel level, ServerPlayer target) {
        flyTo(target.getX(), target.getY() + 3.0D, target.getZ(), speed2());
        if (actTicks % 5 == 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
        }
        if (actTicks % 10 == 0) {
            target.displayClientMessage(Component.literal("¡ESPACIO! ¡ZAFATE!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        }
        if (actTicks % 20 == 0 && actTicks > 0) {
            this.heal(8.0F); // 4 corazones por segundo
            int b = getBrine(target);
            if (b > 0) {
                setBrine(target, b - 1);
            }
            level.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.0D, getZ(), 3, 0.4D, 0.4D, 0.4D, 0.0D);
        }
        if (drainPresses >= drainRequired || getBrine(target) <= 0 || ++actTicks > 400) {
            endDrain(target);
        }
    }

    private void endDrain(ServerPlayer target) {
        if (target != null) {
            ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> target), new HoundGrabPacket(false));
            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        drainTargetId = null;
        endAttack(100);
    }

    /** ESPACIO pulsado por el jugador atrapado por el Drenaje. */
    public static void onEscapePress(ServerPlayer player) {
        for (StalkerEntity s : player.serverLevel().getEntitiesOfClass(StalkerEntity.class, player.getBoundingBox().inflate(128.0D))) {
            if (s.drainTargetId != null && s.drainTargetId.equals(player.getUUID())) {
                s.drainPresses++;
            }
        }
    }

    // ------------------------------------------------------------------ final
    /** Llamado por StalkerEvents cuando muere su objetivo. */
    public void onTargetDied(ServerPlayer victim) {
        if (act == Act.FINISH || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (act == Act.RAPTO) {
            clearCell(level);
        }
        if (act == Act.DRAIN) {
            drainTargetId = null;
        }
        act = Act.FINISH;
        actTicks = 0;
        finishPos = victim.position();
        this.setInvisible(false);
        level.playSound(null, getX(), getY(), getZ(), ModSounds.FLESH_FORM.get(), SoundSource.HOSTILE, 4.0F, 0.8F);
    }

    private void tickFinish(ServerLevel level) {
        this.setInvisible(false);
        Vec3 fp = finishPos != null ? finishPos : this.position();
        double dx = fp.x - getX(), dz = fp.z - getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        Vec3 m = this.getDeltaMovement();
        if (h > 1.5D) {
            this.setDeltaMovement(dx / h * 0.3D, m.y, dz / h * 0.3D);
        } else {
            this.setDeltaMovement(0.0D, m.y, 0.0D);
        }
        if (this.onGround() || h <= 1.5D && Math.abs(getY() - fp.y) < 2.0D) {
            if (++actTicks % 10 == 0) {
                level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.5D, getZ(), 6, 0.6D, 1.2D, 0.6D, 0.02D);
            }
            if (actTicks >= FINISH_TICKS) {
                this.discard(); // terminado su trabajo: desaparece sin soltar nada
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (act == Act.FINISH) {
            this.spawnAtLocation(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
        } else {
            this.spawnAtLocation(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
            this.spawnAtLocation(new ItemStack(Items.TOTEM_OF_UNDYING, 3));
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel level) {
            if (act == Act.RAPTO && raptoReturn != null) {
                ServerPlayer t = resolve(level, targetId);
                if (t != null) {
                    t.connection.teleport(raptoReturn.x, raptoReturn.y, raptoReturn.z, t.getYRot(), t.getXRot());
                }
            }
            clearCell(level);
            if (act == Act.DRAIN) {
                endDrain(resolve(level, targetId));
            }
            clearEffectsEverywhere(level);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            bossEvent.removeAllPlayers();
            if (this.level() instanceof ServerLevel level) {
                if (act == Act.RAPTO && raptoReturn != null && reason.shouldDestroy()) {
                    ServerPlayer t = resolve(level, targetId);
                    if (t != null) {
                        t.connection.teleport(raptoReturn.x, raptoReturn.y, raptoReturn.z, t.getYRot(), t.getXRot());
                    }
                }
                clearCell(level);
                if (act == Act.DRAIN) {
                    endDrain(resolve(level, targetId));
                }
                if (reason.shouldDestroy()) {
                    clearEffectsEverywhere(level);
                }
            }
        }
        super.remove(reason);
    }

    private void clearEffectsEverywhere(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            p.removeEffect(ModEffects.BRINE.get());
            p.removeEffect(ModEffects.OBSESSION.get());
        }
    }

    // ------------------------------------------------------------------ salmuera
    public static int getBrine(LivingEntity e) {
        MobEffectInstance inst = e.getEffect(ModEffects.BRINE.get());
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }

    public static void setBrine(LivingEntity e, int charges) {
        charges = Mth.clamp(charges, 0, BRINE_CAP);
        e.removeEffect(ModEffects.BRINE.get());
        if (charges > 0) {
            e.addEffect(new MobEffectInstance(ModEffects.BRINE.get(), MobEffectInstance.INFINITE_DURATION, charges - 1, false, true, true));
        }
    }

    public static void addBrine(LivingEntity e, int n) {
        setBrine(e, getBrine(e) + n);
    }

    // ------------------------------------------------------------------ musica
    /** Modo de musica para un jugador: 0 nada, 3 acecho 1, 4 acecho 2, 5 persecucion. */
    public int musicModeFor(ServerPlayer p) {
        if (act == Act.FINISH || !this.isAlive()) {
            return 0;
        }
        boolean related = p.getUUID().equals(targetId) || this.distanceTo(p) <= TERROR_RADIUS;
        if (!related) {
            return 0;
        }
        if (getStage() == 2) {
            return 5;
        }
        if (obsTicks >= 3600) {
            return 4;
        }
        if (obsTicks >= 2400) {
            return 3;
        }
        return 0;
    }

    // ------------------------------------------------------------------ utilidades
    private void updateBossBar(ServerLevel level) {
        bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        if (this.tickCount % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                if (this.distanceTo(p) <= 128.0F) {
                    bossEvent.addPlayer(p);
                } else {
                    bossEvent.removePlayer(p);
                }
            }
        }
    }

    private void chase(ServerPlayer target, double speed, double horizontal, double height) {
        double dx = getX() - target.getX(), dz = getZ() - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3D) {
            dx = 1.0D;
            dz = 0.0D;
            len = 1.0D;
        }
        double gx = target.getX() + dx / len * horizontal;
        double gz = target.getZ() + dz / len * horizontal;
        flyTo(gx, target.getY() + height, gz, speed);
    }

    private void flyTo(double gx, double gy, double gz, double speed) {
        double dx = gx - getX(), dy = gy - getY(), dz = gz - getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4D) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double s = Math.min(speed, len);
        this.setDeltaMovement(dx / len * s, dy / len * s, dz / len * s);
    }

    private void halt() {
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void faceTowards(double dx, double dz) {
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    private static boolean isValid(ServerPlayer p) {
        return p.isAlive() && !p.isRemoved() && !p.isSpectator()
                && p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL;
    }

    private ServerPlayer resolve(ServerLevel level, UUID id) {
        if (id == null) {
            return null;
        }
        net.minecraft.world.entity.Entity e = level.getEntity(id);
        return e instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    private ServerPlayer nearestPlayer(ServerLevel level) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (!isValid(p)) {
                continue;
            }
            double d = this.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public UUID getTargetId() {
        return targetId;
    }

    // ------------------------------------------------------------------ NBT
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Stage", getStage());
        tag.putInt("ObsTicks", obsTicks);
        tag.putInt("Stage2Ticks", stage2Ticks);
        if (targetId != null) {
            tag.putUUID("Target", targetId);
        }
        tag.putBoolean("CellBuilt", cellBuilt);
        tag.putInt("CellX", cellX);
        tag.putInt("CellY", cellY);
        tag.putInt("CellZ", cellZ);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setStage(tag.contains("Stage") ? tag.getInt("Stage") : 1);
        obsTicks = tag.getInt("ObsTicks");
        stage2Ticks = tag.getInt("Stage2Ticks");
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        cellBuilt = tag.getBoolean("CellBuilt");
        cellX = tag.getInt("CellX");
        cellY = tag.getInt("CellY");
        cellZ = tag.getInt("CellZ");
        // v4: la Krasue ya no tiene Resistencia; limpia la que traian guardada las Krasue de la v3
        this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
    }
}
