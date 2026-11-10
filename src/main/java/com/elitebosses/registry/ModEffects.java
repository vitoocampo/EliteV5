package com.elitebosses.registry;

import com.elitebosses.EliteBosses;
import com.elitebosses.client.LevelDigitDisplay;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EliteBosses.MODID);

    /** Obsesion: marca al jugador elegido por el Stalker (5 minutos). */
    public static final RegistryObject<MobEffect> OBSESSION = EFFECTS.register("obsession",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x7A1018, false));

    /** Salmuera: se acumula (cada carga sube el nivel). Solo la leche, la muerte o la muerte de la Krasue la quitan. */
    public static final RegistryObject<MobEffect> BRINE = EFFECTS.register("brine",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x3FB8A0, true));

    /** Velo Diurno: la proteccion del objeto Angel Guardian (20 min). Los mobs de Elite Bosses a 15 bloques desaparecen. */
    public static final RegistryObject<MobEffect> VELO_DIURNO = EFFECTS.register("velo_diurno",
            () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xF6D77A, false));

    private ModEffects() {
    }

    private static final class SimpleEffect extends MobEffect {
        private final boolean alwaysShowLevel;

        SimpleEffect(MobEffectCategory category, int color, boolean alwaysShowLevel) {
            super(category, color);
            this.alwaysShowLevel = alwaysShowLevel;
        }

        /** Inventario: el nivel se muestra con digito (1, 2 ... 200), no en romanos, sin limite de nivel. */
        @Override
        public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
            consumer.accept(new LevelDigitDisplay(alwaysShowLevel));
        }
    }
}
