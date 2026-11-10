package com.elitebosses.entity;

import com.elitebosses.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Columna de luz del Juicio. Solo es visual (la dibuja JudgeBeamRenderer con el haz del faro).
 * MODE_FOLLOW: el rayo que sigue al condenado durante el minuto de Lentitud.
 * MODE_TELEGRAPH: aviso fino de la Invocacion Solar (zona 2x2).
 * MODE_STRIKE: la descarga de la Invocacion Solar.
 * MODE_DESCENT: el haz ancho por el que desciende el Juicio.
 */
public class JudgeBeamEntity extends Entity {
    public static final int MODE_FOLLOW = 0, MODE_TELEGRAPH = 1, MODE_STRIKE = 2, MODE_DESCENT = 3;

    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(JudgeBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(JudgeBeamEntity.class, EntityDataSerializers.INT);

    private int age;

    public JudgeBeamEntity(EntityType<? extends JudgeBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Crea y agrega un haz. life = ticks de vida (0 = hasta que se descarte a mano). */
    public static JudgeBeamEntity spawn(ServerLevel level, double x, double y, double z, int mode, int life) {
        JudgeBeamEntity beam = new JudgeBeamEntity(ModEntities.JUDGE_BEAM.get(), level);
        beam.setPos(x, y, z);
        beam.entityData.set(DATA_MODE, mode);
        beam.entityData.set(DATA_LIFE, life);
        level.addFreshEntity(beam);
        return beam;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_MODE, MODE_FOLLOW);
        this.entityData.define(DATA_LIFE, 0);
    }

    public int getMode() {
        return this.entityData.get(DATA_MODE);
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    public int getAge() {
        return age;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        int life = getLife();
        if (!this.level().isClientSide) {
            if (life > 0 && age >= life) {
                this.discard();
            }
            return;
        }
        // particulas en la base (solo cliente)
        int mode = getMode();
        if (mode == MODE_FOLLOW || mode == MODE_DESCENT) {
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ParticleTypes.END_ROD,
                        getX() + (this.random.nextDouble() - 0.5D) * 1.6D, getY() + this.random.nextDouble() * 0.4D,
                        getZ() + (this.random.nextDouble() - 0.5D) * 1.6D, 0.0D, 0.06D + this.random.nextDouble() * 0.1D, 0.0D);
            }
        } else if (mode == MODE_TELEGRAPH) {
            // contorno del area 2x2
            for (int i = 0; i < 3; i++) {
                double t = this.random.nextDouble() * 2.0D - 1.0D;
                double e = this.random.nextBoolean() ? 1.0D : -1.0D;
                boolean alongX = this.random.nextBoolean();
                double px = getX() + (alongX ? t : e);
                double pz = getZ() + (alongX ? e : t);
                this.level().addParticle(ParticleTypes.WAX_OFF, px, getY() + 0.05D, pz, 0.0D, 0.02D, 0.0D);
            }
        } else if (mode == MODE_STRIKE && age < 4) {
            for (int i = 0; i < 12; i++) {
                this.level().addParticle(ParticleTypes.FLASH, getX(), getY() + 0.2D, getZ(), 0.0D, 0.0D, 0.0D);
                this.level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.2D, getZ(),
                        (this.random.nextDouble() - 0.5D) * 0.6D, this.random.nextDouble() * 0.4D,
                        (this.random.nextDouble() - 0.5D) * 0.6D);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0D * 256.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
