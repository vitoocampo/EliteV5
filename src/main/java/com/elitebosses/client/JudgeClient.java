package com.elitebosses.client;

import com.elitebosses.EliteBosses;
import com.elitebosses.net.JudgeHudPacket;
import com.elitebosses.net.JudgeInputPacket;
import com.elitebosses.net.ModNetwork;
import com.elitebosses.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Cliente del Juicio: dibuja el contador rojo, la condena, "COBARDE", el evento rapido (R/G/C/P),
 * el ESPACIO del empalamiento y la distancia tras el Exilio; reproduce los avisos; bloquea el boton
 * "Levantarse" de la cama mientras dura JudgeStalk.
 */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID, value = Dist.CLIENT)
public final class JudgeClient {
    private static final int RED = 0xE01818;
    private static final int DARK_RED = 0x8B0000;
    private static final int GOLD = 0xF2C14E;
    private static final int[] KEYS = {GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_P};
    private static final String KEY_NAMES = "RGCP";

    private static int count;
    private static int counterTicks;
    private static int pulse;
    private static int doomTicks, doomMax;
    private static int distance = -1;
    private static int qteKey = -1, qteTicks, qteMax, qteResult, qteResultTicks;
    private static int impaleLeft = -1;
    private static int bedLock;
    private static int cowardTicks;
    private static boolean jumpWasDown;
    private static SimpleSoundInstance cue;
    private static final RandomSource RNG = RandomSource.create();

    private JudgeClient() {
    }

    // ------------------------------------------------------------------ paquetes
    public static void handle(int type, int a, int b) {
        switch (type) {
            case JudgeHudPacket.COUNTER -> {
                if (a != count) {
                    pulse = 6;
                }
                count = a;
                if (b > 0) {
                    counterTicks = b;
                } else if (b < 0) {
                    counterTicks = 0;
                }
            }
            case JudgeHudPacket.DOOM -> {
                count = a;
                doomTicks = doomMax = Math.max(1, b);
                counterTicks = 0;
            }
            case JudgeHudPacket.DISTANCE -> distance = a;
            case JudgeHudPacket.QTE_START -> {
                qteKey = a;
                qteTicks = qteMax = Math.max(1, b);
                qteResultTicks = 0;
            }
            case JudgeHudPacket.QTE_END -> {
                qteKey = -1;
                qteTicks = 0;
                qteResult = a;
                qteResultTicks = 20;
            }
            case JudgeHudPacket.IMPALE -> {
                impaleLeft = a;
                jumpWasDown = true;
            }
            case JudgeHudPacket.BED_LOCK -> bedLock = Math.max(0, a);
            case JudgeHudPacket.COWARD -> cowardTicks = a;
            case JudgeHudPacket.SOUND -> playCue(a);
            case JudgeHudPacket.CLEAR -> reset(false);
            default -> {
            }
        }
    }

    private static void playCue(int id) {
        Minecraft mc = Minecraft.getInstance();
        SoundEvent sound = switch (id) {
            case 1 -> ModSounds.JUDGEWARN1.get();
            case 2 -> ModSounds.JUDGEWARN2.get();
            case 3 -> ModSounds.JUDGESTALK.get();
            case 4 -> ModSounds.JUDGESPAWN.get();
            default -> null;
        };
        if (sound == null) {
            if (cue != null) {
                mc.getSoundManager().stop(cue);
                cue = null;
            }
            return;
        }
        SimpleSoundInstance inst = SimpleSoundInstance.forUI(sound, 1.0F, 1.0F);
        if (id >= 3) {
            if (cue != null) {
                mc.getSoundManager().stop(cue);
            }
            cue = inst;
        }
        mc.getSoundManager().play(inst);
    }

    private static void reset(boolean stopSounds) {
        count = 0;
        counterTicks = 0;
        doomTicks = 0;
        distance = -1;
        qteKey = -1;
        qteTicks = 0;
        qteResultTicks = 0;
        impaleLeft = -1;
        bedLock = 0;
        cowardTicks = 0;
        if (stopSounds && cue != null) {
            Minecraft.getInstance().getSoundManager().stop(cue);
            cue = null;
        }
    }

