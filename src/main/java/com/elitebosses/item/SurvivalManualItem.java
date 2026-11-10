package com.elitebosses.item;

import com.elitebosses.client.ManualScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Manual de Supervivencia (solo se consigue con aldeanos libreros). Clic derecho para leerlo. */
public class SurvivalManualItem extends Item {
    public SurvivalManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ManualScreen::open);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
