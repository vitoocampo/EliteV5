package com.elitebosses.item;

import com.elitebosses.entity.ChocolateEntity;
import com.elitebosses.entity.HoundmasterEntity;
import com.elitebosses.entity.StalkerEntity;
import com.elitebosses.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Angel Guardian (id interno: vitos_blessing, para no romper mundos ni recetas de la v3):
 * borra a todo mob de EliteBosses en 128 bloques (sin drops) y te da el efecto VELO DIURNO 20 minutos:
 * mientras lo tengas, cualquier mob de EliteBosses que se acerque a 15 bloques o menos de ti desaparece.
 */
public class VitosBlessingItem extends Item {
    public static final long DURATION_TICKS = 20L * 60L * 20L;
    public static final double CLEAR_RANGE = 128.0D;
    public static final double WARD_RANGE = 15.0D;

    public VitosBlessingItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    public static boolean isEliteBoss(Entity e) {
        return e instanceof ChocolateEntity || e instanceof HoundmasterEntity || e instanceof StalkerEntity
                || (e instanceof Wolf w && (w.getPersistentData().hasUUID(HoundmasterEntity.WOLF_KEY)));
    }

    /** Hace desaparecer (sin drops) los mobs de EliteBosses dentro de 'range' de 'center'. */
    public static int banish(ServerLevel level, AABB box) {
        List<Entity> found = new ArrayList<>();
        for (Entity e : level.getEntities((Entity) null, box, VitosBlessingItem::isEliteBoss)) {
            found.add(e);
        }
        for (Entity e : found) {
            level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2.0D, e.getZ(),
                    20, 0.4D, 0.6D, 0.4D, 0.02D);
            e.discard();
        }
        return found.size();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            int n = banish(serverLevel, player.getBoundingBox().inflate(CLEAR_RANGE));
            player.addEffect(new MobEffectInstance(ModEffects.VELO_DIURNO.get(), (int) DURATION_TICKS, 0, false, true, true));
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
            serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(),
                    60, 0.6D, 0.9D, 0.6D, 0.08D);
            player.displayClientMessage(Component.literal(
                    "Angel Guardian: " + n + " criatura(s) desterrada(s). Velo Diurno durante 20 minutos.")
                    .withStyle(ChatFormatting.GOLD), false);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
