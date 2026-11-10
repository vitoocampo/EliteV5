package com.elitebosses.events;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.StalkerEntity;
import com.elitebosses.item.VitosBlessingItem;
import com.elitebosses.registry.ModEffects;
import com.elitebosses.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.common.BasicItemListing;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Totem BlackFate, Angel Guardian (efecto Velo Diurno), Manual (trato de bibliotecario) y muerte del objetivo del Stalker. */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID)
public final class ItemEvents {
    private ItemEvents() {
    }

    // ---------------------------------------------------------------- muerte
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Totem BlackFate (el totem normal ya se resolvio antes dentro de hurt())
        if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.is(ModItems.BLACK_FATE_TOTEM.get())) {
                    stack.shrink(1);
                    player.setHealth(1.0F);
                    player.removeAllEffects();
                    // 30% menos que el totem normal (Regen II 45 s, Fuego 40 s, Absorcion II 5 s)
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 630, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 560, 0));
                    player.setAbsorptionAmount(5.6F);
                    player.getFoodData().setFoodLevel(0);
                    player.getFoodData().setSaturation(0.0F);
                    player.level().broadcastEntityEvent(player, (byte) 35);
                    event.setCanceled(true);
                    return;
                }
            }
        }
        // El Stalker cuyo objetivo murio inicia su cierre
        ServerLevel level = player.serverLevel();
        List<StalkerEntity> stalkers = new ArrayList<>(
                level.getEntities(EntityTypeTest.forClass(StalkerEntity.class), e -> true));
        for (StalkerEntity s : stalkers) {
            if (player.getUUID().equals(s.getTargetId())) {
                s.onTargetDied(player);
            }
        }
    }

    // ------------------------------------------------------- drop del wither
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity().getType() == EntityType.WITHER_SKELETON
                && event.getEntity().getRandom().nextFloat() < 0.20F) {
            event.getDrops().add(new ItemEntity(event.getEntity().level(),
                    event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                    new ItemStack(ModItems.BLACK_FATE_TOTEM.get())));
        }
    }

    // ------------------------------------------------------------ bibliotecario
    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() == VillagerProfession.LIBRARIAN) {
            event.getTrades().get(1).add(new BasicItemListing(
                    new ItemStack(Items.DIAMOND, 40), new ItemStack(ModItems.SURVIVAL_MANUAL.get()), 4, 15, 0.05F));
        }
    }

    // ------------------------------------------------------ Angel Guardian / Velo Diurno
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 5 != 0) {
            return;
        }
        if (player.hasEffect(ModEffects.VELO_DIURNO.get())) {
            VitosBlessingItem.banish(player.serverLevel(), player.getBoundingBox().inflate(VitosBlessingItem.WARD_RANGE));
        }
    }
}
