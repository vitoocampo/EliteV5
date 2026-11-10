package com.elitebosses.entity;

import com.elitebosses.events.JudgmentEvents;
import com.elitebosses.net.JudgeHudPacket;
import com.elitebosses.net.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.UUID;

/**
 * El Juicio. Todo el comportamiento corre en el servidor; el cliente dibuja el modelo GeckoLib,
 * el HUD (JudgeClient) y la musica JudgeChase.
 *
 * Habilidades: rayo de llegada + empujon, Ataque basico (baculo), Valentia (Furia), Empalar (evento rapido),
 * Exilio, Invocacion Solar y reposicionamiento hacia atras.
 */
public class JudgeEntity extends PathfinderMob implements GeoEntity {

    // ------------------------------------------------------------------
    // AJUSTES (todo lo que puedes querer cambiar esta aqui arriba)
    // ------------------------------------------------------------------
    public static final float MAX_HP = 600.0F;              // 300 corazones
    private static final double SPEED = 0.27D;              // bloques/tick (~5.4 b/s): parecido a un jugador corriendo
    private static final double STRAFE_SPEED = 0.20D;
    private static final double KEEP_DISTANCE = 3.6D;       // no se pega mas que esto al jugador

    private static final float ARRIVAL_BOLT_DAMAGE = 2.0F;  // 1 corazon
    private static final double PUSH_HORIZONTAL = 1.25D;    // ~10 bloques de empujon
    private static final double PUSH_VERTICAL = 0.55D;

    private static final double VALOR_RANGE = 20.0D;        // Valentia: a mas de 20 bloques...
    private static final int VALOR_GRACE = 40;              // ...durante 2 s seguidos...
    private static final int FURY_PERIOD = 300;             // ...usa Furia cada 15 s
    private static final int FURY_BOLTS = 15;               // 15 rayos
    private static final float FURY_BOLT_DAMAGE = 1.0F;     // 0.5 corazones cada uno
    private static final int FURY_BOLT_INTERVAL = 4;        // un rayo cada 0.2 s

    private static final float SMASH_MIN = 6.0F;            // ataque basico: 6 PS con vida llena...
    private static final float SMASH_MAX = 8.0F;            // ...hasta 8 PS cuanto menos vida tenga
    private static final double SMASH_RANGE = 5.2D;
    private static final int SMASH_HIT_TICK = 14;           // coincide con la animacion (0.70 s)
    private static final int SMASH_LENGTH = 26;
    private static final int SMASH_COOLDOWN = 34;

    private static final int IMPALE_CD_ESCAPED = 300;       // 15 s si te zafas
    private static final int IMPALE_CD_ESCAPED_P = 400;     // 20 s si salio la P
    private static final int IMPALE_CD_LANDED = 600;        // 30 s si te empala
    private static final int IMPALE_CD_MISSED = 120;        // fallo la embestida
    private static final int QTE_WINDOW = 40;               // 2 s para pulsar la tecla
    private static final int QTE_LAG_GRACE = 6;
    private static final int IMPALE_PRESSES = 7;            // ESPACIO 7 veces
    private static final float IMPALE_PRESS_COST = 1.0F;    // cada pulsacion cuesta 1 PS
    private static final int IMPALE_MAX_TICKS = 600;        // seguro: te suelta a los 30 s
    private static final double LUNGE_SPEED = 0.78D;
    private static final double LUNGE_MAX_RANGE = 16.0D;

    private static final float EXILE_CHANCE = 0.15F;        // 15% de usar Exilio
    private static final int EXILE_COOLDOWN = 500;
    private static final int EXILE_LIFT_TICKS = 30;
    private static final double EXILE_LIFT = 5.0D;          // lo levanta 5 bloques
    private static final int EXILE_MIN = 40, EXILE_MAX = 100;

    private static final int SOLAR_COOLDOWN = 240;
    private static final int SOLAR_CHANNEL = 20;            // 1 s de canalizacion
    private static final float SOLAR_DAMAGE = 6.0F;         // 3 corazones
    private static final double SOLAR_HALF = 1.0D;          // area 2x2

    private static final int BACKSTEP_PERIOD = 200;         // cada 10 s se reposiciona hacia atras
    private static final int INTRO_TICKS = 52;
    private static final int PUSH_TICKS = 22;
    private static final double BAR_RANGE = 96.0D;

    // estados sincronizados (solo para elegir animacion)
    public static final int STATE_NORMAL = 0, STATE_INTRO = 1, STATE_PUSH = 2, STATE_SMASH = 3, STATE_LUNGE = 4,
            STATE_HOLD = 5, STATE_IMPALE = 6, STATE_EXILE = 7, STATE_SOLAR = 8, STATE_BACKSTEP = 9, STATE_FURY = 10;

    public static final String QTE_KEYS = "RGCP";

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(JudgeEntity.class, EntityDataSerializers.INT);

