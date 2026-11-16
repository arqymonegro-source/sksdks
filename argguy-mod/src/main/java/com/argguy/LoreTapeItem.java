package com.argguy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * One tape per YouTube channel of the ARG. Right-click prints the fragment in chat.
 * ALL text (including the codes / URLs you want players to chase) lives in lang/en_us.json,
 * keys tape.argguy.<channel>.title and .line1 - .line5.
 */
public class LoreTapeItem extends Item {
    private static final int LINES = 5;
    private final String channel;

    public LoreTapeItem(Properties props, String channel) {
        super(props);
        this.channel = channel;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.translatable("tape.argguy." + channel + ".title")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            for (int i = 1; i <= LINES; i++) {
                player.sendSystemMessage(Component.translatable("tape.argguy." + channel + ".line" + i)
                        .withStyle(ChatFormatting.GRAY));
            }
            level.playSound(null, player.blockPosition(), ModSounds.RADIO, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
