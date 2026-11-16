
package com.argguy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ArgGuyPersonality {

    private static final Map<UUID, PlayerMemory> MEMORY = new HashMap<>();

    private ArgGuyPersonality() {}

    private static final class PlayerMemory {
        int conversations;
        int attacks;
        int roomEscapes;

        int approaches;
        int retreats;
        int stareTicks;
        int ignoredTicks;
        int sprintTicks;
        int peacefulTicks;
        int closeTicks;

        int actionCooldown = 220;
        int lastAttackTimestamp = Integer.MIN_VALUE;

        int retreatTicks;
        Vec3 retreatTarget = Vec3.ZERO;

        double lastDistance = -1.0;

        boolean wasWatching;
        boolean wasInRedRoom;
        boolean helped;
    }

    private static PlayerMemory memory(Player player) {
        return MEMORY.computeIfAbsent(
                player.getUUID(),
                id -> new PlayerMemory()
        );
    }

    // ------------------------------------------------------------
    // MEMORY
    // ------------------------------------------------------------

    private static void updateRoomMemory(ArgGuyEntity entity) {
        for (Player player : entity.level().players()) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                continue;
            }

            PlayerMemory data = memory(player);
            boolean currentlyInside = RedRoom.isIn(serverPlayer.getUUID());

            if (data.wasInRedRoom && !currentlyInside) {
                data.roomEscapes++;

                player.sendSystemMessage(
                        Component.literal("ARGGUY: You came back.")
                );
            }

            data.wasInRedRoom = currentlyInside;
        }
    }

    private static void observe(
            ArgGuyEntity entity,
            Player player,
            double distance,
            PlayerMemory data
    ) {
        if (data.lastDistance >= 0) {
            if (distance < data.lastDistance - 0.1) {
                data.approaches++;
            } else if (distance > data.lastDistance + 0.1) {
                data.retreats++;
            }
        }

        data.lastDistance = distance;

        if (distance < 8) {
            data.closeTicks++;
        }

        if (player.isSprinting()) {
            data.sprintTicks++;
        }

        if (!player.isSprinting()
                && distance < 12
                && data.attacks == 0) {
            data.peacefulTicks++;
        }

        if (entity.isWatchedBy(player)) {
            data.stareTicks++;
            data.wasWatching = true;
        } else {
            data.ignoredTicks++;

            if (data.wasWatching) {
                data.wasWatching = false;
            }
        }

        // Remember when the player attacks ARGGUY.
        if (player.getLastHurtMob() == entity) {
            int timestamp = player.getLastHurtMobTimestamp();

            if (timestamp != data.lastAttackTimestamp) {
                data.lastAttackTimestamp = timestamp;
                data.attacks++;

                sound(
                        entity,
                        entity.position(),
                        ModSounds.CHUCKLE,
                        2.0F,
                        0.8F
                );

                player.sendSystemMessage(
                        Component.literal(
                                data.attacks == 1
                                        ? "ARGGUY: That was rude."
                                        : "ARGGUY: Again? You're becoming predictable."
                        )
                );
            }
        }
    }

    // ------------------------------------------------------------
    // MAIN PERSONALITY BRAIN
    // ------------------------------------------------------------

    public static void tick(
            ArgGuyEntity entity,
            Player player,
            double distance,
            boolean frozen,
            boolean hunting
    ) {
        if (entity.level().isClientSide() || entity.isWatcher()) {
            return;
        }

        updateRoomMemory(entity);

        if (player == null || player.isSpectator()) {
            return;
        }

        PlayerMemory data = memory(player);

        observe(entity, player, distance, data);

        // ARGGUY stops playing when the real hunt begins.
        if (hunting || entity.isEscaped()) {
            data.retreatTicks = 0;
            return;
        }

        // He cannot perform tricks while someone is watching him.
        if (frozen) {
            return;
        }

        // Continue an already-started fake retreat.
        if (data.retreatTicks > 0) {
            continueFakeRetreat(entity, player, data);
            return;
        }

        if (distance > 23) {
            return;
        }

        if (--data.actionCooldown > 0) {
            return;
        }

        RandomSource random = ((ServerLevel) entity.level()).getRandom();

        data.actionCooldown = 220 + random.nextInt(260);

        switch (random.nextInt(6)) {

            // He pretends to leave, but keeps watching.
            case 0 -> startFakeRetreat(entity, player, data, random);

            // Appears behind the player after they look away.
            case 1 -> {
                if (distance < 18 && !watchedByAnyone(entity)) {
                    appearBehind(
                            entity,
                            player,
                            7.0 + random.nextDouble() * 3.0
                    );

                    sound(
                            entity,
                            behind(player, 2.0),
                            ModSounds.WHISPER,
                            2.8F,
                            0.9F
                    );
                }
            }

            // Makes a sound somewhere behind the player.
            case 2 -> sound(
                    entity,
                    behind(player, 3.0),
                    ModSounds.KNOCK,
                    3.0F,
                    0.85F
            );

            // Stares directly at the player and laughs.
            case 3 -> {
                faceTowards(entity, player);

                sound(
                        entity,
                        entity.position(),
                        ModSounds.CHUCKLE,
                        2.5F,
                        0.75F
                );
            }

            // Occasionally gives the player unexpected help.
            case 4 -> {
                if (distance < 12
                        && !player.isSprinting()
                        && data.attacks == 0
                        && data.roomEscapes == 0) {
                    helpOnce(entity, player, data);
                }
            }

            // Makes a comment about the player's behavior.
            case 5 -> {
                if (data.sprintTicks > 40) {
                    player.sendSystemMessage(
                            Component.literal(
                                    "ARGGUY: Running already? We haven't even started."
                            )
                    );
                } else if (data.peacefulTicks > 80) {
                    player.sendSystemMessage(
                            Component.literal(
                                    "ARGGUY: You're surprisingly polite."
                            )
                    );
                }
            }
        }
    }

    // ------------------------------------------------------------
    // FAKE RETREAT
    // ------------------------------------------------------------

    private static void startFakeRetreat(
            ArgGuyEntity entity,
            Player player,
            PlayerMemory data,
            RandomSource random
    ) {
        Vec3 away = entity.position().subtract(player.position());
        away = new Vec3(away.x, 0, away.z);

        if (away.lengthSqr() < 0.001) {
            double angle = random.nextDouble() * Math.PI * 2;

            away = new Vec3(
                    Math.cos(angle),
                    0,
                    Math.sin(angle)
            );
        }

        away = away.normalize();

        data.retreatTarget = entity.position().add(
                away.scale(5.0 + random.nextDouble() * 3.0)
        );

        data.retreatTicks = 40;

        sound(
                entity,
                entity.position(),
                ModSounds.CHUCKLE,
                2.2F,
                0.8F
        );
    }

    private static void continueFakeRetreat(
            ArgGuyEntity entity,
            Player player,
            PlayerMemory data
    ) {
        Vec3 delta = data.retreatTarget.subtract(entity.position());

        double distance = Math.sqrt(
                delta.x * delta.x + delta.z * delta.z
        );

        if (distance < 0.15 || data.retreatTicks <= 0) {
            data.retreatTicks = 0;
            entity.setDeltaMovement(Vec3.ZERO);
            return;
        }

        double speed = Math.min(0.18, distance);

        entity.setPos(
                entity.getX() + delta.x / distance * speed,
                entity.getY(),
                entity.getZ() + delta.z / distance * speed
        );

        entity.setDeltaMovement(Vec3.ZERO);
        faceTowards(entity, player);

        if (data.retreatTicks % 10 == 0) {
            sound(
                    entity,
                    entity.position(),
                    ModSounds.STEP,
                    1.5F,
                    0.9F
            );
        }

        data.retreatTicks--;
    }

    // ------------------------------------------------------------
    // UNEXPECTED APPEARANCES
    // ------------------------------------------------------------

    private static boolean watchedByAnyone(ArgGuyEntity entity) {
        for (Player player : entity.level().players()) {
            if (entity.isWatchedBy(player)) {
                return true;
            }
        }

        return false;
    }

    private static void appearBehind(
            ArgGuyEntity entity,
            Player player,
            double distance
    ) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 target = behind(player, distance);

        BlockPos pos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(
                        Mth.floor(target.x),
                        0,
                        Mth.floor(target.z)
                )
        );

        entity.setPos(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        entity.setDeltaMovement(Vec3.ZERO);
        entity.getNavigation().stop();

        faceTowards(entity, player);
    }

    private static Vec3 behind(Player player, double distance) {
        Vec3 look = player.getLookAngle();

        Vec3 horizontal = new Vec3(
                look.x,
                0,
                look.z
        );

        if (horizontal.lengthSqr() < 0.001) {
            horizontal = new Vec3(0, 0, 1);
        }

        horizontal = horizontal.normalize();

        return player.position()
                .subtract(horizontal.scale(distance))
                .add(0, 1, 0);
    }

    // ------------------------------------------------------------
    // HELP
    // ------------------------------------------------------------

    private static void helpOnce(
            ArgGuyEntity entity,
            Player player,
            PlayerMemory data
    ) {
        if (data.helped) {
            return;
        }

        data.helped = true;

        player.addEffect(
                new MobEffectInstance(
                        MobEffects.REGENERATION,
                        100,
                        0,
                        false,
                        false
                )
        );

        player.sendSystemMessage(
                Component.literal(
                        "ARGGUY: There. Don't make me regret being nice."
                )
        );

        sound(
                entity,
                entity.position(),
                ModSounds.CHIME,
                1.8F,
                1.2F
        );
    }

    // ------------------------------------------------------------
    // RIGHT-CLICK CONVERSATIONS
    // ------------------------------------------------------------

    public static InteractionResult interact(
            ArgGuyEntity entity,
            Player player
    ) {
        if (entity.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (entity.isWatcher()) {
            player.sendSystemMessage(
                    Component.literal(
                            "It doesn't respond. Its head follows your movement."
                    )
            );

            return InteractionResult.SUCCESS;
        }

        PlayerMemory data = memory(player);
        data.conversations++;

        faceTowards(entity, player);

        String message;

        if (entity.isHunting()) {
            message = switch (data.conversations % 3) {
                case 0 -> "You picked a terrible time to talk.";
                case 1 -> "Do you really think I am going to stop?";
                default -> "Go on. Keep talking.";
            };
        } else if (data.roomEscapes > 0) {
            message = switch (data.conversations % 3) {
                case 0 -> "You remember that room, don't you?";
                case 1 -> "Getting out doesn't mean you won.";
                default -> "I left the door open. Mostly.";
            };
        } else if (data.attacks > 0) {
            message = switch (data.conversations % 3) {
                case 0 -> "Still angry about that?";
                case 1 -> "You really thought that would work?";
                default -> "I admire the confidence. Not the intelligence.";
            };
        } else if (data.stareTicks > 100) {
            message = switch (data.conversations % 3) {
                case 0 -> "You stare a lot.";
                case 1 -> "Blink. It's starting to get awkward.";
                default -> "I can see you, too.";
            };
        } else if (data.peacefulTicks > 80) {
            message = switch (data.conversations % 3) {
                case 0 -> "You are different from the others.";
                case 1 -> "I almost like you.";
                default -> "Don't get comfortable.";
            };
        } else {
            message = switch ((data.conversations - 1) % 7) {
                case 0 -> "Oh. You can see me.";
                case 1 -> "Did you expect me to say something?";
                case 2 -> "You came all this way just to talk?";
                case 3 -> "I was having a perfectly quiet day.";
                case 4 -> "Do you always follow strangers?";
                case 5 -> "Careful. I might start liking you.";
                default -> "You should probably be doing something else.";
            };
        }

        player.sendSystemMessage(
                Component.literal("ARGGUY: " + message)
        );

        sound(
                entity,
                entity.position(),
                ModSounds.CHUCKLE,
                1.4F,
                0.85F
        );

        if (data.conversations % 4 == 0
                && !entity.isHunting()
                && data.attacks == 0
                && data.roomEscapes == 0) {
            helpOnce(entity, player, data);
        }

        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------
    // SHARED HELPERS
    // ------------------------------------------------------------

    private static void faceTowards(
            ArgGuyEntity entity,
            Player player
    ) {
        Vec3 delta = player.getEyePosition()
                .subtract(entity.getEyePosition());

        float yaw = (float) (
                Mth.atan2(delta.z, delta.x) * 57.29578F
        ) - 90.0F;

        float pitch = (float) -(
                Mth.atan2(
                        delta.y,
                        Math.sqrt(delta.x * delta.x + delta.z * delta.z)
                ) * 57.29578F
        );

        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setXRot(pitch);
    }

    private static void sound(
            ArgGuyEntity entity,
            Vec3 position,
            SoundEvent sound,
            float volume,
            float pitch
    ) {
        entity.level().playSound(
                null,
                position.x,
                position.y,
                position.z,
                sound,
                SoundSource.HOSTILE,
                volume,
                pitch
        );
    }
}
