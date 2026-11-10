package com.elitebosses.client;

import com.elitebosses.entity.JudgeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class JudgeRenderer extends GeoEntityRenderer<JudgeEntity> {
    public JudgeRenderer(EntityRendererProvider.Context context) {
        super(context, new JudgeModel());
        this.shadowRadius = 1.6F;
        // rendija de la mascara, ojos de las alas, herida del pecho, anillos y baculo brillan
        // (textures/entity/judge_glowmask.png)
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** Tiene su propia animacion de muerte (cae de rodillas): no se voltea. */
    @Override
    protected float getDeathMaxRotation(JudgeEntity animatable) {
        return 0.0F;
    }
}
