package com.elitebosses.events;

import com.elitebosses.EliteBosses;
import com.elitebosses.command.ChocoSoundCommand;
import com.elitebosses.entity.ChocolateEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = EliteBosses.MODID)
public final class ChocoEvents {
    private ChocoEvents() {
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Item item = event.getItem().getItem();
        if (item == Items.COOKIE) {
            onCookie(player);
        } else if (item == Items.MILK_BUCKET) {
            onMilk(player);
        }
    }

    /**
     * Galleta: desaparece todo Chocolate a 32 bloques o menos de quien la come,
     * y tambien el que lo este persiguiendo a el, sin importar la distancia.
     */
    private static void onCookie(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double range = ChocolateEntity.COOKIE_RANGE;
        List<ChocolateEntity> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ChocolateEntity mob
                    && (mob.distanceToSqr(player) <= range * range || mob.isTargeting(player))) {
                found.add(mob);
            }
        }
        for (ChocolateEntity mob : found) {
            mob.vanishWithGhasts();
        }
    }

    /**
     * Leche: el Chocolate que te persigue (o que este a 32 bloques o menos) desaparece
     * igual que con la galleta, se te quita el efecto y pagas el castigo de tramposo.
     */
    private static void onMilk(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double range = ChocolateEntity.COOKIE_RANGE;
        List<ChocolateEntity> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ChocolateEntity mob
                    && (mob.distanceToSqr(player) <= range * range
                    || mob.isTargeting(player) || mob.isGrantingSpeedTo(player))) {
                found.add(mob);
            }
        }
        if (found.isEmpty()) {
            return;
        }
        for (ChocolateEntity mob : found) {
            mob.vanishWithGhasts();
        }
        player.removeAllEffects();
        ChocoPunishment.punish(player);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        ChocoSoundCommand.register(event.getDispatcher());
    }
}
