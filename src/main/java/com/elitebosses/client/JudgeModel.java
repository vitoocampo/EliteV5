package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.JudgeEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Modelo GeckoLib del Juicio. Los anillos giran siempre (no dependen de la animacion activa). */
public class JudgeModel extends GeoModel<JudgeEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(EliteBosses.MODID, "geo/judge.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EliteBosses.MODID, "textures/entity/judge.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(EliteBosses.MODID, "animations/judge.animation.json");

    @Override
    public ResourceLocation getModelResource(JudgeEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(JudgeEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(JudgeEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(JudgeEntity animatable, long instanceId, AnimationState<JudgeEntity> state) {
        float t = (animatable.tickCount + state.getPartialTick()) / 20.0F;

        // anillos giroscopicos
        spin("haloA", 0.0F, 0.0F, t * 0.55F);
        spin("haloB", 0.0F, t * 0.8F, 0.0F);
        spin("haloC", 0.0F, 0.0F, -t * 1.6F);
        spin("staffHead", 0.0F, 0.0F, t * 0.9F);

        if (animatable.isDeadOrDying() || animatable.getJudgeState() != JudgeEntity.STATE_NORMAL) {
            return;
        }
        // la cabeza sigue a su victima (se SUMA a la animacion)
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) {
            return;
        }
        float yaw = Mth.clamp(data.netHeadYaw(), -50.0F, 50.0F) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(data.headPitch(), -35.0F, 35.0F) * Mth.DEG_TO_RAD;
        // GeckoLib: rotX positivo levanta la vista en este modelo (frente -Z); el pitch de MC es positivo hacia abajo
        add("neck", -pitch * 0.35F, yaw * 0.35F);
        add("head", -pitch * 0.65F, yaw * 0.65F);
    }

    /** Giro absoluto (estos huesos no tienen animacion): rotacion inicial + angulo. */
    private void spin(String name, float x, float y, float z) {
        CoreGeoBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) {
            bone.setRotX(bone.getInitialSnapshot().getRotX() + x);
            bone.setRotY(bone.getInitialSnapshot().getRotY() + y);
            bone.setRotZ(bone.getInitialSnapshot().getRotZ() + z);
        }
    }

    private void add(String name, float x, float y) {
        CoreGeoBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) {
            bone.setRotX(bone.getRotX() + x);
            bone.setRotY(bone.getRotY() + y);
        }
    }
}
