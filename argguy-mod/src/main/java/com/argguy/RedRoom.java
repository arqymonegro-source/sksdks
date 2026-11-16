
package com.argguy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Red Room: where ARGGUY takes players when it catches them.
 * A sealed room built high above the world, watched by silent copies.
 * After 45 seconds, the player is returned to their original position.
 *
 * 20% chance of generating the Whiteroom instead.
 */
public final class RedRoom {

    private static final int STAY_TICKS = 900;

    private record Session(
            ServerLevel level,
            double ox,
            double oy,
            double oz,
            float yaw,
            float pitch,
            int cx,
            int cy,
            int cz,
            long enteredAt
    ) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private RedRoom() {}

    public static boolean isIn(UUID id) {
        return SESSIONS.containsKey(id);
    }

    private static void console(ServerPlayer p, String key) {
        p.sendSystemMessage(
                Component.translatable(key).withStyle(ChatFormatting.GRAY)
        );
    }

    // ------------------------------------------------------------
    // Enter the Red Room
    // ------------------------------------------------------------

    public static void enter(ServerPlayer p) {
        if (SESSIONS.containsKey(p.getUUID())) return;

        ServerLevel level = (ServerLevel) p.level();

        int cx = (int) Math.floor(p.getX());
        int cz = (int) Math.floor(p.getZ());

        int cy = level.dimension() == Level.OVERWORLD ? 240 : 110;

        boolean white = level.getRandom().nextInt(5) == 0;

        // Generate the room.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 5; dy++) {

                    boolean shell =
                            dx == -4 || dx == 4
                            || dz == -4 || dz == 4
                            || dy == 0 || dy == 5;

                    BlockState st = Blocks.AIR.defaultBlockState();

                    if (shell) {
                        if (white) {
                            st = (dy == 0)
                                    ? (((dx + dz) & 1) == 0
                                            ? Blocks.PURPLE_CONCRETE
                                            : Blocks.BLACK_CONCRETE
                                    ).defaultBlockState()
                                    : Blocks.WHITE_CONCRETE.defaultBlockState();
                        } else {
                            st = Blocks.RED_CONCRETE.defaultBlockState();
                        }
                    }

                    level.setBlock(
                            new BlockPos(cx + dx, cy + dy, cz + dz),
                            st,
                            3
                    );
                }
            }
        }

        // Ceiling light.
        level.setBlock(
                new BlockPos(cx, cy + 5, cz),
                (white
                        ? Blocks.SEA_LANTERN
                        : Blocks.CRYING_OBSIDIAN
                ).defaultBlockState(),
                3
        );

        // Four silent watchers in the corners.
        int[][] corners = {
                {-3, -3},
                {3, -3},
                {-3, 3},
                {3, 3}
        };

        for (int[] c : corners) {
            ArgGuyEntity w = new ArgGuyEntity(ArgGuyMod.ARGGUY, level);

            w.makeRoomWatcher();

            w.setPos(
                    cx + c[0] + 0.5,
                    cy + 1,
                    cz + c[1] + 0.5
            );

            level.addFreshEntity(w);
        }

        // Save the player's original position.
        SESSIONS.put(
                p.getUUID(),
                new Session(
                        level,
                        p.getX(),
                        p.getY(),
                        p.getZ(),
                        p.getYRot(),
                        p.getXRot(),
                        cx,
                        cy,
                        cz,
                        level.getGameTime()
                )
        );

        // Teleport into the room.
        p.teleportTo(cx + 0.5, cy + 1, cz + 0.5);

        // Darkness effect.
        p.addEffect(
                new MobEffectInstance(
                        MobEffects.DARKNESS,
                        STAY_TICKS,
                        0,
                        false,
                        false
                )
        );

        console(p, "console.argguy.room1");
        console(p, "console.argguy.room2");
    }

    // ------------------------------------------------------------
    // Server tick
    // ------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;

        for (Map.Entry<UUID, Session> e :
                new ArrayList<>(SESSIONS.entrySet())) {

            Session s = e.getValue();

            long t = s.level().getGameTime() - s.enteredAt();

            ServerPlayer p =
                    server.getPlayerList().getPlayer(e.getKey());

            if (p != null) {

                // First message.
                if (t == 200) {
                    console(p, "console.argguy.room3");
                }

                // Banging sound.
                if (t == 450) {
                    p.level().playSound(
                            null,
                            p.getX(),
                            p.getY(),
                            p.getZ(),
                            ModSounds.VOICE_BANGING,
                            SoundSource.HOSTILE,
                            2.4F,
                            1.0F
                    );
                }

                // Connection lost sound and message.
                if (t == STAY_TICKS - 60) {
                    p.level().playSound(
                            null,
                            p.getX(),
                            p.getY(),
                            p.getZ(),
                            ModSounds.CONNECTION_LOST,
                            SoundSource.HOSTILE,
                            2.0F,
                            1.0F
                    );

                    console(p, "console.argguy.lost");
                }
            }

            // Automatically exit after 45 seconds.
            if (t >= STAY_TICKS) {
                exit(e.getKey(), server);
            }
        }
    }

    // ------------------------------------------------------------
    // Exit and cleanup
    // ------------------------------------------------------------

    public static void exit(UUID id, MinecraftServer server) {
        Session s = SESSIONS.remove(id);

        if (s == null) return;

        ServerLevel level = s.level();

        // Remove room watchers.
        AABB box = new AABB(
                s.cx() - 6,
                s.cy() - 1,
                s.cz() - 6,
                s.cx() + 7,
                s.cy() + 8,
                s.cz() + 7
        );

        for (ArgGuyEntity w :
                level.getEntitiesOfClass(ArgGuyEntity.class, box)) {

            if (w.entityTags().contains(ArgGuyEntity.ROOM_TAG)) {
                w.discard();
            }
        }

        // Remove the room blocks.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 5; dy++) {

                    level.setBlock(
                            new BlockPos(
                                    s.cx() + dx,
                                    s.cy() + dy,
                                    s.cz() + dz
                            ),
                            Blocks.AIR.defaultBlockState(),
                            3
                    );
                }
            }
        }

        // Return the player.
        ServerPlayer p =
                server.getPlayerList().getPlayer(id);

        if (p != null) {
            p.teleportTo(
                    s.ox(),
                    s.oy(),
                    s.oz()
            );

            console(p, "console.argguy.back");
        }
    }
}
