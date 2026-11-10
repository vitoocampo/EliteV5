package com.elitebosses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.elitebosses.EliteBosses;
import com.elitebosses.entity.ChocolateEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Dibuja el mob como un sprite 2D que siempre mira a la camara y se estira al caminar. */
public class ChocolateRenderer extends EntityRenderer<ChocolateEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(EliteBosses.MODID, "textures/entity/chocolate.png");

    private static final float HEIGHT = 2.0F;
    private static final float ASPECT = 301.0F / 308.0F; // ancho / alto del PNG

    public ChocolateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
    }

    @Override
    public ResourceLocation getTextureLocation(ChocolateEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ChocolateEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        pose.pushPose();

        // Estiramiento vertical al caminar (conserva el "volumen": si sube, se angosta)
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        float intensity = Math.min(1.0F, (float) Math.sqrt(dx * dx + dz * dz) * 5.0F);
        float wave = Mth.sin((entity.tickCount + partialTick) * 0.55F);
        float stretch = 1.0F + 0.22F * intensity * wave;
        float squeeze = 1.0F / stretch;

        // Siempre de frente a la camara, pero sin inclinarse
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - this.entityRenderDispatcher.camera.getYRot()));
        pose.scale(squeeze, stretch, 1.0F);

        float halfWidth = HEIGHT * ASPECT * 0.5F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        PoseStack.Pose last = pose.last();
        Matrix4f matrix = last.pose();
        Matrix3f normal = last.normal();

        vertex(consumer, matrix, normal, -halfWidth, 0.0F, 0.0F, 1.0F, light);
        vertex(consumer, matrix, normal, halfWidth, 0.0F, 1.0F, 1.0F, light);
        vertex(consumer, matrix, normal, halfWidth, HEIGHT, 1.0F, 0.0F, light);
        vertex(consumer, matrix, normal, -halfWidth, HEIGHT, 0.0F, 0.0F, light);

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float u, float v, int light) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
