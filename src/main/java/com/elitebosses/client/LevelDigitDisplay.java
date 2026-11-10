package com.elitebosses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/**
 * Dibuja el nombre de los efectos de Elite Bosses en el inventario con el nivel en digitos.
 * El juego solo muestra romanos del II al X y no escribe nada a partir del nivel 11; aqui se
 * escribe siempre el numero real (Salmuera puede llegar a 200).
 */
public final class LevelDigitDisplay implements IClientMobEffectExtensions {
    private final boolean alwaysShowLevel;

    public LevelDigitDisplay(boolean alwaysShowLevel) {
        this.alwaysShowLevel = alwaysShowLevel;
    }

    @Override
    public boolean renderInventoryText(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                       GuiGraphics gg, int x, int y, int blitOffset) {
        Font font = Minecraft.getInstance().font;
        int level = instance.getAmplifier() + 1;
        MutableComponent name = instance.getEffect().getDisplayName().copy();
        if (alwaysShowLevel || level > 1) {
            name.append(Component.literal(" " + level));
        }
        gg.drawString(font, name, x + 28, y + 6, 0xFFFFFF);
        gg.drawString(font, MobEffectUtil.formatDuration(instance, 1.0F), x + 28, y + 16, 0x7F7F7F);
        return true; // true = no dibuja el texto original (con romanos)
    }
}
