package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.elitebosses.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EliteBosses.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHOCOLATE.get(), ChocolateRenderer::new);
        event.registerEntityRenderer(ModEntities.HOUNDMASTER.get(), HoundmasterRenderer::new);
        event.registerEntityRenderer(ModEntities.STALKER.get(), StalkerRenderer::new);
        event.registerEntityRenderer(ModEntities.JUDGE.get(), JudgeRenderer::new);
        event.registerEntityRenderer(ModEntities.JUDGE_BEAM.get(), JudgeBeamRenderer::new);
    }

    /** HUD del Juicio (contador, condena, COBARDE, evento rapido...). */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("judge_hud", JudgeClient::renderOverlay);
    }
}
