package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dibuja la barra de vida personalizada del Sabueso en lugar de la linea morada de Minecraft. */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID, value = Dist.CLIENT)
public final class HoundBossBar {
    private static final String NAME_KEY = "entity.elitebosses.houndmaster";
    private static final ResourceLocation EMPTY = new ResourceLocation(EliteBosses.MODID, "textures/gui/hound_bar_empty.png");
    private static final ResourceLocation FILL = new ResourceLocation(EliteBosses.MODID, "textures/gui/hound_bar_fill.png");
    private static final ResourceLocation FRAME = new ResourceLocation(EliteBosses.MODID, "textures/gui/hound_bar_frame.png");

    // medidas de las texturas (px) y de la ventana interior donde va la vida
    private static final int TEX_W = 432, TEX_H = 146;
    private static final int IX = 119, IY = 35, IW = 278, IH = 43;
    private static final float SCALE = 0.62F;

    private HoundBossBar() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !NAME_KEY.equals(tc.getKey())) {
            return;
        }
        try {
            event.setCanceled(true);
        } catch (UnsupportedOperationException ex) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int w = Math.round(TEX_W * SCALE);
        int h = Math.round(TEX_H * SCALE);
        int x = (screenW - w) / 2;
        int y = event.getY() - 4;

        float pct = Math.max(0.0F, Math.min(1.0F, boss.getProgress()));
        int fillTexW = Math.round(IW * pct);

        RenderSystem.enableBlend();
        g.blit(EMPTY, x, y, w, h, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
        if (fillTexW > 0) {
            g.blit(FILL, x + Math.round(IX * SCALE), y + Math.round(IY * SCALE),
                    Math.round(fillTexW * SCALE), Math.round(IH * SCALE),
                    (float) IX, (float) IY, fillTexW, IH, TEX_W, TEX_H);
        }
        g.blit(FRAME, x, y, w, h, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
        RenderSystem.disableBlend();

        event.setIncrement(h - 2);
    }
}
