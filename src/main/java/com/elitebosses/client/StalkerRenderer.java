package com.elitebosses.client;

import com.elitebosses.entity.StalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {
    public StalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new StalkerModel());
        this.shadowRadius = 0.0F;
        // ojos y corazon brillan (textures/entity/stalker_glowmask.png)
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** Etapa 1: tamano de Allay (0.2); etapa 2: tamano completo (3 bloques). */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, StalkerEntity animatable,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float s = animatable.getStage() == 2 ? 1.0F : 0.2F;
        super.scaleModelForRender(widthScale * s, heightScale * s, poseStack, animatable, model, isReRender,
                partialTick, packedLight, packedOverlay);
    }

    /** La Krasue tiene su propia animacion de muerte: no se voltea como un mob normal. */
    @Override
    protected float getDeathMaxRotation(StalkerEntity animatable) {
        return 0.0F;
    }
}
