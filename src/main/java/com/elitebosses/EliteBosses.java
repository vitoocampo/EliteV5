package com.elitebosses;

import com.elitebosses.net.ModNetwork;
import com.elitebosses.registry.ModCreativeTabs;
import com.elitebosses.registry.ModEffects;
import com.elitebosses.registry.ModEntities;
import com.elitebosses.registry.ModItems;
import com.elitebosses.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(EliteBosses.MODID)
public class EliteBosses {
    public static final String MODID = "elitebosses";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EliteBosses() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(ModNetwork::register));
    }
}
