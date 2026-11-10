package com.elitebosses.entity;

import com.elitebosses.net.HoundGrabPacket;
import com.elitebosses.net.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
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
 * El Sabueso (Houndmaster). Todo el comportamiento corre en el servidor; el cliente solo
 * dibuja el modelo, la barra de vida y la musica (ver paquetes en com.elitebosses.net).
 */
public class HoundmasterEntity extends PathfinderMob implements GeoEntity {

    // ------------------------------------------------------------------
    // AJUSTES (todo lo que puedes querer cambiar esta aqui arriba)
    // ------------------------------------------------------------------
    public static final float MAX_HP = 1400.0F;             // 700 corazones
    private static final double BASE_SPEED = 0.69D;         // bloques/tick (~13.8 b/s): velocidad en PERSECUCION (jugador a 70 bloques o menos)
    private static final double SEARCH_SPEED = 0.40D;       // bloques/tick (8 b/s): velocidad en BUSQUEDA (jugador a mas de 70 bloques)
    private static final int WOLF_LIFETIME_TICKS = 300;     // los lobos del Sabueso desaparecen a los 15 s
    private static final double BOOST_FACTOR = 2.0D;        // tras matar con otros jugadores cerca: x2 hasta el primer golpe
    public static final double SURVIVOR_RANGE = 90.0D;      // si al matar no hay jugadores a 90 bloques, desaparece
    public static final double NEAR_RANGE = 70.0D;          // ataques, velocidad y rotura instantanea
    public static final double BAR_RANGE = 200.0D;          // alcance de la barra de vida
    public static final double CHASE_MUSIC_RANGE = 70.0D;   // HoundChase suena a 70 bloques o menos
    public static final double LURK_MUSIC_RANGE = 120.0D;   // radio de terror: HoundLurk a 120 bloques o menos
    private static final double BED_RANGE = 60.0D;          // no rompe bloques cerca de una cama
    private static final int BREAK_PER_WINDOW = 10;         // bloques por ventana (lejos)
    private static final int BREAK_WINDOW_TICKS = 60;       // 3 s
    private static final float POUNCE_DAMAGE = 7.0F;        // ataque basico (Abalanzar): 7 PS = 3.5 corazones antes de armadura
    private static final float GRAB_DAMAGE_PER_SEC = 3.5F;  // por segundo
    private static final int ATTACK_PAUSE = 60;             // pausa entre ataques (3 s)
    private static final float LIFT_VELOCITY = 1.36F;       // ~10 bloques de altura
    private static final float REMATE_HP_LIMIT = 12.0F;     // menos de 6 corazones
    private static final int REMATE_SPECIALS_NEEDED = 2;    // unico enfriamiento: esperar 2 ataques especiales
    private static final float REMATE_SUCCESS = 0.65F;      // 35% de fallar
    private static final int DIG_TICKS = 100;               // Canalizacion: 5 s con la cabeza baja
    private static final int COMBAT_PERIOD = 600;           // cada 30 s en combate...
    private static final float DIG_CHANCE = 0.15F;          // ...15% de usar Canalizacion
    private static final int CANAL_WOLF_HP = 100;           // 50 corazones
    private static final int CANAL_GRAB_MIN = 30, CANAL_GRAB_MAX = 50;
    private static final double CANAL_KEEP_DISTANCE = 30.0D;
    private static final float REMATE_SELF_DAMAGE = 20.0F;
    private static final double REMATE_RANGE = 64.0D;
    private static final int WOLF_HP = 20;

    // iron: 165/240/225/195 de durabilidad / 5 s  -> el hierro se rompe a los 5 s de agarre
    private static final int WEAR_HEAD = 33, WEAR_CHEST = 48, WEAR_LEGS = 45, WEAR_FEET = 39;

    public static final int STATE_NORMAL = 0, STATE_POUNCE = 1, STATE_GROWL = 2, STATE_GRAB = 3,
            STATE_HOWL = 4, STATE_CHARGE = 5, STATE_STUN = 6, STATE_DIG = 7;

    public static final String HIT_KEY = "elitebosses_hound_hit";
    public static final String WOLF_KEY = "elitebosses_hound_wolf";
    public static final String CANAL_KEY = "elitebosses_hound_canal_wolf";

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(HoundmasterEntity.class, EntityDataSerializers.INT);

    private static final TicketType<ChunkPos> HOUND_TICKET =
            TicketType.create("elitebosses_hound", Comparator.comparingLong(ChunkPos::toLong), 60);

