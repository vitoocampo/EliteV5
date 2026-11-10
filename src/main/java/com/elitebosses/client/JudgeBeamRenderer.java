package com.elitebosses.client;

import com.elitebosses.entity.JudgeBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Dibuja la columna de luz con el haz del faro de Minecraft (colores y grosor segun el modo). */
public class JudgeBeamRenderer extends EntityRenderer<JudgeBeamEntity> {
    public JudgeBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(JudgeBeamEntity beam, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = beam.getAge() + partialTick;
        int life = beam.getLife();
        float radius, glow;
        float[] color;
        switch (beam.getMode()) {
            case JudgeBeamEntity.MODE_TELEGRAPH -> {
                float flick = 0.6F + 0.4F * Mth.sin(age * 1.7F);
                radius = 0.08F + 0.05F * flick;
                glow = 1.0F; // marca el area 2x2
                color = new float[]{1.0F, 0.72F * flick + 0.2F, 0.25F};
            }
            case JudgeBeamEntity.MODE_STRIKE -> {
                float k = life > 0 ? 1.0F - Mth.clamp(age / life, 0.0F, 1.0F) : 1.0F;
                radius = 0.35F + 0.65F * k;
                glow = 1.1F + 0.3F * k;
                color = new float[]{1.0F, 0.95F, 0.75F};
            }
            case JudgeBeamEntity.MODE_DESCENT -> {
                float k = life > 0 ? 1.0F - Mth.clamp(age / life, 0.0F, 1.0F) : 1.0F;
                radius = 1.1F * k + 0.05F;
                glow = 1.5F * k + 0.1F;
                color = new float[]{1.0F, 0.97F, 0.86F};
            }
            default -> { // MODE_FOLLOW: pulsa lentamente
                float p = 0.5F + 0.5F * Mth.sin(age * 0.15F);
                radius = 0.28F + 0.08F * p;
                glow = 0.45F + 0.15F * p;
                color = new float[]{1.0F, 0.98F, 0.9F};
            }
        }
        poseStack.pushPose();
        poseStack.translate(-0.5D, 0.0D, -0.5D); // renderBeaconBeam se centra en el bloque (0.5, 0.5)
        int height = Math.max(32, beam.level().getMaxBuildHeight() - (int) beam.getY());
        BeaconRenderer.renderBeaconBeam(poseStack, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F,
                beam.level().getGameTime(), 0, height, color, radius, glow);
        poseStack.popPose();
        super.render(beam, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public boolean shouldRender(JudgeBeamEntity beam, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(JudgeBeamEntity beam) {
        return BeaconRenderer.BEAM_LOCATION;
    }
}
