package com.elitebosses.registry;

import com.elitebosses.EliteBosses;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EliteBosses.MODID);

    public static final RegistryObject<SoundEvent> CHOCOCHASE1 = register("chocochase1");
    public static final RegistryObject<SoundEvent> CHOCOCHASE2 = register("chocochase2");
    public static final RegistryObject<SoundEvent> CHOCOSPAWN = register("chocospawn");
    public static final RegistryObject<SoundEvent> HOUNDLURK = register("houndlurk");
    public static final RegistryObject<SoundEvent> HOUNDCHASE = register("houndchase");
    public static final RegistryObject<SoundEvent> STALKERSTALK1 = register("stalkerstalk1");
    public static final RegistryObject<SoundEvent> STALKERSTALK2 = register("stalkerstalk2");
    public static final RegistryObject<SoundEvent> STALKERCHASE = register("stalkerchase");
    public static final RegistryObject<SoundEvent> FLESH_FORM = register("flesh_form");
    public static final RegistryObject<SoundEvent> JUDGECHASE = register("judgechase");
    public static final RegistryObject<SoundEvent> JUDGESPAWN = register("judgespawn");
    public static final RegistryObject<SoundEvent> JUDGESTALK = register("judgestalk");
    public static final RegistryObject<SoundEvent> JUDGEWARN1 = register("judgewarn1");
    public static final RegistryObject<SoundEvent> JUDGEWARN2 = register("judgewarn2");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EliteBosses.MODID, name)));
    }
}