    private enum Act { CHASE, POUNCE, GROWL, LEVANT, GRAB, GRABBING, BACK, CAST, RETREAT, REM_HOWL, REM_CHARGE, STUN, DIG }

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.elitebosses.houndmaster"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private Act act = Act.CHASE;
    private int actTicks;
    private int cooldown = 80;
    private UUID targetId;
    private UUID grabbedId;
    private int presses, required, grabTicks;
    private UUID remateTargetId;
    private boolean remateSuccess;
    private UUID flungId;
    private int flungTicks;
    private final List<UUID> wolves = new ArrayList<>();
    private int forcedWolves = -1;
    private boolean hungerGiven;
    private long vulnerableUntil;
    private int breakBudget = BREAK_PER_WINDOW;
    private int breakWindow = BREAK_WINDOW_TICKS;
    private boolean bedNear;
    private int soundTimer = 100;
    private boolean boosted;
    private int specialsSinceRemate = REMATE_SPECIALS_NEEDED;
    private boolean remateLanded;
    private int combatTicks;
    private boolean digPending;
    private UUID canalWolfId;
    private UUID canalGrabbedId;
    private int canalPresses, canalRequired, canalGrabTicks, canalRollTimer;
    private boolean canalGrabReady;

    public HoundmasterEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
        this.bossEvent.setDarkenScreen(false);
        this.bossEvent.setPlayBossMusic(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ------------------------------------------------------------------ animaciones (GeckoLib)
    private static final String ANIM_PREFIX = "animation.houndmaster.";
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // "main": estado/locomocion. "action": dentellada puntual (va DESPUES para que sus huesos manden).
        controllers.add(new AnimationController<>(this, "main", 6, this::mainPredicate));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("bite", RawAnimation.begin().thenPlay(ANIM_PREFIX + "bite")));
    }

    private PlayState mainPredicate(AnimationState<HoundmasterEntity> state) {
        double speed = 1.0D;
        RawAnimation anim;
        switch (getHoundState()) {
            case STATE_POUNCE -> anim = RawAnimation.begin().thenPlayAndHold(ANIM_PREFIX + "pounce");
            case STATE_GROWL -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "growl");
            case STATE_GRAB -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "grab");
            case STATE_HOWL -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "howl");
            case STATE_CHARGE -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "charge");
            case STATE_STUN -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "stun");
            case STATE_DIG -> anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "dig");
            default -> {
                float amount = state.getLimbSwingAmount();
                if (amount > 0.55F) {
                    anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "run");
                    speed = Mth.clamp(0.8D + amount * 0.5D, 0.8D, 1.4D);
                } else if (state.isMoving()) {
                    anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "walk");
                    speed = Mth.clamp(0.6D + amount * 1.6D, 0.7D, 1.4D);
                } else {
                    anim = RawAnimation.begin().thenLoop(ANIM_PREFIX + "idle");
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
        this.entityData.define(DATA_STATE, STATE_NORMAL);
    }

    public int getHoundState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setHoundState(int state) {
        if (this.entityData.get(DATA_STATE) != state) {
            this.entityData.set(DATA_STATE, state);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** No puede regenerar vida de ninguna forma. */
    @Override
    public void heal(float amount) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().getGameTime() < vulnerableUntil) {
            amount *= 1.1F;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
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
        if (--soundTimer <= 0) {
            soundTimer = 200;
            howlOrBark(level);
        }
        if (--breakWindow <= 0) {
            breakWindow = BREAK_WINDOW_TICKS;
            breakBudget = BREAK_PER_WINDOW;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (this.tickCount % 20 == 0) {
            keepChunksLoaded(level);
            updateBed(level);
            manageWolves(level);
        }
        updateBossBar(level);
        if (this.tickCount % 10 == 0) {
            pickTarget(level);
        }
        handleFlung(level);
        expireWolves(level);
        tickCanalWolf(level);

        ServerPlayer target = resolve(level, targetId);
        if (target != null && this.distanceTo(target) <= NEAR_RANGE) {
            if (++combatTicks >= COMBAT_PERIOD) {
                combatTicks = 0;
                if (this.random.nextFloat() < DIG_CHANCE) {
                    digPending = true;
                }
            }
        }

        if (act == Act.STUN) {
            halt();
            setHoundState(STATE_STUN);
            if (++actTicks >= 60) {
                act = Act.CHASE;
                actTicks = 0;
                setHoundState(STATE_NORMAL);
            }
            return;
        }
        if (act == Act.GRABBING) {
            tickGrabbing(level);
            return;
        }
        if (act == Act.REM_HOWL || act == Act.REM_CHARGE) {
            tickRemate(level);
            return;
        }
        if (target == null) {
            halt();
            setHoundState(STATE_NORMAL);
            act = Act.CHASE;
            return;
        }
        this.getLookControl().setLookAt(target, 60.0F, 60.0F);

        double dist = this.distanceTo(target);
        switch (act) {
            case CHASE -> {
                setHoundState(STATE_NORMAL);
                drive(target.getX() - getX(), target.getZ() - getZ(), chaseSpeed(dist), target);
                if (dist <= NEAR_RANGE && cooldown <= 0) {
                    if (!tryRemate(level)) {
                        if (digPending && canalWolfId == null) {
                            digPending = false;
                            startDig();
                        } else {
                            chooseAttack();
                        }
                    }
                }
            }
            case POUNCE -> {
                setHoundState(STATE_POUNCE);
                drive(target.getX() - getX(), target.getZ() - getZ(), 1.35D, target);
                if (dist <= 2.6D) {
                    hit(target, POUNCE_DAMAGE, 1.1D);
                    if (act == Act.POUNCE) {
                        finish(ATTACK_PAUSE, true);
                    }
                } else if (++actTicks > 60) {
                    finish(40, false);
                }
            }
            case GROWL -> {
                setHoundState(STATE_GROWL);
                halt();
                faceTowards(target.getX() - getX(), target.getZ() - getZ());
                if (actTicks == 0) {
                    play(level, SoundEvents.WOLF_GROWL, 10.0F, 0.45F);
                }
                if (++actTicks >= 24) {
                    act = Act.LEVANT;
                    actTicks = 0;
                }
            }
            case LEVANT -> {
                setHoundState(STATE_NORMAL);
                drive(target.getX() - getX(), target.getZ() - getZ(), 1.0D, target);
                if (dist <= 2.6D) {
                    Vec3 m = target.getDeltaMovement();
                    target.setDeltaMovement(m.x * 0.2D, LIFT_VELOCITY, m.z * 0.2D);
                    target.hurtMarked = true;
                    target.getPersistentData().putLong(HIT_KEY, level.getGameTime());
                    boosted = false;
                    play(level, SoundEvents.WOLF_GROWL, 8.0F, 0.6F);
                    finish(80, true);
                } else if (++actTicks > 100) {
                    finish(40, false);
                }
            }
            case GRAB -> {
                setHoundState(STATE_POUNCE);
                drive(target.getX() - getX(), target.getZ() - getZ(), 1.1D, target);
                if (dist <= 2.4D) {
                    beginGrab(level, target);
                } else if (++actTicks > 80) {
                    finish(40, false);
                }
            }
            case BACK -> {
                ServerPlayer nearest = nearestPlayer(level);
                double nd = nearest == null ? 99.0D : this.distanceTo(nearest);
                setHoundState(STATE_NORMAL);
                if (nearest == null || nd >= 15.0D || ++actTicks > 100) {
                    act = Act.CAST;
                    actTicks = 0;
                } else {
                    drive(getX() - nearest.getX(), getZ() - nearest.getZ(), 0.9D, null);
                }
            }
            case DIG -> tickDig(level);
            case CAST -> tickCast(level, target);
            case RETREAT -> {
                setHoundState(STATE_NORMAL);
                if (dist >= 20.0D || ++actTicks > 120) {
                    forcedWolves = 2;
                    act = Act.CAST;
                    actTicks = 0;
                } else {
                    drive(getX() - target.getX(), getZ() - target.getZ(), 1.0D, null);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------ ataques ------------------------------
    private void chooseAttack() {
        actTicks = 0;
        int roll = this.random.nextInt(95); // 45 / 25 / 15 / 10 normalizado
        if (roll >= 45) {
            specialsSinceRemate++;
        }
        if (roll < 45) {
            act = Act.POUNCE;
            if (this.onGround()) {
                this.setDeltaMovement(this.getDeltaMovement().x, 0.35D, this.getDeltaMovement().z);
            }
        } else if (roll < 70) {
            act = Act.GROWL;
        } else if (roll < 85) {
            act = Act.GRAB;
        } else {
            forcedWolves = -1;
            act = Act.BACK;
        }
    }

    /** Termina un ataque. Si queda poca vida puede activar la Retirada (30%). */
    private void finish(int pause, boolean allowRetreat) {
        float frac = Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F);
        cooldown = Math.max(10, Math.round(pause * (0.35F + 0.65F * frac))); // menos vida = ataques especiales mas seguidos
        actTicks = 0;
        setHoundState(STATE_NORMAL);
        if (allowRetreat && this.getHealth() < this.getMaxHealth() * 0.4F && this.random.nextFloat() < 0.30F) {
            act = Act.RETREAT;
        } else {
            act = Act.CHASE;
        }
    }

    private void hit(ServerPlayer player, float baseDamage, double knock) {
        this.triggerAnim("action", "bite"); // dentellada (animacion GeckoLib, se sincroniza sola con los clientes)
        player.hurt(this.damageSources().mobAttack(this), scaled(baseDamage));
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        player.knockback(knock, -dx, -dz);
        player.hurtMarked = true;
        play((ServerLevel) this.level(), SoundEvents.WOLF_GROWL, 8.0F, 0.7F);
        afterDamage(player);
    }

    /** Marca el golpe y revisa si se activa el REMATE. Devuelve true si empezo. */
    private boolean afterDamage(ServerPlayer player) {
        player.getPersistentData().putLong(HIT_KEY, this.level().getGameTime());
        boosted = false; // el primer golpe termina el impulso de velocidad
        return tryRemate((ServerLevel) this.level());
    }

    /** Lanza el REMATE si hay un jugador a menos de 6 corazones y ya paso el enfriamiento. */
    private boolean tryRemate(ServerLevel level) {
        if (act == Act.REM_HOWL || act == Act.REM_CHARGE || act == Act.STUN
                || specialsSinceRemate < REMATE_SPECIALS_NEEDED) {
            return false;
        }
        for (ServerPlayer p : level.players()) {
            if (isValid(p) && p.getHealth() < REMATE_HP_LIMIT && this.distanceTo(p) <= REMATE_RANGE) {
                startRemate(level);
                return act == Act.REM_HOWL;
            }
        }
        return false;
    }

    /** BUSQUEDA (jugador a mas de NEAR_RANGE): 8 b/s fijos. PERSECUCION (a NEAR_RANGE o menos): velocidad base. */
    private double chaseSpeed(double distToTarget) {
        if (distToTarget > NEAR_RANGE) {
            return SEARCH_SPEED;
        }
        return boosted ? BASE_SPEED * BOOST_FACTOR : BASE_SPEED;
    }

    /** Los lobos que invoca el Sabueso (Manada y Canalizacion) desaparecen a los 15 s de vida. */
    private void expireWolves(ServerLevel level) {
        Iterator<UUID> it = wolves.iterator();
        while (it.hasNext()) {
            net.minecraft.world.entity.Entity e = level.getEntity(it.next());
            if (e instanceof Wolf w && w.isAlive() && w.tickCount >= WOLF_LIFETIME_TICKS) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, w.getX(), w.getY() + 0.5D, w.getZ(),
                        10, 0.3D, 0.4D, 0.3D, 0.02D);
                w.discard();
                it.remove();
            }
        }
    }

    /** Llamado por HoundEvents cuando este Sabueso mata a un jugador. */
    public void onKilledPlayer(ServerLevel level, ServerPlayer victim) {
        for (ServerPlayer p : level.players()) {
            if (p != victim && p.isAlive() && !p.isSpectator() && this.distanceTo(p) <= SURVIVOR_RANGE) {
                boosted = true;
                return;
            }
        }
        this.discard(); // sin testigos: desaparece sin soltar nada
    }

    /** Menos dano con 3-4 jugadores a 60 bloques (-10%), mas con 5 o mas (+15%). */
    private float scaled(float base) {
        int n = 0;
        for (ServerPlayer p : ((ServerLevel) this.level()).players()) {
            if (p.isAlive() && !p.isSpectator() && this.distanceTo(p) <= 60.0F) {
                n++;
            }
        }
        if (n >= 5) {
            return base * 1.15F;
        }
        if (n == 3 || n == 4) {
            return base * 0.90F;
        }
        return base;
    }

    // ------------------------------ agarre ------------------------------
    private void beginGrab(ServerLevel level, ServerPlayer player) {
        grabbedId = player.getUUID();
        presses = 0;
        required = 5 + this.random.nextInt(21); // 5 a 25
        grabTicks = 0;
        act = Act.GRABBING;
        setHoundState(STATE_GRAB);
        player.getPersistentData().putLong(HIT_KEY, level.getGameTime());
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new HoundGrabPacket(true));
        play(level, SoundEvents.WOLF_GROWL, 10.0F, 0.5F);
    }

    private void tickGrabbing(ServerLevel level) {
        ServerPlayer p = resolve(level, grabbedId);
        if (p == null || !p.isAlive() || p.isSpectator()) {
            releaseGrab(false);
            return;
        }
        halt();
        setHoundState(STATE_GRAB);
        faceTowards(p.getX() - getX(), p.getZ() - getZ());
        Vec3 f = Vec3.directionFromRotation(0.0F, this.getYRot());
        p.connection.teleport(getX() + f.x * 1.5D, getY() + 0.9D, getZ() + f.z * 1.5D, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        if (grabTicks % 10 == 0) {
            p.displayClientMessage(Component.literal("¡ESPACIO! ¡ZAFATE!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        }
        if (grabTicks % 20 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
            if (grabTicks > 0) {
                p.hurt(this.damageSources().mobAttack(this), scaled(GRAB_DAMAGE_PER_SEC));
                wearArmor(p);
                if (afterDamage(p)) {
                    releaseGrab(false);
                    return;
                }
            }
        }
        grabTicks++;
        if (presses >= required) {
            releaseGrab(true);
        }
    }

    private void wearArmor(ServerPlayer p) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int[] wear = {WEAR_HEAD, WEAR_CHEST, WEAR_LEGS, WEAR_FEET};
        for (int i = 0; i < slots.length; i++) {
            final EquipmentSlot slot = slots[i];
            ItemStack stack = p.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.hurtAndBreak(wear[i], p, e -> e.broadcastBreakEvent(slot));
            }
        }
    }

    private void releaseGrab(boolean escaped) {
        ServerPlayer p = this.level() instanceof ServerLevel sl ? resolve(sl, grabbedId) : null;
        if (p != null) {
            ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                    new HoundGrabPacket(false));
            if (escaped) {
                Vec3 away = p.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
                p.setDeltaMovement(away.x * 0.9D, 0.4D, away.z * 0.9D);
                p.hurtMarked = true;
            }
        }
        grabbedId = null;
        if (act == Act.GRABBING) {
            finish(escaped ? 100 : 60, false);
        }
    }

    /** Llamado desde el paquete que manda el cliente al pulsar ESPACIO. */
    public static void onEscapePress(ServerPlayer player) {
        for (HoundmasterEntity h : player.serverLevel().getEntitiesOfClass(
                HoundmasterEntity.class, player.getBoundingBox().inflate(128.0D))) {
            if (h.grabbedId != null && h.grabbedId.equals(player.getUUID())) {
                h.presses++;
            }
            if (h.canalGrabbedId != null && h.canalGrabbedId.equals(player.getUUID())) {
                h.canalPresses++;
            }
        }
    }

    // ------------------------------ canalizacion ------------------------------
    private void startDig() {
        specialsSinceRemate++;
        act = Act.DIG;
        actTicks = 0;
    }

    private void tickDig(ServerLevel level) {
        halt();
        setHoundState(STATE_DIG);
        if (actTicks == 0) {
            play(level, SoundEvents.WOLF_GROWL, 10.0F, 0.4F);
            for (ServerPlayer p : level.players()) {
                if (this.distanceTo(p) <= BAR_RANGE) {
                    p.displayClientMessage(Component.literal("El Sabueso escarba")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
        }
        if (actTicks % 10 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 0.2D, getZ(),
                    6, 0.8D, 0.1D, 0.8D, 0.02D);
        }
        if (++actTicks >= DIG_TICKS) {
            spawnCanalWolf(level);
            finish(80, false);
        }
    }

    private void spawnCanalWolf(ServerLevel level) {
        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf == null) {
            return;
        }
        Vec3 f = Vec3.directionFromRotation(0.0F, this.getYRot());
        wolf.moveTo(getX() + f.x * 3.0D, getY(), getZ() + f.z * 3.0D, this.getYRot(), 0.0F);
        if (!level.noCollision(wolf)) {
            wolf.moveTo(getX(), getY(), getZ(), 0.0F, 0.0F);
        }
        wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CANAL_WOLF_HP);
        wolf.setHealth(CANAL_WOLF_HP);
        wolf.setPersistenceRequired();
        wolf.getPersistentData().putUUID(WOLF_KEY, this.getUUID());
        wolf.getPersistentData().putBoolean(CANAL_KEY, true);
        wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4)); // Lentitud V por 5 s
        wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 9, false, false)); // Velocidad 10
        ServerPlayer target = nearestPlayer(level);
        if (target != null) {
            wolf.setPersistentAngerTarget(target.getUUID());
            wolf.setRemainingPersistentAngerTime(360000);
            wolf.setTarget(target);
        }
        level.addFreshEntity(wolf);
        canalWolfId = wolf.getUUID();
        canalGrabbedId = null;
        canalGrabReady = false;
        canalRollTimer = 60;
    }

    private int validPlayersNearHound(ServerLevel level) {
        int n = 0;
        for (ServerPlayer p : level.players()) {
            if (isValid(p) && this.distanceTo(p) <= 64.0F) {
                n++;
            }
        }
        return n;
    }

    /** Lobo hostil de la Canalizacion: cada 3 s, 50% de agarrar a un jugador (con 6+ corazones). */
    private void tickCanalWolf(ServerLevel level) {
        if (canalWolfId == null) {
            return;
        }
        net.minecraft.world.entity.Entity e = level.getEntity(canalWolfId);
        if (!(e instanceof Wolf wolf) || !wolf.isAlive()) {
            endCanalGrab(level, false);
            canalWolfId = null;
            return;
        }
        if (wolf.tickCount >= WOLF_LIFETIME_TICKS) { // el lobo de la Canalizacion tambien dura 15 s
            endCanalGrab(level, false);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, wolf.getX(), wolf.getY() + 0.5D, wolf.getZ(),
                    10, 0.3D, 0.4D, 0.3D, 0.02D);
            wolf.discard();
            canalWolfId = null;
            return;
        }
        if (canalGrabbedId == null) {
            if (--canalRollTimer <= 0) {
                canalRollTimer = 60;
                canalGrabReady = this.random.nextFloat() < 0.5F;
            }
            if (canalGrabReady) {
                for (ServerPlayer p : level.players()) {
                    if (isValid(p) && p.getHealth() >= REMATE_HP_LIMIT && p.distanceTo(wolf) <= 3.0F) {
                        canalGrabbedId = p.getUUID();
                        canalPresses = 0;
                        canalRequired = CANAL_GRAB_MIN + this.random.nextInt(CANAL_GRAB_MAX - CANAL_GRAB_MIN + 1);
                        canalGrabTicks = 0;
                        canalGrabReady = false;
                        wolf.setTarget(null);
                        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                                new HoundGrabPacket(true));
                        level.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.WOLF_GROWL,
                                SoundSource.HOSTILE, 6.0F, 0.6F);
                        break;
                    }
                }
            }
            return;
        }
        ServerPlayer victim = resolve(level, canalGrabbedId);
        if (victim == null || !isValid(victim) || victim.getHealth() < REMATE_HP_LIMIT
                || canalPresses >= canalRequired || ++canalGrabTicks > 600) {
            endCanalGrab(level, canalPresses >= canalRequired);
            return;
        }
        // el lobo sujeta al jugador y se mueve
        wolf.setTarget(null);
        wolf.getNavigation().stop();
        Vec3 goal;
        double hx = getX(), hz = getZ();
        double dx = wolf.getX() - hx, dz = wolf.getZ() - hz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (validPlayersNearHound(level) <= 1) {
            goal = dist > 3.0D ? new Vec3(hx, wolf.getY(), hz) : null;       // solo uno: lo arrastra hacia el Sabueso
        } else {
            double ux = dist < 1.0E-3D ? 1.0D : dx / dist, uz = dist < 1.0E-3D ? 0.0D : dz / dist;
            goal = Math.abs(dist - CANAL_KEEP_DISTANCE) > 1.5D
                    ? new Vec3(hx + ux * CANAL_KEEP_DISTANCE, wolf.getY(), hz + uz * CANAL_KEEP_DISTANCE) : null; // varios: lo aleja y lo deja a 30 bloques
        }
        Vec3 m = wolf.getDeltaMovement();
        if (goal != null) {
            double gx = goal.x - wolf.getX(), gz = goal.z - wolf.getZ();
            double gl = Math.sqrt(gx * gx + gz * gz);
            if (gl > 1.0E-3D) {
                double sp = Math.min(0.6D, gl);
                wolf.setDeltaMovement(gx / gl * sp, m.y, gz / gl * sp);
                wolf.setYRot((float) (Mth.atan2(gz, gx) * (180.0D / Math.PI)) - 90.0F);
                if (wolf.horizontalCollision && wolf.onGround()) {
                    wolf.setDeltaMovement(wolf.getDeltaMovement().x, 0.5D, wolf.getDeltaMovement().z);
                }
            }
        } else {
            wolf.setDeltaMovement(0.0D, m.y, 0.0D);
        }
        victim.connection.teleport(wolf.getX(), wolf.getY() + 0.9D, wolf.getZ(), victim.getYRot(), victim.getXRot());
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0F;
        if (canalGrabTicks % 10 == 0) {
            victim.displayClientMessage(Component.literal("¡ESPACIO! ¡ZAFATE!")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        }
    }

    private void endCanalGrab(ServerLevel level, boolean escaped) {
        if (canalGrabbedId == null) {
            return;
        }
        ServerPlayer p = resolve(level, canalGrabbedId);
        if (p != null) {
            ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                    new HoundGrabPacket(false));
        }
        canalGrabbedId = null;
        canalRollTimer = 60;
    }

    // ------------------------------ manada ------------------------------
    private void tickCast(ServerLevel level, ServerPlayer target) {
        setHoundState(STATE_HOWL);
        halt();
        faceTowards(target.getX() - getX(), target.getZ() - getZ());
        if (actTicks == 0) {
            play(level, SoundEvents.WOLF_WHINE, 12.0F, 0.4F);
        }
        if (actTicks == 25) {
            int count = forcedWolves > 0 ? forcedWolves : 2 + this.random.nextInt(4); // 2 a 5
            forcedWolves = -1;
            summonWolves(level, count);
            vulnerableUntil = level.getGameTime() + 100L; // +10% de dano por 5 s
        }
        if (++actTicks >= 45) {
            finish(80, false);
        }
    }

    private void summonWolves(ServerLevel level, int count) {
        ServerPlayer target = nearestPlayer(level);
        hungerGiven = false;
        for (int i = 0; i < count; i++) {
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf == null) {
                continue;
            }
            double ang = this.random.nextDouble() * Math.PI * 2.0D;
            double r = 3.0D + this.random.nextDouble() * 2.0D;
            double x = getX() + Math.cos(ang) * r;
            double z = getZ() + Math.sin(ang) * r;
            wolf.moveTo(x, getY(), z, this.random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(wolf)) {
                wolf.moveTo(getX(), getY(), getZ(), 0.0F, 0.0F);
            }
            wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(WOLF_HP);
            wolf.setHealth(WOLF_HP);
            wolf.setPersistenceRequired();
            wolf.getPersistentData().putUUID(WOLF_KEY, this.getUUID());
            if (target != null) {
                wolf.setPersistentAngerTarget(target.getUUID());
                wolf.setRemainingPersistentAngerTime(36000);
                wolf.setTarget(target);
            }
            level.addFreshEntity(wolf);
            wolves.add(wolf.getUUID());
        }
    }

    private void manageWolves(ServerLevel level) {
        List<Wolf> alive = new ArrayList<>();
        Iterator<UUID> it = wolves.iterator();
        while (it.hasNext()) {
            net.minecraft.world.entity.Entity e = level.getEntity(it.next());
            if (e instanceof Wolf w && w.isAlive()) {
                alive.add(w);
            } else {
                it.remove();
            }
        }
        int n = alive.size();
        if (n == 0) {
            return;
        }
        if (n <= 2) {
            for (Wolf w : alive) {
                w.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6.0D); // 3 corazones
                w.setHealth(Math.min(w.getHealth(), 6.0F));
                w.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 5));   // Velocidad VI
                w.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1));     // Fuerza II
            }
        } else if (n <= 3) {
            for (Wolf w : alive) {
                w.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0D); // 5 corazones
                w.setHealth(Math.min(w.getHealth(), 10.0F));
                w.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 3));   // Velocidad IV
            }
        }
        if (n <= 3 && !hungerGiven) {
            hungerGiven = true;
            for (ServerPlayer p : level.players()) {
                if (p.isAlive() && this.distanceTo(p) <= 64.0F) {
                    p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 3));      // Hambre IV
                }
            }
        }
    }

    // ------------------------------ remate ------------------------------
    private void startRemate(ServerLevel level) {
        ServerPlayer best = null;
        for (ServerPlayer p : level.players()) {
            if (!isValid(p) || this.distanceTo(p) > REMATE_RANGE) {
                continue;
            }
            if (best == null || p.getHealth() < best.getHealth()) {
                best = p;
            }
        }
        if (best == null) {
            return;
        }
        if (act == Act.GRABBING) {
            releaseGrab(false);
        }
        remateTargetId = best.getUUID();
        specialsSinceRemate = 0;
        remateLanded = false;
        remateSuccess = this.random.nextFloat() < REMATE_SUCCESS; // se decide ahora; no se puede cancelar
        act = Act.REM_HOWL;
        actTicks = 0;
    }

    private void tickRemate(ServerLevel level) {
        ServerPlayer rt = resolve(level, remateTargetId);
        if (rt == null || !rt.isAlive()) {
            finish(80, false);
            return;
        }
        faceTowards(rt.getX() - getX(), rt.getZ() - getZ());
        if (act == Act.REM_HOWL) {
            setHoundState(STATE_HOWL);
            halt();
            if (actTicks % 20 == 0) {
                play(level, SoundEvents.WOLF_WHINE, 14.0F, 0.3F);
            }
            if (++actTicks >= 50) {
                act = Act.REM_CHARGE;
                actTicks = 0;
            }
            return;
        }
        setHoundState(STATE_CHARGE);
        drive(rt.getX() - getX(), rt.getZ() - getZ(), 1.7D, rt);
        if (this.distanceTo(rt) <= 2.6D) {
            resolveRemate(level, rt);
        } else if (++actTicks > 90) {
            finish(80, false);
        }
    }

    private void resolveRemate(ServerLevel level, ServerPlayer rt) {
        if (!remateSuccess) {
            remateFails(level);
            return;
        }
        if (rt.isBlocking()) {
            // el escudo aguanta: se rompe y el jugador queda a 1 corazon
            ItemStack shield = rt.getUseItem();
            if (!shield.isEmpty()) {
                shield.shrink(1);
            }
            rt.stopUsingItem();
            level.broadcastEntityEvent(rt, (byte) 30);
            rt.setAbsorptionAmount(0.0F);
            rt.setHealth(2.0F);
            level.playSound(null, rt.getX(), rt.getY(), rt.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            finish(80, false);
            return;
        }
        Vec3 dir = rt.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        rt.setDeltaMovement(dir.x * 4.0D, 0.25D, dir.z * 4.0D);
        rt.hurtMarked = true;
        flungId = rt.getUUID();
        flungTicks = 0;
        rt.getPersistentData().putLong(HIT_KEY, level.getGameTime());
        play(level, SoundEvents.WOLF_GROWL, 12.0F, 0.4F);
        finish(80, false);
    }

    private void remateFails(ServerLevel level) {
        act = Act.STUN;
        actTicks = 0;
        setHoundState(STATE_STUN);
        this.invulnerableTime = 0;
        this.hurt(this.damageSources().generic(), REMATE_SELF_DAMAGE);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_HURT, SoundSource.HOSTILE, 10.0F, 0.4F);
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("El Sabueso sufre 20 de daño").withStyle(ChatFormatting.GRAY)));
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("REMATE FALLIDO").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        }
    }

    /** REMATE ASESTADO: todos los jugadores a 64 bloques de la victima sufren efectos. */
    private void remateAftermath(ServerLevel level, ServerPlayer victim) {
        for (ServerPlayer o : level.players()) {
            if (o == victim || !o.isAlive() || o.distanceTo(victim) > REMATE_RANGE) {
                continue;
            }
            o.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 20, 0));
            o.addEffect(new MobEffectInstance(MobEffects.HUNGER, 7 * 20, 9));
            o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 * 20, 0));
            o.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 7 * 20, 0));
            o.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 20));
            o.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("REMATE ASESTADO").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }
    }

    /** El jugador empujado por el Remate muere si choca con una estructura. */
    private void handleFlung(ServerLevel level) {
        if (flungId == null) {
            return;
        }
        ServerPlayer p = resolve(level, flungId);
        if (p == null || !p.isAlive() || ++flungTicks > 50) {
            flungId = null;
            return;
        }
        if (flungTicks >= 3 && (p.horizontalCollision || (p.verticalCollision && p.getDeltaMovement().y > 0.0D))) {
            flungId = null;
            remateAftermath(level, p);
            p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    // ------------------------------ movimiento ------------------------------
    private void halt() {
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(0.0D, m.y, 0.0D);
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

    /** Avanza en linea recta (sin pathfinding) hacia (dx,dz) a 'speed' bloques por tick. */
    private void drive(double dx, double dz, double speed, ServerPlayer target) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3D) {
            halt();
            return;
        }
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(dx / len * speed, m.y, dz / len * speed);
        faceTowards(dx, dz);
        if (this.horizontalCollision) {
            if (this.onGround()) {
                Vec3 v = this.getDeltaMovement();
                this.setDeltaMovement(v.x, 0.55D, v.z);
            }
            breakAhead(dx / len, dz / len, target != null && target.getY() > getY() + 1.5D);
        }
    }

    private void breakAhead(double fx, double fz, boolean targetAbove) {
        if (bedNear || !(this.level() instanceof ServerLevel level)
                || !ForgeEventFactory.getMobGriefingEvent(level, this)) {
            return;
        }
        boolean near = this.nearTarget();
        if (!near && breakBudget <= 0) {
            return;
        }
        AABB box = this.getBoundingBox().move(fx * 1.0D, 0.0D, fz * 1.0D).inflate(0.5D, 0.0D, 0.5D);
        if (targetAbove) {
            box = box.expandTowards(0.0D, 1.5D, 0.0D);
        }
        int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY), maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = level.getBlockState(pos);
            if (!isNatural(level, pos, state)) {
                continue;
            }
            level.destroyBlock(pos, false, this);
            if (!near) {
                breakBudget--;
                if (breakBudget <= 0) {
                    return;
                }
            }
        }
    }

    private boolean nearTarget() {
        if (this.level() instanceof ServerLevel level) {
            ServerPlayer t = resolve(level, targetId);
            return t != null && this.distanceTo(t) <= NEAR_RANGE;
        }
        return false;
    }

    /** Lista de bloques "naturales" (el juego no distingue entre naturales y colocados). */
    private static boolean isNatural(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.isAir() || s.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.SAND) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SNOW)
                || s.is(BlockTags.ICE) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.NYLIUM)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Tags.Blocks.ORES)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.COBWEB) || s.is(Blocks.VINE) || s.is(Blocks.GRASS)
                || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN) || s.is(Blocks.DEAD_BUSH)
                || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)
                || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.CALCITE)
                || s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.MAGMA_BLOCK);
    }

    private void updateBed(ServerLevel level) {
        bedNear = level.getPoiManager()
                .getInRange(h -> h.is(PoiTypes.HOME), this.blockPosition(), (int) BED_RANGE, PoiManager.Occupancy.ANY)
                .findAny().isPresent();
    }

    private void keepChunksLoaded(ServerLevel level) {
        ChunkPos cp = new ChunkPos(this.blockPosition());
        level.getChunkSource().addRegionTicket(HOUND_TICKET, cp, 3, cp);
    }

    // ------------------------------ objetivos ------------------------------
    public static boolean isValid(ServerPlayer p) {
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

    private void pickTarget(ServerLevel level) {
        ServerPlayer current = resolve(level, targetId);
        if (current != null && isValid(current) && act != Act.CHASE) {
            return; // no cambia de victima a medio ataque
        }
        ServerPlayer best = nearestPlayer(level);
        targetId = best == null ? null : best.getUUID();
    }

    private void updateBossBar(ServerLevel level) {
        bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        if (this.tickCount % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                if (this.distanceTo(p) <= BAR_RANGE) {
                    bossEvent.addPlayer(p);
                } else {
                    bossEvent.removePlayer(p);
                }
            }
        }
    }

    // ------------------------------ sonidos ------------------------------
    private void howlOrBark(ServerLevel level) {
        if (this.random.nextBoolean()) {
            play(level, SoundEvents.WOLF_WHINE, 10.0F, 0.5F);   // aullido grave
        } else {
            play(level, SoundEvents.WOLF_AMBIENT, 10.0F, 0.6F); // ladrido
        }
    }

    private void play(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ------------------------------ ciclo de vida ------------------------------
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel level) {
            releaseGrab(false);
            for (UUID id : wolves) {
                net.minecraft.world.entity.Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            wolves.clear();
            discardCanalWolf(level);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            bossEvent.removeAllPlayers();
            if (this.level() instanceof ServerLevel level) {
                ServerPlayer p = resolve(level, grabbedId);
                if (p != null) {
                    ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                            new HoundGrabPacket(false));
                }
                ServerPlayer cp = resolve(level, canalGrabbedId);
                if (cp != null) {
                    ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> cp),
                            new HoundGrabPacket(false));
                }
                if (reason == RemovalReason.DISCARDED) {
                    discardCanalWolf(level);
                    for (UUID id : wolves) {
                        net.minecraft.world.entity.Entity e = level.getEntity(id);
                        if (e != null) {
                            e.discard();
                        }
                    }
                }
            }
        }
        super.remove(reason);
    }

    private void discardCanalWolf(ServerLevel level) {
        if (canalWolfId != null) {
            net.minecraft.world.entity.Entity e = level.getEntity(canalWolfId);
            if (e != null) {
                e.discard();
            }
            canalWolfId = null;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}
