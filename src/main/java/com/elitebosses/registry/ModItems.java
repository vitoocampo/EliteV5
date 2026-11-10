package com.elitebosses.registry;

import com.elitebosses.EliteBosses;
import com.elitebosses.item.BlackFateTotemItem;
import com.elitebosses.item.SurvivalManualItem;
import com.elitebosses.item.VitosBlessingItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EliteBosses.MODID);

    public static final RegistryObject<Item> CHOCOLATE_SPAWN_EGG = ITEMS.register("chocolate_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CHOCOLATE, 0x3B1F0E, 0xC98A4B, new Item.Properties()));

    public static final RegistryObject<Item> HOUNDMASTER_SPAWN_EGG = ITEMS.register("houndmaster_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.HOUNDMASTER, 0x1A1414, 0xB01010, new Item.Properties()));

    public static final RegistryObject<Item> STALKER_SPAWN_EGG = ITEMS.register("stalker_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.STALKER, 0x5A1A1A, 0xD8D0C8, new Item.Properties()));

    public static final RegistryObject<Item> JUDGE_SPAWN_EGG = ITEMS.register("judge_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.JUDGE, 0x2A2826, 0xE0B54A, new Item.Properties()));

    public static final RegistryObject<Item> VITOS_BLESSING = ITEMS.register("vitos_blessing",
            () -> new VitosBlessingItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> SURVIVAL_MANUAL = ITEMS.register("survival_manual",
            () -> new SurvivalManualItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> BLACK_FATE_TOTEM = ITEMS.register("black_fate_totem",
            () -> new BlackFateTotemItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {
    }
}
