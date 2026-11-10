package com.elitebosses.registry;

import com.elitebosses.EliteBosses;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Pestana propia del creativo: "Elite Bosses" (huevos y objetos). */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EliteBosses.MODID);

    public static final RegistryObject<CreativeModeTab> ELITE_BOSSES = TABS.register("elitebosses",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elitebosses"))
                    .icon(() -> new ItemStack(ModItems.HOUNDMASTER_SPAWN_EGG.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.CHOCOLATE_SPAWN_EGG.get());
                        output.accept(ModItems.HOUNDMASTER_SPAWN_EGG.get());
                        output.accept(ModItems.STALKER_SPAWN_EGG.get());
                        output.accept(ModItems.JUDGE_SPAWN_EGG.get());
                        output.accept(ModItems.VITOS_BLESSING.get());
                        output.accept(ModItems.SURVIVAL_MANUAL.get());
                        output.accept(ModItems.BLACK_FATE_TOTEM.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
