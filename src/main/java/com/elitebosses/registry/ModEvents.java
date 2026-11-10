package com.elitebosses.registry;

import com.elitebosses.EliteBosses;
import com.elitebosses.entity.ChocolateEntity;
import com.elitebosses.entity.HoundmasterEntity;
import com.elitebosses.entity.JudgeEntity;
import com.elitebosses.entity.StalkerEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EliteBosses.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    private ModEvents() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CHOCOLATE.get(), ChocolateEntity.createAttributes().build());
        event.put(ModEntities.HOUNDMASTER.get(), HoundmasterEntity.createAttributes().build());
        event.put(ModEntities.STALKER.get(), StalkerEntity.createAttributes().build());
        event.put(ModEntities.JUDGE.get(), JudgeEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.CHOCOLATE_SPAWN_EGG.get());
            event.accept(ModItems.HOUNDMASTER_SPAWN_EGG.get());
            event.accept(ModItems.STALKER_SPAWN_EGG.get());
            event.accept(ModItems.JUDGE_SPAWN_EGG.get());
        }
    }
}
