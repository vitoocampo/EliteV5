package com.elitebosses.registry;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.ChocolateEntity;
import com.elitebosses.entity.HoundmasterEntity;
import com.elitebosses.entity.JudgeBeamEntity;
import com.elitebosses.entity.JudgeEntity;
import com.elitebosses.entity.StalkerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EliteBosses.MODID);

    public static final RegistryObject<EntityType<ChocolateEntity>> CHOCOLATE = ENTITIES.register("chocolate",
            () -> EntityType.Builder.of(ChocolateEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.9F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(EliteBosses.MODID, "chocolate").toString()));

    public static final RegistryObject<EntityType<HoundmasterEntity>> HOUNDMASTER = ENTITIES.register("houndmaster",
            () -> EntityType.Builder.of(HoundmasterEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 1.98F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build(new ResourceLocation(EliteBosses.MODID, "houndmaster").toString()));

    public static final RegistryObject<EntityType<StalkerEntity>> STALKER = ENTITIES.register("stalker",
            () -> EntityType.Builder.of(StalkerEntity::new, MobCategory.MONSTER)
                    .sized(1.6F, 3.0F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .fireImmune()
                    .build(new ResourceLocation(EliteBosses.MODID, "stalker").toString()));

    /** El Juicio: ~7 bloques de alto (modelo); la caja de choque es mas baja para que pueda moverse. */
    public static final RegistryObject<EntityType<JudgeEntity>> JUDGE = ENTITIES.register("judge",
            () -> EntityType.Builder.of(JudgeEntity::new, MobCategory.MONSTER)
                    .sized(1.7F, 5.4F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build(new ResourceLocation(EliteBosses.MODID, "judge").toString()));

    /** Rayo/columna de luz del Juicio (no se guarda en el mundo). */
    public static final RegistryObject<EntityType<JudgeBeamEntity>> JUDGE_BEAM = ENTITIES.register("judge_beam",
            () -> EntityType.Builder.<JudgeBeamEntity>of(JudgeBeamEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .build(new ResourceLocation(EliteBosses.MODID, "judge_beam").toString()));

    private ModEntities() {
    }
}