    // ------------------------------------------------------------------ tick y teclas
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset(true);
            return;
        }
        if (counterTicks > 0) counterTicks--;
        if (doomTicks > 0) doomTicks--;
        if (qteTicks > 0) qteTicks--;
        if (qteResultTicks > 0) qteResultTicks--;
        if (bedLock > 0) bedLock--;
        if (cowardTicks > 0) cowardTicks--;
        if (pulse > 0) pulse--;

        // en la cama durante JudgeStalk: el boton "Levantarse" no funciona
        if (mc.screen instanceof InBedChatScreen bed) {
            for (GuiEventListener l : bed.children()) {
                if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                        && "multiplayer.stopSleeping".equals(tc.getKey())) {
                    b.active = bedLock <= 0;
                }
            }
        }

        // ESPACIO para liberarse del empalamiento
        if (impaleLeft > 0) {
            boolean down = mc.options.keyJump.isDown();
            if (down && !jumpWasDown) {
                ModNetwork.CHANNEL.sendToServer(new JudgeInputPacket(JudgeInputPacket.SPACE, 0));
            }
            jumpWasDown = down;
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (qteKey < 0 || qteTicks <= 0 || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (event.getKey() == KEYS[i]) {
                ModNetwork.CHANNEL.sendToServer(new JudgeInputPacket(JudgeInputPacket.QTE_KEY, i));
                qteTicks = 0; // espera la respuesta del servidor
                return;
            }
        }
    }

    // ------------------------------------------------------------------ cama: no te puedes levantar
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (bedLock > 0 && event.getScreen() instanceof InBedChatScreen) {
            for (GuiEventListener l : event.getListenersList()) {
                if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                        && "multiplayer.stopSleeping".equals(tc.getKey())) {
                    b.active = false;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (bedLock > 0 && event.getScreen() instanceof InBedChatScreen && event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(true);
    }

    // ------------------------------------------------------------------ HUD
    /** Registrado en ClientModEvents como capa por encima de todo. */
    public static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;

        // contador rojo (arriba, bajo las barras de jefe)
        if (counterTicks > 0 && doomTicks <= 0) {
            float fade = Math.min(1.0F, counterTicks / 20.0F);
            float s = 3.0F + (pulse > 0 ? pulse * 0.12F : 0.0F);
            int y = (int) (height * 0.17F);
            drawCentered(g, font, String.valueOf(count), width / 2, y, s, RED, fade, true);
        }

        // condena: el numero fijo al centro + "Tus dias estan contados"
        if (doomTicks > 0) {
            float t = doomMax - doomTicks;
            float fade = Math.min(1.0F, Math.min(t / 10.0F, doomTicks / 15.0F));
            g.fill(0, 0, width, height, argb(0x000000, 0.35F * fade));
            drawCentered(g, font, String.valueOf(count), width / 2, height / 2 - 34, 6.0F, DARK_RED, fade, true);
            drawCentered(g, font, "Tus días están contados", width / 2, height / 2 + 26, 1.8F, DARK_RED, fade, true);
        }

        // COBARDE (Valentia)
        if (cowardTicks > 0) {
            float fade = Math.min(1.0F, cowardTicks / 12.0F);
            int jx = RNG.nextInt(5) - 2, jy = RNG.nextInt(5) - 2;
            g.fill(0, 0, width, height, argb(0x400000, 0.22F * fade));
            drawCentered(g, font, "COBARDE", width / 2 + jx, (int) (height * 0.32F) + jy, 5.0F, 0xB00000, fade, true);
        }

        // distancia tras el Exilio
        if (distance > 0) {
            String txt = "EL JUICIO  ·  " + distance + " bloques";
            drawCentered(g, font, txt, width / 2, (int) (height * 0.25F), 1.6F, distance > 40 ? RED : GOLD, 1.0F, true);
        }

        // evento rapido: R / G / C / P
        if (qteKey >= 0 && qteTicks > 0) {
            float k = qteTicks / (float) qteMax;
            int cx = width / 2, cy = height / 2 + 30;
            int box = 34;
            g.fill(cx - box, cy - box, cx + box, cy + box, argb(0x000000, 0.65F));
            g.fill(cx - box, cy - box, cx + box, cy - box + 2, argb(GOLD, 1.0F));
            g.fill(cx - box, cy + box - 2, cx + box, cy + box, argb(GOLD, 1.0F));
            g.fill(cx - box, cy - box, cx - box + 2, cy + box, argb(GOLD, 1.0F));
            g.fill(cx + box - 2, cy - box, cx + box, cy + box, argb(GOLD, 1.0F));
            drawCentered(g, font, String.valueOf(KEY_NAMES.charAt(qteKey)), cx, cy - 13, 4.0F, 0xFFFFFF, 1.0F, true);
            int bw = (int) (box * 2 * k);
            g.fill(cx - box, cy + box + 6, cx - box + bw, cy + box + 10, argb(k > 0.35F ? GOLD : RED, 1.0F));
            drawCentered(g, font, "¡PULSA!", cx, cy - box - 18, 1.6F, RED, 1.0F, true);
        } else if (qteResultTicks > 0) {
            drawCentered(g, font, qteResult == 1 ? "TE ZAFASTE" : "EMPALADO", width / 2, height / 2 + 20, 2.4F,
                    qteResult == 1 ? GOLD : DARK_RED, Math.min(1.0F, qteResultTicks / 8.0F), true);
        }

        // empalado: ESPACIO x N
        if (impaleLeft > 0) {
            g.fill(0, 0, width, height, argb(0x300000, 0.25F));
            drawCentered(g, font, "¡ESPACIO!", width / 2, height / 2 + 34, 2.6F, RED, 1.0F, true);
            drawCentered(g, font, "x" + impaleLeft, width / 2, height / 2 + 62, 2.0F, 0xFFFFFF, 1.0F, true);
        }

        // en la cama: no puedes levantarte
        if (bedLock > 0) {
            drawCentered(g, font, "No puedes levantarte...", width / 2, (int) (height * 0.2F), 1.5F, DARK_RED,
                    Math.min(1.0F, bedLock / 20.0F), true);
        }
    }

    private static void drawCentered(GuiGraphics g, Font font, String text, int x, int y, float scale, int rgb,
                                     float alpha, boolean shadow) {
        if (alpha <= 0.02F) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int w = font.width(text);
        g.drawString(font, text, -w / 2, 0, argb(rgb, alpha), shadow);
        g.pose().popPose();
    }

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 6, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