    private static final TicketType<ChunkPos> JUDGE_TICKET =
            TicketType.create("elitebosses_judge", Comparator.comparingLong(ChunkPos::toLong), 60);

    private enum Act { INTRO, PUSH, CHASE, STRAFE, SMASH, LUNGE, HOLD, IMPALED, EXILE, SOLAR, BACKSTEP, FURY }

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.elitebosses.judge"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

    private UUID targetId;
    private boolean fromJudgment;       // aparecio por la condena (no por huevo/comando)
    private Act act = Act.INTRO;
    private int actTicks;
    private int globalCd = 20;
    private int smashCd, impaleCd = 100, solarCd = 160, exileCd = 300, backstepCd = BACKSTEP_PERIOD;
    private int farTicks, furyCd, furyBolts, furyTimer;
    private int strafeTicks, strafeDir = 1, nextStrafe = 100;
    private int stuckTicks;
    private double lastDist = -1.0D;
    private int noTargetTicks;
    // empalar
    private UUID heldId;
    private int qteKey = -1, qteTicks, impalePresses;
    // exilio
    private Vec3 exileStart;
    private boolean exileTracking;
    // invocacion solar
    private Vec3 solarCenter;

    public JudgeEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 800;
        this.setPersistenceRequired();
        this.setMaxUpStep(2.0F);
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    /** Llamado por JudgmentEvents al terminar el rayo de luz. */
    public void setSentenced(ServerPlayer target) {
        this.targetId = target.getUUID();
        this.fromJudgment = true;
    }

    public UUID getTargetId() {
        return targetId;
    }

