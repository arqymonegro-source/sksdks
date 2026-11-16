package com.argguy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/**
 * "demo_stop": classifies what you just recorded, Interloper style.
 *   Type 1 - nothing happened          Type 3 - something is nearby, watching
 *   Type 5 - first-person of the thing, it is hunting      Type 6 - recorded inside the Red Room
 *   Type 7 - "COMMITTED TODAY": the entity has already escaped
 * Playback never matches generation. (Text in lang: demo.argguy.*)
 */
public class DemoRecorderItem extends Item {
    public DemoRecorderItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        List<ArgGuyEntity> near = level.getEntitiesOfClass(ArgGuyEntity.class, player.getBoundingBox().inflate(48.0));
        ArgGuyEntity e = near.stream().min(Comparator.comparingDouble(x -> x.distanceToSqr(player))).orElse(null);

        int type;
        if (RedRoom.isIn(player.getUUID())) type = 6;
        else if (e != null && e.isEscaped()) type = 7;
        else if (e != null && e.isHunting()) type = 5;
        else if (e != null) type = 3;
        else type = 1;

        String name = "argguy_" + (level.getGameTime() % 100000);
        player.sendSystemMessage(Component.translatable("demo.argguy.header").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("demo.argguy.saved", name).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("demo.argguy.type", type).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.translatable("demo.argguy.type" + type).withStyle(ChatFormatting.DARK_RED));
        if (type >= 3) {
            player.sendSystemMessage(Component.translatable("demo.argguy.rot").withStyle(ChatFormatting.DARK_GRAY));
        }
        return InteractionResult.SUCCESS;
    }
}
