package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.StalkerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Modelo GeckoLib de la Krasue. */
public class StalkerModel extends GeoModel<StalkerEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(EliteBosses.MODID, "geo/stalker.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EliteBosses.MODID, "textures/entity/stalker.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(EliteBosses.MODID, "animations/stalker.animation.json");

    @Override
    public ResourceLocation getModelResource(StalkerEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(StalkerEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(StalkerEntity animatable) {
        return ANIMATION;
    }

    /** La cabeza flotante mira hacia donde mira la entidad (se suma a la animacion). */
    @Override
    public void setCustomAnimations(StalkerEntity animatable, long instanceId, AnimationState<StalkerEntity> state) {
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null || animatable.isFinishing()) {
            return;
        }
        float yaw = Mth.clamp(data.netHeadYaw(), -60.0F, 60.0F) * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(data.headPitch(), -40.0F, 40.0F) * Mth.DEG_TO_RAD;
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        if (head != null) {
            head.setRotX(head.getRotX() + pitch * 0.5F);
            head.setRotY(head.getRotY() + yaw * 0.7F);
        }
        CoreGeoBone neck = getAnimationProcessor().getBone("neck");
        if (neck != null) {
            neck.setRotY(neck.getRotY() + yaw * 0.3F);
        }
    }
}