    // ------------------------------------------------------------------ animaciones (GeckoLib)
    private static final String P = "animation.judge.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation INTRO = RawAnimation.begin().thenPlayAndHold(P + "intro");
    private static final RawAnimation PUSH = RawAnimation.begin().thenPlayAndHold(P + "push");
    private static final RawAnimation SMASH = RawAnimation.begin().thenPlayAndHold(P + "smash");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenLoop(P + "lunge");
    private static final RawAnimation HOLD = RawAnimation.begin().thenLoop(P + "hold");
    private static final RawAnimation IMPALE = RawAnimation.begin().thenPlay(P + "impale").thenLoop(P + "impale_hold");
    private static final RawAnimation EXILE = RawAnimation.begin().thenPlayAndHold(P + "exile");
    private static final RawAnimation SOLAR = RawAnimation.begin().thenPlayAndHold(P + "solar");
    private static final RawAnimation BACKSTEP = RawAnimation.begin().thenPlayAndHold(P + "backstep");
    private static final RawAnimation FURY = RawAnimation.begin().thenPlayAndHold(P + "fury");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
    }

    private PlayState mainPredicate(AnimationState<JudgeEntity> state) {
        if (this.isDeadOrDying()) {
            state.getController().setAnimationSpeed(1.0D);
            return state.setAndContinue(DEATH);
        }
        double speed = 1.0D;
        RawAnimation anim;
        switch (getJudgeState()) {
            case STATE_INTRO -> anim = INTRO;
            case STATE_PUSH -> anim = PUSH;
            case STATE_SMASH -> anim = SMASH;
            case STATE_LUNGE -> anim = LUNGE;
            case STATE_HOLD -> anim = HOLD;
            case STATE_IMPALE -> anim = IMPALE;
            case STATE_EXILE -> anim = EXILE;
            case STATE_SOLAR -> anim = SOLAR;
            case STATE_BACKSTEP -> anim = BACKSTEP;
            case STATE_FURY -> anim = FURY;
            default -> {
                float amount = state.getLimbSwingAmount();
                if (state.isMoving() && amount > 0.08F) {
                    anim = WALK;
                    speed = Mth.clamp(0.55D + amount * 0.9D, 0.6D, 1.35D);
                } else {
                    anim = IDLE;
                }
            }
        }
        state.getController().setAnimationSpeed(speed);
        return state.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, STATE_INTRO);
    }

    public int getJudgeState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setJudgeState(int s) {
        if (this.entityData.get(DATA_STATE) != s) {
            this.entityData.set(DATA_STATE, s);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(4.5D, 2.5D, 4.5D); // alas y halo salen de la caja
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        // los rayos son suyos
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5F;
    }

    @Override
    public float getVoicePitch() {
        return 0.45F;
    }

    // ------------------------------------------------------------------
    // Cerebro (solo servidor)
    // ------------------------------------------------------------------
    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive() && this.level() instanceof ServerLevel serverLevel) {
            brain(serverLevel);
        }
        super.aiStep();
    }

    private void brain(ServerLevel level) {
        if (this.tickCount % 20 == 0) {
            keepChunksLoaded(level);
        }
        if (globalCd > 0) globalCd--;
        if (smashCd > 0) smashCd--;
        if (impaleCd > 0) impaleCd--;
        if (solarCd > 0) solarCd--;
        if (exileCd > 0) exileCd--;
        if (backstepCd > 0) backstepCd--;
        if (furyCd > 0) furyCd--;

        ServerPlayer target = acquireTarget(level);
        updateBossBar(level, target);
        if (target == null) {
            if (++noTargetTicks > 200) {
                vanish(level);
            }
            halt();
            setJudgeState(STATE_NORMAL);
            return;
        }
        noTargetTicks = 0;
        if (fromJudgment) {
            JudgmentEvents.markJudgeActive(target);
        }
        if (this.tickCount % 10 == 0) {
            clearLeaves(level);
        }

        double dist = this.distanceTo(target);
        tickValor(level, target, dist);
        tickExileTracking(target, dist);

        switch (act) {
            case INTRO -> tickIntro(level, target);
            case PUSH -> tickPush(level, target);
            case CHASE, STRAFE -> tickChase(level, target, dist);
            case SMASH -> tickSmash(level, target, dist);
            case LUNGE -> tickLunge(level, target, dist);
            case HOLD -> tickHold(level, target);
            case IMPALED -> tickImpaled(level, target);
            case EXILE -> tickExile(level, target);
            case SOLAR -> tickSolar(level, target);
            case BACKSTEP -> tickBackstep(target);
            case FURY -> tickFuryPose(target);
        }
    }

    // ------------------------------ llegada: rayo + empujon ------------------------------
    private void tickIntro(ServerLevel level, ServerPlayer target) {
        setJudgeState(STATE_INTRO);
        halt();
        lookAt(target);
        if (actTicks == 0) {
            smite(level, target, ARRIVAL_BOLT_DAMAGE);
            play(level, SoundEvents.BEACON_ACTIVATE, 4.0F, 0.5F);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.0D, getZ(), 120, 1.2D, 2.5D, 1.2D, 0.05D);
        }
        if (++actTicks >= INTRO_TICKS) {
            setAct(Act.PUSH);
        }
    }

    private void tickPush(ServerLevel level, ServerPlayer target) {
        setJudgeState(STATE_PUSH);
        halt();
        lookAt(target);
        if (actTicks == 11) {
            Vec3 dir = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
            dir = dir.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, this.getYRot()) : dir.normalize();
            target.setDeltaMovement(dir.x * PUSH_HORIZONTAL, PUSH_VERTICAL, dir.z * PUSH_HORIZONTAL);
            target.hurtMarked = true;
            target.fallDistance = 0.0F;
            play(level, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
            level.sendParticles(ParticleTypes.SONIC_BOOM, getX() + dir.x * 2.0D, getY() + 3.0D, getZ() + dir.z * 2.0D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (++actTicks >= PUSH_TICKS) {
            globalCd = 20;
            setAct(Act.CHASE);
        }
    }

    // ------------------------------ persecucion ------------------------------
    private void tickChase(ServerLevel level, ServerPlayer target, double dist) {
        setJudgeState(STATE_NORMAL);
        lookAt(target);

        // reposicionamiento hacia atras cada 10 s (solo si esta cerca)
        if (backstepCd <= 0 && dist < 7.0D && globalCd <= 0) {
            backstepCd = BACKSTEP_PERIOD;
            setAct(Act.BACKSTEP);
            return;
        }
        if (globalCd <= 0 && chooseAttack(level, target, dist)) {
            return;
        }

        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (act == Act.STRAFE) {
            // rodea al jugador sin alejarse: tangente + correccion de distancia
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0E-3D) {
                double tx = -dz / len * strafeDir, tz = dx / len * strafeDir;
                double radial = Mth.clamp((dist - (KEEP_DISTANCE + 1.5D)) * 0.3D, -0.5D, 1.0D);
                moveDir(tx + dx / len * radial, tz + dz / len * radial, STRAFE_SPEED, target);
            }
            if (--strafeTicks <= 0 || dist > 12.0D) {
                act = Act.CHASE;
                nextStrafe = 80 + this.random.nextInt(80);
            }
        } else {
            if (dist > KEEP_DISTANCE) {
                moveDir(dx, dz, SPEED, target);
            } else {
                halt();
            }
            if (--nextStrafe <= 0 && dist < 9.0D) {
                act = Act.STRAFE;
                strafeTicks = 30 + this.random.nextInt(25);
                strafeDir = this.random.nextBoolean() ? 1 : -1;
            }
        }
        checkStuck(level, target, dist);
    }

    /** Devuelve true si empezo un ataque. */
    private boolean chooseAttack(ServerLevel level, ServerPlayer target, double dist) {
        boolean specialReady = (impaleCd <= 0 && dist >= 3.0D && dist <= LUNGE_MAX_RANGE)
                || (solarCd <= 0 && dist <= 32.0D);
        if (specialReady && this.random.nextFloat() < 0.55F) {
            // 15% de que el especial sea Exilio
            if (exileCd <= 0 && dist <= 12.0D && this.random.nextFloat() < EXILE_CHANCE) {
                startExile(target);
                return true;
            }
            boolean canImpale = impaleCd <= 0 && dist >= 3.0D && dist <= LUNGE_MAX_RANGE;
            boolean canSolar = solarCd <= 0 && dist <= 32.0D;
            if (canImpale && (!canSolar || this.random.nextBoolean())) {
                setAct(Act.LUNGE);
                play(level, SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
                return true;
            }
            if (canSolar) {
                startSolar(level, target);
                return true;
            }
        }
        if (smashCd <= 0 && dist <= SMASH_RANGE - 0.4D) {
            setAct(Act.SMASH);
            return true;
        }
        return false;
    }

    // ------------------------------ ataque basico ------------------------------
    private void tickSmash(ServerLevel level, ServerPlayer target, double dist) {
        setJudgeState(STATE_SMASH);
        halt();
        if (actTicks < SMASH_HIT_TICK) {
            lookAt(target);
        }
        if (actTicks == 4) {
            play(level, SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.4F);
        }
        if (actTicks == SMASH_HIT_TICK) {
            Vec3 f = Vec3.directionFromRotation(0.0F, this.getYRot());
            level.sendParticles(ParticleTypes.EXPLOSION, getX() + f.x * 3.5D, getY() + 0.2D, getZ() + f.z * 3.5D,
                    2, 0.4D, 0.1D, 0.4D, 0.0D);
            play(level, SoundEvents.ANVIL_LAND, 2.5F, 0.5F);
            if (dist <= SMASH_RANGE && Math.abs(target.getY() - getY()) < 6.0D) {
                float missing = 1.0F - Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F);
                float dmg = SMASH_MIN + (SMASH_MAX - SMASH_MIN) * missing;
                target.hurt(this.damageSources().mobAttack(this), dmg);
                target.knockback(0.6D, getX() - target.getX(), getZ() - target.getZ());
                target.hurtMarked = true;
            }
        }
        if (++actTicks >= SMASH_LENGTH) {
            smashCd = SMASH_COOLDOWN;
            globalCd = 10;
            setAct(Act.CHASE);
        }
    }

    // ------------------------------ empalar ------------------------------
    private void tickLunge(ServerLevel level, ServerPlayer target, double dist) {
        setJudgeState(STATE_LUNGE);
        moveDir(target.getX() - getX(), target.getZ() - getZ(), LUNGE_SPEED, target);
        if (dist <= 3.4D && !target.isPassenger()) {
            beginHold(level, target);
        } else if (++actTicks > 30) {
            impaleCd = IMPALE_CD_MISSED;
            globalCd = 20;
            setAct(Act.CHASE);
        }
    }

    private void beginHold(ServerLevel level, ServerPlayer target) {
        heldId = target.getUUID();
        float r = this.random.nextFloat();
        qteKey = r < 0.30F ? 0 : r < 0.60F ? 1 : r < 0.90F ? 2 : 3; // R, G, C 30% c/u; P 10%
        qteTicks = 0;
        setAct(Act.HOLD);
        halt();
        send(target, JudgeHudPacket.QTE_START, qteKey, QTE_WINDOW);
        play(level, SoundEvents.WARDEN_ROAR, 3.0F, 0.7F);
    }

    private Vec3 holdPoint() {
        Vec3 f = Vec3.directionFromRotation(0.0F, this.getYRot());
        return new Vec3(getX() + f.x * 2.3D, getY() + 4.4D, getZ() + f.z * 2.3D);
    }

    private void tickHold(ServerLevel level, ServerPlayer ignored) {
        setJudgeState(STATE_HOLD);
        halt();
        ServerPlayer p = resolve(level, heldId);
        if (p == null || !isValid(p)) {
            endHold(level, null, false);
            return;
        }
        lookAt(p);
        Vec3 h = holdPoint();
        float yaw = (float) (Mth.atan2(getZ() - h.z, getX() - h.x) * (180.0D / Math.PI)) - 90.0F;
        p.connection.teleport(h.x, h.y, h.z, yaw, -35.0F);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        if (++qteTicks > QTE_WINDOW + QTE_LAG_GRACE) {
            send(p, JudgeHudPacket.QTE_END, 0, 0);
            beginImpale(level, p);
        }
    }

    /** El cliente pulso R/G/C/P. */
    public static void onQteKey(ServerPlayer player, int key) {
        for (JudgeEntity j : player.serverLevel().getEntitiesOfClass(JudgeEntity.class, player.getBoundingBox().inflate(16.0D))) {
            if (j.act == Act.HOLD && player.getUUID().equals(j.heldId)) {
                if (key == j.qteKey) {
                    j.send(player, JudgeHudPacket.QTE_END, 1, 0);
                    j.endHold(player.serverLevel(), player, true);
                } else {
                    j.send(player, JudgeHudPacket.QTE_END, 0, 0);
                    j.beginImpale(player.serverLevel(), player);
                }
                return;
            }
        }
    }

    private void endHold(ServerLevel level, ServerPlayer p, boolean escaped) {
        if (p != null) {
            Vec3 away = p.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            away = away.lengthSqr() < 1.0E-4D ? Vec3.ZERO : away.normalize();
            p.setDeltaMovement(away.x * 0.7D, 0.2D, away.z * 0.7D);
            p.hurtMarked = true;
            p.fallDistance = 0.0F;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
        }
        impaleCd = escaped ? (qteKey == 3 ? IMPALE_CD_ESCAPED_P : IMPALE_CD_ESCAPED) : IMPALE_CD_MISSED;
        heldId = null;
        qteKey = -1;
        globalCd = 25;
        setAct(Act.CHASE);
    }

    private void beginImpale(ServerLevel level, ServerPlayer p) {
        heldId = p.getUUID();
        impalePresses = IMPALE_PRESSES;
        setAct(Act.IMPALED);
        send(p, JudgeHudPacket.IMPALE, impalePresses, 0);
        play(level, SoundEvents.TRIDENT_HIT, 3.0F, 0.5F);
        play(level, SoundEvents.PLAYER_HURT, 2.0F, 0.7F);
    }

    private Vec3 pinPoint() {
        Vec3 f = Vec3.directionFromRotation(0.0F, this.getYRot());
        Vec3 right = new Vec3(-f.z, 0.0D, f.x);
        return new Vec3(getX() + f.x * 1.6D + right.x * 1.2D, getY(), getZ() + f.z * 1.6D + right.z * 1.2D);
    }

    private void tickImpaled(ServerLevel level, ServerPlayer ignored) {
        setJudgeState(STATE_IMPALE);
        halt();
        ServerPlayer p = resolve(level, heldId);
        if (p == null || !isValid(p) || ++actTicks > IMPALE_MAX_TICKS) {
            releaseImpale(level, p);
            return;
        }
        Vec3 pin = pinPoint();
        p.connection.teleport(pin.x, pin.y, pin.z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        if (actTicks % 20 == 0) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pin.x, pin.y + 1.0D, pin.z, 3, 0.2D, 0.3D, 0.2D, 0.0D);
        }
    }

    /** El jugador empalado pulso ESPACIO. */
    public static void onSpacePress(ServerPlayer player) {
        for (JudgeEntity j : player.serverLevel().getEntitiesOfClass(JudgeEntity.class, player.getBoundingBox().inflate(16.0D))) {
            if (j.act == Act.IMPALED && player.getUUID().equals(j.heldId)) {
                player.invulnerableTime = 0;
                player.hurt(j.damageSources().indirectMagic(j, j), IMPALE_PRESS_COST);
                j.impalePresses--;
                if (j.impalePresses <= 0 || !player.isAlive()) {
                    j.releaseImpale(player.serverLevel(), player);
                } else {
                    j.send(player, JudgeHudPacket.IMPALE, j.impalePresses, 0);
                }
                return;
            }
        }
    }

    private void releaseImpale(ServerLevel level, ServerPlayer p) {
        if (p != null) {
            send(p, JudgeHudPacket.IMPALE, -1, 0);
            if (p.isAlive()) {
                Vec3 away = p.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
                away = away.lengthSqr() < 1.0E-4D ? Vec3.ZERO : away.normalize();
                p.setDeltaMovement(away.x * 0.6D, 0.3D, away.z * 0.6D);
                p.hurtMarked = true;
            }
        }
        heldId = null;
        impaleCd = IMPALE_CD_LANDED;
        globalCd = 30;
        setAct(Act.CHASE);
    }

    // ------------------------------ exilio ------------------------------
    private void startExile(ServerPlayer target) {
        exileCd = EXILE_COOLDOWN;
        heldId = target.getUUID();
        exileStart = target.position();
        setAct(Act.EXILE);
        play((ServerLevel) this.level(), SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 0.5F);
    }

    private void tickExile(ServerLevel level, ServerPlayer ignored) {
        setJudgeState(STATE_EXILE);
        halt();
        ServerPlayer p = resolve(level, heldId);
        if (p == null || !isValid(p) || exileStart == null) {
            heldId = null;
            setAct(Act.CHASE);
            return;
        }
        lookAt(p);
        actTicks++;
        if (actTicks <= EXILE_LIFT_TICKS) {
            double k = actTicks / (double) EXILE_LIFT_TICKS;
            double ease = k * k * (3.0D - 2.0D * k);
            p.connection.teleport(exileStart.x, exileStart.y + EXILE_LIFT * ease, exileStart.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0.0F;
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.5D, p.getZ(), 4, 0.3D, 0.6D, 0.3D, 0.02D);
            return;
        }
        BlockPos dest = null;
        for (int i = 0; i < 32 && dest == null; i++) {
            double ang = this.random.nextDouble() * Math.PI * 2.0D;
            double d = EXILE_MIN + this.random.nextDouble() * (EXILE_MAX - EXILE_MIN);
            dest = findSurface(level, getX() + Math.cos(ang) * d, getZ() + Math.sin(ang) * d);
        }
        if (dest != null) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0D, p.getZ(), 80, 0.4D, 1.0D, 0.4D, 0.1D);
            p.connection.teleport(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D, p.getYRot(), p.getXRot());
            p.fallDistance = 0.0F;
            p.setDeltaMovement(Vec3.ZERO);
            level.playSound(null, dest, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2.0F, 0.5F);
            exileTracking = true;
            farTicks = VALOR_GRACE; // Valentia empieza a contar al instante
        }
        heldId = null;
        exileStart = null;
        globalCd = 30;
        setAct(Act.CHASE);
    }

    private void tickExileTracking(ServerPlayer target, double dist) {
        if (!exileTracking) {
            return;
        }
        if (dist <= VALOR_RANGE) {
            exileTracking = false;
            send(target, JudgeHudPacket.DISTANCE, -1, 0);
        } else if (this.tickCount % 5 == 0) {
            send(target, JudgeHudPacket.DISTANCE, (int) Math.round(dist), 0);
        }
    }

    // ------------------------------ invocacion solar ------------------------------
    private void startSolar(ServerLevel level, ServerPlayer target) {
        solarCd = SOLAR_COOLDOWN;
        BlockPos ground = findSurface(level, target.getX(), target.getZ());
        double gy = ground != null && Math.abs(ground.getY() - target.getY()) < 4.0D ? ground.getY() : target.getY();
        solarCenter = new Vec3(target.getX(), gy, target.getZ());
        JudgeBeamEntity.spawn(level, solarCenter.x, solarCenter.y, solarCenter.z, JudgeBeamEntity.MODE_TELEGRAPH, SOLAR_CHANNEL + 2);
        level.playSound(null, solarCenter.x, solarCenter.y, solarCenter.z, SoundEvents.BEACON_POWER_SELECT,
                SoundSource.HOSTILE, 3.0F, 0.6F);
        setAct(Act.SOLAR);
    }

    private void tickSolar(ServerLevel level, ServerPlayer target) {
        setJudgeState(STATE_SOLAR);
        halt();
        lookAt(target);
        if (actTicks == SOLAR_CHANNEL && solarCenter != null) {
            JudgeBeamEntity.spawn(level, solarCenter.x, solarCenter.y, solarCenter.z, JudgeBeamEntity.MODE_STRIKE, 16);
            level.playSound(null, solarCenter.x, solarCenter.y, solarCenter.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.HOSTILE, 4.0F, 0.8F);
            level.sendParticles(ParticleTypes.FLAME, solarCenter.x, solarCenter.y + 0.2D, solarCenter.z, 40, 0.8D, 0.2D, 0.8D, 0.05D);
            AABB area = new AABB(solarCenter.x - SOLAR_HALF, solarCenter.y - 1.0D, solarCenter.z - SOLAR_HALF,
                    solarCenter.x + SOLAR_HALF, solarCenter.y + 64.0D, solarCenter.z + SOLAR_HALF);
            if (target.getBoundingBox().intersects(area)) {
                target.invulnerableTime = 0;
                target.hurt(this.damageSources().indirectMagic(this, this), SOLAR_DAMAGE);
                JudgmentEvents.applyHolyBurn(target);
            }
        }
        if (++actTicks >= SOLAR_CHANNEL + 10) {
            solarCenter = null;
            globalCd = 20;
            setAct(Act.CHASE);
        }
    }

    // ------------------------------ reposicion ------------------------------
    private void tickBackstep(ServerPlayer target) {
        setJudgeState(STATE_BACKSTEP);
        lookAt(target);
        if (actTicks == 0) {
            play((ServerLevel) this.level(), SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
            Vec3 m = this.getDeltaMovement();
            this.setDeltaMovement(m.x, 0.32D, m.z);
        }
        if (actTicks < 12) {
            double dx = getX() - target.getX(), dz = getZ() - target.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0E-3D) {
                Vec3 m = this.getDeltaMovement();
                this.setDeltaMovement(dx / len * 0.48D, m.y, dz / len * 0.48D);
            }
        } else {
            halt();
        }
        if (++actTicks >= 16) {
            globalCd = 6;
            setAct(Act.CHASE);
        }
    }

    // ------------------------------ valentia (furia) ------------------------------
    private void tickValor(ServerLevel level, ServerPlayer target, double dist) {
        // descarga de los rayos que quedan de la ultima Furia
        if (furyBolts > 0 && --furyTimer <= 0) {
            furyTimer = FURY_BOLT_INTERVAL;
            furyBolts--;
            smite(level, target, FURY_BOLT_DAMAGE);
        }
        boolean busy = act == Act.HOLD || act == Act.IMPALED || act == Act.EXILE || act == Act.INTRO || act == Act.PUSH;
        if (dist > VALOR_RANGE && !busy) {
            farTicks++;
            if (farTicks >= VALOR_GRACE && furyCd <= 0) {
                furyCd = FURY_PERIOD;
                furyBolts = FURY_BOLTS;
                furyTimer = 0;
                send(target, JudgeHudPacket.COWARD, 50, 0);
                play(level, SoundEvents.WITHER_SPAWN, 2.0F, 0.6F);
                if (act == Act.CHASE || act == Act.STRAFE) {
                    setAct(Act.FURY);
                }
            }
        } else {
            farTicks = 0;
        }
    }

    private void tickFuryPose(ServerPlayer target) {
        setJudgeState(STATE_FURY);
        halt();
        lookAt(target);
        if (++actTicks >= 24) {
            setAct(Act.CHASE);
        }
    }

    // ------------------------------ utilidades ------------------------------
    private void setAct(Act next) {
        act = next;
        actTicks = 0;
    }

    private void smite(ServerLevel level, ServerPlayer p, float damage) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(p.getX(), p.getY(), p.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        p.invulnerableTime = 0;
        p.hurt(this.damageSources().indirectMagic(this, this), damage);
    }

    private void send(ServerPlayer p, int type, int a, int b) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new JudgeHudPacket(type, a, b));
    }

    private void halt() {
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(0.0D, m.y, 0.0D);
    }

    private void lookAt(Entity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        if (dx * dx + dz * dz > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
        }
        this.getLookControl().setLookAt(e, 30.0F, 30.0F);
    }

    /** Avanza en (dx,dz) a 'speed' bloques/tick; sube escalones de 2 y salta si choca. */
    private void moveDir(double dx, double dz, double speed, ServerPlayer target) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3D) {
            halt();
            return;
        }
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(dx / len * speed, m.y, dz / len * speed);
        if (this.horizontalCollision && this.onGround()) {
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x, 0.6D, v.z);
        }
    }

    /** Si lleva 2 s sin acercarse (atascado), parpadea junto al jugador. */
    private void checkStuck(ServerLevel level, ServerPlayer target, double dist) {
        if (dist <= KEEP_DISTANCE + 1.0D || act == Act.STRAFE) {
            stuckTicks = 0;
            lastDist = dist;
            return;
        }
        if (lastDist >= 0.0D && dist > lastDist - 0.02D) {
            stuckTicks++;
        } else {
            stuckTicks = Math.max(0, stuckTicks - 2);
        }
        lastDist = dist;
        if (stuckTicks >= 40) {
            stuckTicks = 0;
            blinkNear(level, target);
        }
    }

    private void blinkNear(ServerLevel level, ServerPlayer target) {
        for (int i = 0; i < 16; i++) {
            double ang = this.random.nextDouble() * Math.PI * 2.0D;
            double d = 4.0D + this.random.nextDouble() * 3.0D;
            BlockPos pos = findSurface(level, target.getX() + Math.cos(ang) * d, target.getZ() + Math.sin(ang) * d);
            if (pos == null || Math.abs(pos.getY() - target.getY()) > 6.0D) {
                continue;
            }
            AABB box = this.getDimensions(this.getPose()).makeBoundingBox(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            if (!level.noCollision(this, box)) {
                continue;
            }
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.5D, getZ(), 60, 0.8D, 2.0D, 0.8D, 0.08D);
            this.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.5D, getZ(), 60, 0.8D, 2.0D, 0.8D, 0.08D);
            play(level, SoundEvents.ILLUSIONER_MIRROR_MOVE, 3.0F, 0.4F);
            return;
        }
    }

    /** Las hojas no lo detienen: las atraviesa (solo hojas). */
    private void clearLeaves(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(0.4D, 0.2D, 0.4D);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState s = level.getBlockState(pos);
            if (s.is(BlockTags.LEAVES)) {
                level.destroyBlock(pos, false, this);
            }
        }
    }

    /** Busca superficie segura (no agua/lava, con 2 bloques de aire). Devuelve null si no sirve. */
    public static BlockPos findSurface(ServerLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(bx, 0, bz))) {
            return null;
        }
        if (level.dimensionType().hasCeiling()) {
            for (int y = Math.min(level.getMaxBuildHeight() - 2, 120); y > level.getMinBuildHeight() + 1; y--) {
                BlockPos pos = new BlockPos(bx, y, bz);
                if (isStandable(level, pos)) {
                    return pos;
                }
            }
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BlockPos pos = new BlockPos(bx, y, bz);
        return y > level.getMinBuildHeight() + 1 && isStandable(level, pos) ? pos : null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.isAir() || !below.getFluidState().isEmpty() || !below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
            return false;
        }
        for (int i = 0; i < 2; i++) {
            BlockState s = level.getBlockState(pos.above(i));
            if (!s.getFluidState().isEmpty() || !s.getCollisionShape(level, pos.above(i)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void keepChunksLoaded(ServerLevel level) {
        ChunkPos cp = new ChunkPos(this.blockPosition());
        level.getChunkSource().addRegionTicket(JUDGE_TICKET, cp, 3, cp);
    }

    public static boolean isValid(ServerPlayer p) {
        return p.isAlive() && !p.isRemoved() && !p.isSpectator()
                && p.gameMode.getGameModeForPlayer() != GameType.CREATIVE;
    }

    private ServerPlayer resolve(ServerLevel level, UUID id) {
        if (id == null) {
            return null;
        }
        Entity e = level.getEntity(id);
        return e instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    /** Su victima es fija; si nacio de un huevo/comando elige al jugador valido mas cercano. */
    private ServerPlayer acquireTarget(ServerLevel level) {
        ServerPlayer t = resolve(level, targetId);
        if (t != null && isValid(t)) {
            return t;
        }
        if (fromJudgment) {
            return null;
        }
        ServerPlayer best = null;
        double bd = 64.0D * 64.0D;
        for (ServerPlayer p : level.players()) {
            if (isValid(p) && this.distanceToSqr(p) < bd) {
                bd = this.distanceToSqr(p);
                best = p;
            }
        }
        if (best != null) {
            targetId = best.getUUID();
        }
        return best;
    }

    private void updateBossBar(ServerLevel level, ServerPlayer target) {
        bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        if (this.tickCount % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                if ((target != null && p == target) || this.distanceTo(p) <= BAR_RANGE) {
                    bossEvent.addPlayer(p);
                } else {
                    bossEvent.removePlayer(p);
                }
            }
        }
    }

    private void play(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, getX(), getY() + 3.0D, getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Asciende y desaparece (su victima se fue del mundo). */
    private void vanish(ServerLevel level) {
        JudgeBeamEntity.spawn(level, getX(), getY(), getZ(), JudgeBeamEntity.MODE_DESCENT, 30);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3.0D, getZ(), 150, 1.0D, 3.0D, 1.0D, 0.1D);
        play(level, SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.5F);
        this.discard();
    }

    /** La victima murio: la sentencia se cumplio, el Juicio se retira. */
    public void onTargetDied(ServerLevel level) {
        releaseAll(level);
        vanish(level);
    }

    private void releaseAll(ServerLevel level) {
        ServerPlayer p = resolve(level, heldId);
        if (p != null) {
            send(p, JudgeHudPacket.QTE_END, 0, 0);
            send(p, JudgeHudPacket.IMPALE, -1, 0);
        }
        heldId = null;
        ServerPlayer t = resolve(level, targetId);
        if (t != null) {
            send(t, JudgeHudPacket.DISTANCE, -1, 0);
        }
    }

    // ------------------------------ ciclo de vida ------------------------------
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel level) {
            releaseAll(level);
            ServerPlayer t = resolve(level, targetId);
            if (fromJudgment && targetId != null) {
                JudgmentEvents.onJudgeDefeated(level.getServer(), targetId);
            }
            if (t != null) {
                t.displayClientMessage(Component.translatable("message.elitebosses.judge_defeated")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
            JudgeBeamEntity.spawn(level, getX(), getY(), getZ(), JudgeBeamEntity.MODE_DESCENT, 70);
        }
        super.die(source);
    }

    /** 3 s de animacion de muerte antes de desaparecer. */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.level() instanceof ServerLevel level && this.deathTime % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.5D, getZ(), 8, 0.8D, 1.5D, 0.8D, 0.03D);
        }
        if (this.deathTime >= 60 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2.0D, getZ(), 3, 0.5D, 1.0D, 0.5D, 0.0D);
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.5D, getZ(), 200, 1.2D, 2.5D, 1.2D, 0.15D);
            }
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            bossEvent.removeAllPlayers();
            if (this.level() instanceof ServerLevel level && reason != RemovalReason.KILLED) {
                releaseAll(level);
            }
        }
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetId != null) {
            tag.putUUID("JudgeTarget", targetId);
        }
        tag.putBoolean("FromJudgment", fromJudgment);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("JudgeTarget")) {
            targetId = tag.getUUID("JudgeTarget");
        }
        fromJudgment = tag.getBoolean("FromJudgment");
        act = Act.CHASE; // al recargar el mundo no repite la llegada
        this.entityData.set(DATA_STATE, STATE_NORMAL);
    }
}
