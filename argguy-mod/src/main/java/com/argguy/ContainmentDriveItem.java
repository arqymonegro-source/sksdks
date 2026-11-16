package com.argguy;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The friends' compression drive. Only works on ARGGUY while YOU are looking at it. */
public class ContainmentDriveItem extends Item {
    public ContainmentDriveItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof ArgGuyEntity e) {
            if (!player.level().isClientSide() && !e.tryCapture(player)) {
                player.sendOverlayMessage(Component.translatable("message.argguy.not_watched"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
