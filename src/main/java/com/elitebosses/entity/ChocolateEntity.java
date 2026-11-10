package com.elitebosses.entity;

import com.elitebosses.events.ChocoScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class ChocolateEntity extends PathfinderMob {

    public enum State { PHASE1, HUNT, RETREAT, WAIT, KEEP_DISTANCE, FOLLOW_SLOW, FOLLOW_FAST, KILL, RAGE, FLEE }

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(ChocolateEntity.class, EntityDataSerializers.INT);

    // ------------------------------------------------------------------
    // Ajustes. Las velocidades estan expresadas como "atributo equivalente
    // de jugador" (jugador corriendo = 0.13). Ver applySpeed().
    // ------------------------------------------------------------------
    public static final double MUSIC_RANGE = 32.0D;
    public static final double COOKIE_RANGE = 32.0D;

    private static final double DETECT_RANGE = 32.0D;
    private static final double FAR_RANGE = 48.0D;

    private static final float PLAYER_SPRINT = 0.13F;
    private static final float PLAYER_SPEED2_SPRINT = 0.182F; // 0.1 * 1.4 (Speed II) * 1.3 (correr)

    // Fase 1
    private static final float P1_BASE_FACTOR = 0.95F;     // un poco mas lento que el jugador
    private static final float P1_SPEED_GROWTH = 1.20F;    // +20% por golpe
    private static final float P1_BASE_DAMAGE = 4.0F;      // 2 corazones
    private static final float P1_DAMAGE_GROWTH = 1.0F;    // +1 de dano por golpe
    private static final int P1_BASE_COOLDOWN = 40;        // 2 s
    private static final int MIN_COOLDOWN = 4;             // piso: 0.2 s

    // Disparadores
    private static final float PHASE2_PLAYER_HP = 10.0F;   // 5 corazones o menos
    private static final float PHASE2_MOB_HP = 50.0F;
    private static final float RAGE_MOB_HP = 10.0F;

    // Fase 2
    private static final float RETREAT_SPEED = 0.40F;
    private static final double RETREAT_DISTANCE = 15.0D;
    private static final float SLOW_FACTOR = 0.94F;
    private static final float FAST_FACTOR = 1.06F;
    private static final float KILL_FACTOR = 1.20F;
    private static final float RAGE_FACTOR = 2.0F;

    private static final int SPEED_TOTAL_TICKS = 270 * 20;  // 4:30
    private static final int WAIT_TICKS = 5 * 20;
    private static final int KEEP_DISTANCE_TICKS = 10 * 20;
    private static final int SLOW_TICKS = 60 * 20;
    private static final int KILL_PHASE_TICKS = 30 * 20;
    private static final float KILL_DAMAGE = 6.0F;         // 3 corazones
    private static final int KILL_COOLDOWN = 20;           // 1 s

    // ------------------------------------------------------------------
    private State state = State.PHASE1;
    private UUID victimId;
    private int attackCount;
    private int attackCooldown;
    private int stateTicks;
    private boolean rage;
    private boolean speedActive;
    private long speedStart;

    public ChocolateEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, 1);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean isTargeting(Player player) {
        return victimId != null && victimId.equals(player.getUUID());
    }

    public boolean isGrantingSpeedTo(Player player) {
        return speedActive && victimId != null && victimId.equals(player.getUUID());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    // ------------------------------------------------------------------
    // Cerebro (solo servidor)
    // ------------------------------------------------------------------
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();

        float hp = this.getHealth();
        if (state == State.PHASE1 && hp <= PHASE2_MOB_HP) {
            enterPhase2();
        }
        if (!rage && hp <= RAGE_MOB_HP) {
            enterRage();
        }

        if (speedActive && now - speedStart >= SPEED_TOTAL_TICKS) {
            vanishNaturally(level);
            return;
        }

        ServerPlayer victim = resolveVictim(level);
        if (victim == null) {
            this.getNavigation().stop();
            return;
        }

        if (speedActive && this.tickCount % 10 == 0 && !victim.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            vanishNaturally(level);
            return;
        }

        switch (state) {
            case PHASE1 -> tickPhase1(victim);
            case HUNT -> tickHunt(victim);
            case RETREAT -> tickRetreat(victim, now);
            case WAIT, KEEP_DISTANCE, FOLLOW_SLOW, FOLLOW_FAST, KILL -> tickTimeline(victim, now);
            case RAGE -> tickRage(victim, now);
            case FLEE -> tickFlee(victim);
        }
    }

    private ServerPlayer resolveVictim(ServerLevel level) {
        ServerPlayer current = null;
        if (victimId != null) {
            current = level.getServer().getPlayerList().getPlayer(victimId);
            if (current == null || !current.isAlive() || current.level() != level) {
                resetToPhase1();
                current = null;
            }
        }
        if (current != null && (speedActive || current.distanceToSqr(this) <= FAR_RANGE * FAR_RANGE)) {
            return current;
        }
        double search = state == State.PHASE1 ? DETECT_RANGE : FAR_RANGE;
        Player nearest = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), search,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        if (nearest instanceof ServerPlayer sp) {
            victimId = sp.getUUID();
            return sp;
        }
        victimId = null;
        return null;
    }

    private void resetToPhase1() {
        state = State.PHASE1;
        this.entityData.set(DATA_PHASE, 1);
        victimId = null;
        attackCount = 0;
        attackCooldown = 0;
        stateTicks = 0;
        rage = false;
        speedActive = false;
        speedStart = 0L;
    }

    private void enterPhase2() {
        if (state != State.PHASE1) {
            return;
        }
        state = State.HUNT;
        this.entityData.set(DATA_PHASE, 2);
        attackCount = 0;
        attackCooldown = 0;
    }

    private void enterRage() {
        rage = true;
        if (state == State.FLEE) {
            return;
        }
        state = State.RAGE;
        this.entityData.set(DATA_PHASE, 2);
        attackCooldown = 0;
    }

    // ---------------------------- FASE 1 ------------------------------
    private void tickPhase1(ServerPlayer victim) {
        if (victim.getHealth() <= PHASE2_PLAYER_HP) {
            enterPhase2();
            return;
        }
        float speed = PLAYER_SPRINT * P1_BASE_FACTOR * (float) Math.pow(P1_SPEED_GROWTH, attackCount);
        chase(victim, speed);
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (attackCooldown <= 0 && canReach(victim)) {
            dealExactDamage(victim, P1_BASE_DAMAGE + P1_DAMAGE_GROWTH * attackCount);
            attackCount++;
            attackCooldown = Math.max(MIN_COOLDOWN,
                    Math.round(P1_BASE_COOLDOWN * (float) Math.pow(0.5D, attackCount)));
            if (victim.isAlive() && victim.getHealth() <= PHASE2_PLAYER_HP) {
                enterPhase2();
            }
        }
    }

    // ---------------------------- FASE 2 ------------------------------
    private void tickHunt(ServerPlayer victim) {
        chase(victim, PLAYER_SPEED2_SPRINT);
        if (canReach(victim)) {
            setToOneHeart(victim);
            state = State.RETREAT;
            stateTicks = 0;
        }
    }

    private void tickRetreat(ServerPlayer victim, long now) {
        stateTicks++;
        applySpeed(RETREAT_SPEED);
        if (this.distanceTo(victim) >= RETREAT_DISTANCE || stateTicks > 100) {
            this.getNavigation().stop();
            grantSpeed(victim, now);
            state = State.WAIT;
            return;
        }
        if (stateTicks % 8 == 1 || this.getNavigation().isDone()) {
            runAwayFrom(victim);
        }
    }

    private void tickTimeline(ServerPlayer victim, long now) {
        long t = now - speedStart;
        if (t < WAIT_TICKS) {
            state = State.WAIT;
        } else if (t < WAIT_TICKS + KEEP_DISTANCE_TICKS) {
            state = State.KEEP_DISTANCE;
        } else if (t < WAIT_TICKS + KEEP_DISTANCE_TICKS + SLOW_TICKS) {
            state = State.FOLLOW_SLOW;
        } else if (t < SPEED_TOTAL_TICKS - KILL_PHASE_TICKS) {
            state = State.FOLLOW_FAST;
        } else {
            state = State.KILL;
        }

        switch (state) {
            case WAIT -> {
                this.getNavigation().stop();
                this.getLookControl().setLookAt(victim, 30.0F, 30.0F);
            }
            case KEEP_DISTANCE -> {
                float dist = this.distanceTo(victim);
                if (dist < 7.0F) {
                    applySpeed(PLAYER_SPEED2_SPRINT * SLOW_FACTOR);
                    if (this.tickCount % 8 == 0 || this.getNavigation().isDone()) {
                        runAwayFrom(victim);
                    }
                } else if (dist > 9.0F) {
                    chase(victim, PLAYER_SPEED2_SPRINT * SLOW_FACTOR);
                } else {
                    this.getNavigation().stop();
                    this.getLookControl().setLookAt(victim, 30.0F, 30.0F);
                }
            }
            case FOLLOW_SLOW -> follow(victim, SLOW_FACTOR);
            case FOLLOW_FAST -> follow(victim, FAST_FACTOR);
            case KILL -> chase(victim, PLAYER_SPEED2_SPRINT * KILL_FACTOR);
            default -> { }
        }

        // En toda la fase 2 ataca apenas te alcance (3 corazones, 1 s de espera entre golpes)
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (attackCooldown <= 0 && canReach(victim)) {
            dealExactDamage(victim, KILL_DAMAGE);
            attackCooldown = KILL_COOLDOWN;
        }
    }

    private void follow(ServerPlayer victim, float factor) {
        chase(victim, PLAYER_SPEED2_SPRINT * factor);
    }

    // ----------------------- FURIA (vida <= 10) -----------------------
    private void tickRage(ServerPlayer victim, long now) {
        chase(victim, PLAYER_SPEED2_SPRINT * RAGE_FACTOR);
        if (canReach(victim)) {
            setToOneHeart(victim);
            if (!speedActive) {
                grantSpeed(victim, now);
            }
            state = State.FLEE;
        }
    }

    private void tickFlee(ServerPlayer victim) {
        applySpeed(PLAYER_SPEED2_SPRINT * RAGE_FACTOR);
        if (this.tickCount % 8 == 0 || this.getNavigation().isDone()) {
            runAwayFrom(victim);
        }
    }

    // ----------------------------- Ayudas ------------------------------
    /**
     * En un mob el atributo de velocidad entra dos veces (impulso de avance y
     * aceleracion), asi que la velocidad real crece con su cuadrado. Para igualar
     * a un jugador con atributo p, el mob necesita raiz(p).
     */
    private void applySpeed(float playerEquivalent) {
        AttributeInstance attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.setBaseValue(Math.sqrt(playerEquivalent));
        }
    }

    private void chase(LivingEntity target, float playerEquivalent) {
        applySpeed(playerEquivalent);
        if (this.tickCount % 4 == 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(target, 1.0D);
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void runAwayFrom(LivingEntity from) {
        Vec3 pos = DefaultRandomPos.getPosAway(this, 16, 7, from.position());
        if (pos != null) {
            this.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.0D);
        }
    }

    private boolean canReach(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double reach = this.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + 1.1D;
        return horizontal <= reach && Math.abs(target.getY() - this.getY()) < 2.0D;
    }

    private void grantSpeed(ServerPlayer player, long now) {
        speedActive = true;
        speedStart = now;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_TOTAL_TICKS, 1, false, true, true));
    }

    /** Quita vida exacta, ignorando armadura y encantamientos. */
    private void dealExactDamage(ServerPlayer player, float amount) {
        float remaining = player.getHealth() - amount;
        if (remaining <= 0.0F) {
            player.hurt(this.damageSources().magic(), Float.MAX_VALUE);
            return;
        }
        player.setHealth(remaining);
        afterHit(player);
    }

    /** Deja al jugador en 1 corazon, tenga la vida maxima que tenga. */
    private void setToOneHeart(ServerPlayer player) {
        player.setAbsorptionAmount(0.0F);
        if (player.getHealth() > 2.0F) {
            player.setHealth(2.0F);
        }
        afterHit(player);
    }

    private void afterHit(ServerPlayer player) {
        this.level().broadcastEntityEvent(player, (byte) 2);
        player.knockback(0.4D, this.getX() - player.getX(), this.getZ() - player.getZ());
        player.hurtMarked = true;
        this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
    }

    private void vanishNaturally(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.9D, this.getZ(),
                20, 0.4D, 0.6D, 0.4D, 0.02D);
        this.discard();
    }

    /** Galleta o leche: desaparece al instante con gritos de ghast y sin soltar nada. */
    public void vanishWithGhasts() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        final double x = this.getX();
        final double y = this.getY() + 0.9D;
        final double z = this.getZ();
        final SoundEvent[] pool = {
                SoundEvents.GHAST_SCREAM, SoundEvents.GHAST_WARN, SoundEvents.GHAST_HURT,
                SoundEvents.GHAST_SCREAM, SoundEvents.GHAST_DEATH, SoundEvents.GHAST_WARN,
                SoundEvents.GHAST_SCREAM, SoundEvents.GHAST_HURT
        };
        for (int i = 0; i < pool.length; i++) {
            final SoundEvent sound = pool[i];
            final float pitch = 0.85F + level.random.nextFloat() * 0.35F;
            ChocoScheduler.runLater(i * 6, () ->
                    level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, 5.0F, pitch));
        }
        level.sendParticles(ParticleTypes.POOF, x, y, z, 25, 0.4D, 0.6D, 0.4D, 0.03D);
        this.discard();
    }

    // ------------------------------ NBT -------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ChocoState", state.name());
        tag.putInt("ChocoAttackCount", attackCount);
        tag.putBoolean("ChocoRage", rage);
        tag.putBoolean("ChocoSpeedActive", speedActive);
        tag.putLong("ChocoSpeedStart", speedStart);
        if (victimId != null) {
            tag.putUUID("ChocoVictim", victimId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            state = State.valueOf(tag.getString("ChocoState"));
        } catch (IllegalArgumentException ex) {
            state = State.PHASE1;
        }
        attackCount = tag.getInt("ChocoAttackCount");
        rage = tag.getBoolean("ChocoRage");
        speedActive = tag.getBoolean("ChocoSpeedActive");
        speedStart = tag.getLong("ChocoSpeedStart");
        victimId = tag.hasUUID("ChocoVictim") ? tag.getUUID("ChocoVictim") : null;
        this.entityData.set(DATA_PHASE, state == State.PHASE1 ? 1 : 2);
    }
}
