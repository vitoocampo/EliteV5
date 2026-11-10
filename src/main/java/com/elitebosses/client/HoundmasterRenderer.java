package com.elitebosses.client;

import com.elitebosses.entity.HoundmasterEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class HoundmasterRenderer extends GeoEntityRenderer<HoundmasterEntity> {
    public HoundmasterRenderer(EntityRendererProvider.Context context) {
        super(context, new HoundmasterModel());
        this.shadowRadius = 1.1F;
        this.scaleWidth = 0.85F;
        this.scaleHeight = 0.85F;
        // ojos, cavidad toracica y llagas brillan (textures/entity/houndmaster_glowmask.png)
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
