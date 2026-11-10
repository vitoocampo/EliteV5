package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.HoundmasterEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Modelo GeckoLib del Sabueso: geo + animaciones + textura en assets/elitebosses. */
public class HoundmasterModel extends GeoModel<HoundmasterEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(EliteBosses.MODID, "geo/houndmaster.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EliteBosses.MODID, "textures/entity/houndmaster.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(EliteBosses.MODID, "animations/houndmaster.animation.json");

    @Override
    public ResourceLocation getModelResource(HoundmasterEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(HoundmasterEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(HoundmasterEntity animatable) {
        return ANIMATION;
    }

    /** Sigue con la cabeza al objetivo (se SUMA a la animacion, no la reemplaza). */
    @Override
    public void setCustomAnimations(HoundmasterEntity animatable, long instanceId, AnimationState<HoundmasterEntity> state) {
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) {
            return;
        }
        float k = animatable.getHoundState() == HoundmasterEntity.STATE_NORMAL ? 1.0F : 0.3F;
        float yaw = Mth.clamp(data.netHeadYaw(), -55.0F, 55.0F) * Mth.DEG_TO_RAD * k;
        float pitch = Mth.clamp(data.headPitch(), -35.0F, 35.0F) * Mth.DEG_TO_RAD * k;
        addRot("neck", pitch * 0.4F, yaw * 0.4F);
        addRot("head", pitch * 0.6F, yaw * 0.6F);
    }

    private void addRot(String name, float x, float y) {
        CoreGeoBone bone = getAnimationProcessor().getBone(name);
        if (bone != null) {
            bone.setRotX(bone.getRotX() + x);
            bone.setRotY(bone.getRotY() + y);
        }
    }
}
