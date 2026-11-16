
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RedRoom {

    private static final int STAY_TICKS = 900;
    private static final int ROOM_HEIGHT = 8;

    private static final int RED_RADIUS = 10;
    private static final int WHITE_RADIUS = 13;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private RedRoom() {}

    // ------------------------------------------------------------
    // ROOM SESSION
    // ------------------------------------------------------------

    private static final class Session {

        final ServerLevel level;

        final double ox;
        final double oy;
        final double oz;

        final float yaw;
        final float pitch;

        final int cx;
        final int cy;
        final int cz;

        final long enteredAt;

        final boolean white;
        final int radius;
        final int layout;

        // Original blocks are restored when the player exits.
        final Map<BlockPos, BlockState> originalBlocks =
                new HashMap<>();

        // Whiteroom escape tile.
        int exitX;
        int exitZ;
        int escapeProgress;

        // Whiteroom moving structure.
        int pillarX;
        int pillarZ;

        int nextEventAt;

        Session(
                ServerLevel level,
                ServerPlayer player,
                int cx,
                int cy,
                int cz,
                boolean white,
                int radius,
                int layout
        ) {
            this.level = level;

            this.ox = player.getX();
            this.oy = player.getY();
            this.oz = player.getZ();

            this.yaw = player.getYRot();
            this.pitch = player.getXRot();

            this.cx = cx;
            this.cy = cy;
            this.cz = cz;

            this.enteredAt = level.getGameTime();

            this.white = white;
            this.radius = radius;
            this.layout = layout;

            this.nextEventAt =
                    150 + level.getRandom().nextInt(120);
        }
    }

    // ------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------

    public static boolean isIn(UUID id) {
        return SESSIONS.containsKey(id);
    }

    public static boolean isWhite(UUID id) {
        Session s = SESSIONS.get(id);
        return s != null && s.white;
    }

    private static void console(ServerPlayer player, String key) {
        player.sendSystemMessage(
                Component.translatable(key)
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    private static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(
                Component.literal(text)
                        .withStyle(ChatFormatting.DARK_RED)
        );
    }

    private static void whiteMessage(ServerPlayer player, String text) {
        player.sendSystemMessage(
                Component.literal(text)
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    // ------------------------------------------------------------
    // ENTER
    // ------------------------------------------------------------

    public static void enter(ServerPlayer player) {

        if (SESSIONS.containsKey(player.getUUID())) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();

        boolean white = level.getRandom().nextInt(5) == 0;

        int radius = white ? WHITE_RADIUS : RED_RADIUS;

        int cx = (int) Math.floor(player.getX());
        int cz = (int) Math.floor(player.getZ());

        int cy = level.dimension() == Level.OVERWORLD
                ? 240
                : 110;

        // Avoid overlapping other active rooms.
        int[] center = findUnusedCenter(
                level,
                cx,
                cz,
                radius
        );

        cx = center[0];
        cz = center[1];

        int layout = level.getRandom().nextInt(3);

        Session session = new Session(
                level,
                player,
                cx,
                cy,
                cz,
                white,
                radius,
                layout
        );

        if (white) {
            prepareWhiteRoom(session);
        } else {
            prepareRedRoom(session);
        }

        SESSIONS.put(player.getUUID(), session);

        // Teleport player.
        player.teleportTo(
                cx + 0.5,
                cy + 1,
                cz + 0.5
        );

        // Initial visual reveal.
        player.addEffect(
                new MobEffectInstance(
                        MobEffects.DARKNESS,
                        white ? 120 : 65,
                        0,
                        false,
                        false
                )
        );

        if (white) {
            whiteMessage(
                    player,
                    "..."
            );

            whiteMessage(
                    player,
                    "There is something here."
            );
        } else {
            console(player, "console.argguy.room1");
            console(player, "console.argguy.room2");

            message(
                    player,
                    "You hear something moving behind the walls."
            );
        }
    }

    private static int[] findUnusedCenter(
            ServerLevel level,
            int cx,
            int cz,
            int radius
    ) {
        int attempts = 0;

        while (attempts++ < 20) {

            boolean overlapping = false;

            for (Session other : SESSIONS.values()) {

                if (other.level != level) {
                    continue;
                }

                int minimum = radius + other.radius + 5;

                if (Math.abs(cx - other.cx) < minimum
                        && Math.abs(cz - other.cz) < minimum) {

                    overlapping = true;
                    break;
                }
            }

            if (!overlapping) {
                break;
            }

            cx += radius * 2 + 10;
        }

        return new int[]{cx, cz};
    }

    // ------------------------------------------------------------
    // BLOCK PLACEMENT AND RESTORATION
    // ------------------------------------------------------------

    private static void place(
            Session s,
            int x,
            int y,
            int z,
            BlockState state
    ) {
        BlockPos pos = new BlockPos(
                s.cx + x,
                s.cy + y,
                s.cz + z
        );

        s.originalBlocks.putIfAbsent(
                pos,
                s.level.getBlockState(pos)
        );

        s.level.setBlock(pos, state, 3);
    }

    private static void place(
            Session s,
            int x,
            int y,
            int z,
            net.minecraft.world.level.block.Block block
    ) {
        place(s, x, y, z, block.defaultBlockState());
    }

    // ------------------------------------------------------------
    // COMMON ROOM SHELL
    // ------------------------------------------------------------

    private static void buildShell(Session s) {

        int r = s.radius;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {

                for (int y = 0; y < ROOM_HEIGHT; y++) {

                    boolean floor = y == 0;
                    boolean ceiling = y == ROOM_HEIGHT - 1;

                    boolean wall =
                            Math.abs(x) == r
                            || Math.abs(z) == r;

                    BlockState state = Blocks.AIR.defaultBlockState();

                    if (s.white) {

                        if (floor) {
                            state = Blocks.WHITE_CONCRETE.defaultBlockState();

                        } else if (ceiling || wall) {
                            state = Blocks.WHITE_CONCRETE.defaultBlockState();
                        }

                    } else {

                        if (floor) {

                            if ((x + z) % 5 == 0) {
                                state = Blocks.BLACK_CONCRETE.defaultBlockState();
                            } else {
                                state = Blocks.RED_CONCRETE.defaultBlockState();
                            }

                        } else if (ceiling) {

                            state = Blocks.BLACK_CONCRETE.defaultBlockState();

                        } else if (wall) {

                            if (y == 2 || y == 5) {
                                state = Blocks.BLACK_CONCRETE.defaultBlockState();
                            } else {
                                state = Blocks.RED_CONCRETE.defaultBlockState();
                            }
                        }
                    }

                    place(s, x, y, z, state);
                }
            }
        }

        place(
                s,
                0,
                5,
                0,
                Blocks.SEA_LANTERN
        );
    }

    // ------------------------------------------------------------
    // RED ROOM
    // ------------------------------------------------------------

    private static void prepareRedRoom(Session s) {

        buildShell(s);

        switch (s.layout) {
            case 0 -> buildInterrogationRoom(s);
            case 1 -> buildCorridorRoom(s);
            default -> buildObservationRoom(s);
        }

        buildFalseDoor(s);
        spawnWatchers(s);
    }

    // Layout 1: interrogation chamber.
    private static void buildInterrogationRoom(Session s) {

        // Central raised platform.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {

                place(
                        s,
                        x,
                        1,
                        z,
                        Blocks.BLACK_CONCRETE
                );
            }
        }

        // Central pillar.
        for (int y = 1; y <= 4; y++) {
            place(
                    s,
                    0,
                    y,
                    0,
                    Blocks.CRYING_OBSIDIAN
            );
        }

        // Four surrounding columns.
        buildRedColumn(s, -6, -6);
        buildRedColumn(s, 6, -6);
        buildRedColumn(s, -6, 6);
        buildRedColumn(s, 6, 6);
    }

    private static void buildRedColumn(
            Session s,
            int x,
            int z
    ) {
        for (int y = 1; y <= 5; y++) {
            place(
                    s,
                    x,
                    y,
                    z,
                    y == 5
                            ? Blocks.REDSTONE_BLOCK
                            : Blocks.CRYING_OBSIDIAN
            );
        }
    }

    // Layout 2: corridor labyrinth.
    private static void buildCorridorRoom(Session s) {

        // First wall, with an offset gap.
        for (int z = -8; z <= 8; z++) {

            if (z == 2 || z == 3) {
                continue;
            }

            for (int y = 1; y <= 4; y++) {
                place(
                        s,
                        -3,
                        y,
                        z,
                        Blocks.RED_CONCRETE
                );
            }
        }

        // Second wall, with a different gap.
        for (int z = -8; z <= 8; z++) {

            if (z == -3 || z == -2) {
                continue;
            }

            for (int y = 1; y <= 4; y++) {
                place(
                        s,
                        3,
                        y,
                        z,
                        Blocks.BLACK_CONCRETE
                );
            }
        }

        // Short perpendicular partitions.
        for (int x = -8; x <= 8; x++) {

            if (x == -5 || x == 5) {
                continue;
            }

            for (int y = 1; y <= 3; y++) {
                place(
                        s,
                        x,
                        y,
                        6,
                        Blocks.RED_CONCRETE
                );
            }
        }
    }

    // Layout 3: observation chamber.
    private static void buildObservationRoom(Session s) {

        // A larger centre platform.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {

                place(
                        s,
                        x,
                        1,
                        z,
                        Blocks.CRYING_OBSIDIAN
                );
            }
        }

        // Four tall pillars.
        int[][] pillars = {
                {-7, -7},
                {7, -7},
                {-7, 7},
                {7, 7}
        };

        for (int[] pillar : pillars) {

            for (int y = 1; y <= 6; y++) {

                place(
                        s,
                        pillar[0],
                        y,
                        pillar[1],
                        Blocks.BLACK_CONCRETE
                );
            }
        }

        // Long dark strips across the ceiling.
        for (int x = -8; x <= 8; x++) {
            place(s, x, 6, 0, Blocks.BLACK_CONCRETE);
        }

        for (int z = -8; z <= 8; z++) {
            place(s, 0, 6, z, Blocks.BLACK_CONCRETE);
        }
    }

    // A fake doorway built into one of the walls.
    private static void buildFalseDoor(Session s) {

        int side = s.level.getRandom().nextInt(4);
        int x = 0;
        int z = 0;

        switch (side) {
            case 0 -> x = -s.radius;
            case 1 -> x = s.radius;
            case 2 -> z = -s.radius;
            default -> z = s.radius;
        }

        for (int y = 1; y <= 4; y++) {
            for (int offset = -1; offset <= 1; offset++) {

                int px = x;
                int pz = z;

                if (side < 2) {
                    pz += offset;
                } else {
                    px += offset;
                }

                place(
                        s,
                        px,
                        y,
                        pz,
                        Blocks.BLACK_CONCRETE
                );
            }
        }
    }

    // ------------------------------------------------------------
    // WATCHERS
    // ------------------------------------------------------------

    private static void spawnWatchers(Session s) {

        int r = s.radius - 2;

        int[][] positions = {
                {-r, -r},
                {r, -r},
                {-r, r},
                {r, r}
        };

        for (int[] position : positions) {

            ArgGuyEntity watcher =
                    new ArgGuyEntity(ArgGuyMod.ARGGUY, s.level);

            watcher.makeRoomWatcher();

            watcher.setPos(
                    s.cx + position[0] + 0.5,
                    s.cy + 1,
                    s.cz + position[1] + 0.5
            );

            s.level.addFreshEntity(watcher);
        }
    }

    // ------------------------------------------------------------
    // WHITEROOM
    // ------------------------------------------------------------

    private static void prepareWhiteRoom(Session s) {

        buildShell(s);

        // Four distant, identical structures.
        int[][] structures = {
                {-8, -8},
                {8, -8},
                {-8, 8},
                {8, 8}
        };

        for (int[] structure : structures) {
            buildWhiteStructure(
                    s,
                    structure[0],
                    structure[1]
            );
        }

        // A strange moving structure.
        int[][] possiblePillars = {
                {0, -10},
                {10, 0},
                {0, 10},
                {-10, 0}
        };

        int selected = s.level.getRandom().nextInt(4);

        s.pillarX = possiblePillars[selected][0];
        s.pillarZ = possiblePillars[selected][1];

        buildMovingPillar(
                s,
                s.pillarX,
                s.pillarZ
        );

        // One tile is different from the rest.
        int direction = s.level.getRandom().nextInt(4);

        switch (direction) {
            case 0 -> {
                s.exitX = 8 + s.level.getRandom().nextInt(3);
                s.exitZ = 2 + s.level.getRandom().nextInt(5);
            }
            case 1 -> {
                s.exitX = -8 - s.level.getRandom().nextInt(3);
                s.exitZ = -2 - s.level.getRandom().nextInt(5);
            }
            case 2 -> {
                s.exitX = 2 + s.level.getRandom().nextInt(5);
                s.exitZ = -8 - s.level.getRandom().nextInt(3);
            }
            default -> {
                s.exitX = -2 - s.level.getRandom().nextInt(5);
                s.exitZ = 8 + s.level.getRandom().nextInt(3);
            }
        }

        place(
                s,
                s.exitX,
                0,
                s.exitZ,
                Blocks.PURPLE_CONCRETE
        );
    }

    private static void buildWhiteStructure(
            Session s,
            int x,
            int z
    ) {
        for (int y = 1; y <= 5; y++) {

            place(
                    s,
                    x,
                    y,
                    z,
                    Blocks.WHITE_CONCRETE
            );
        }

        // Small top piece.
        place(
                s,
                x,
                6,
                z,
                Blocks.QUARTZ_BLOCK
        );
    }

    private static void buildMovingPillar(
            Session s,
            int x,
            int z
    ) {
        for (int y = 1; y <= 4; y++) {

            place(
                    s,
                    x,
                    y,
                    z,
                    Blocks.BLACK_CONCRETE
            );
        }

        place(
                s,
                x,
                5,
                z,
                Blocks.WHITE_CONCRETE
        );
    }

    private static boolean lookingAtPillar(
            Session s,
            ServerPlayer player
    ) {

        Vec3 target = new Vec3(
                s.cx + s.pillarX + 0.5,
                s.cy + 3.5,
                s.cz + s.pillarZ + 0.5
        );

        Vec3 direction = target
                .subtract(player.getEyePosition())
                .normalize();

        return player.getLookAngle()
                .normalize()
                .dot(direction) > 0.88;
    }

    private static void moveWhitePillar(Session s) {

        // Remove old pillar.
        for (int y = 1; y <= 5; y++) {

            place(
                    s,
                    s.pillarX,
                    y,
                    s.pillarZ,
                    Blocks.AIR
            );
        }

        int[][] positions = {
                {0, -10},
                {10, 0},
                {0, 10},
                {-10, 0}
        };

        int index = s.level.getRandom().nextInt(4);

        for (int attempts = 0;
             attempts < 5
                     && positions[index][0] == s.pillarX
                     && positions[index][1] == s.pillarZ;
             attempts++) {

            index = s.level.getRandom().nextInt(4);
        }

        s.pillarX = positions[index][0];
        s.pillarZ = positions[index][1];

        buildMovingPillar(
                s,
                s.pillarX,
                s.pillarZ
        );
    }

    // ------------------------------------------------------------
    // RED ROOM EVENTS
    // ------------------------------------------------------------

    private static void redRoomEvent(
            Session s,
            ServerPlayer player
    ) {

        int event = s.level.getRandom().nextInt(4);

        switch (event) {

            // Banging from a random wall.
            case 0 -> {

                int side = s.level.getRandom().nextInt(4);

                double x = s.cx;
                double z = s.cz;

                if (side == 0) x -= s.radius - 1;
                if (side == 1) x += s.radius - 1;
                if (side == 2) z -= s.radius - 1;
                if (side == 3) z += s.radius - 1;

                s.level.playSound(
                        null,
                        x,
                        s.cy + 3,
                        z,
                        ModSounds.VOICE_BANGING,
                        SoundSource.HOSTILE,
                        2.5F,
                        0.85F + s.level.getRandom().nextFloat() * 0.3F
                );

                message(
                        player,
                        "Something hit the other side of the wall."
                );
            }

            // The watchers change their positions when unseen.
            case 1 -> {

                AABB box = roomBox(s);

                for (ArgGuyEntity watcher :
                        s.level.getEntitiesOfClass(
                                ArgGuyEntity.class,
                                box
                        )) {

                    if (!watcher.entityTags().contains(
                            ArgGuyEntity.ROOM_TAG
                    )) {
                        continue;
                    }

                    if (watcher.isWatchedBy(player)) {
                        continue;
                    }

                    int[][] corners = {
                            {-s.radius + 2, -s.radius + 2},
                            {s.radius - 2, -s.radius + 2},
                            {-s.radius + 2, s.radius - 2},
                            {s.radius - 2, s.radius - 2}
                    };

                    int[] destination = corners[
                            s.level.getRandom().nextInt(corners.length)
                    ];

                    watcher.setPos(
                            s.cx + destination[0] + 0.5,
                            s.cy + 1,
                            s.cz + destination[1] + 0.5
                    );
                }

                message(
                        player,
                        "You feel like something has moved."
                );
            }

            // Wall patterns change.
            case 2 -> {

                int side = s.level.getRandom().nextInt(4);
                int offset = s.level.getRandom().nextInt(9) - 4;

                for (int y = 1; y <= 4; y++) {

                    for (int width = -1; width <= 1; width++) {

                        int x = 0;
                        int z = 0;

                        switch (side) {
                            case 0 -> {
                                x = -s.radius + 1;
                                z = offset + width;
                            }
                            case 1 -> {
                                x = s.radius - 1;
                                z = offset + width;
                            }
                            case 2 -> {
                                x = offset + width;
                                z = -s.radius + 1;
                            }
                            default -> {
                                x = offset + width;
                                z = s.radius - 1;
                            }
                        }

                        place(
                                s,
                                x,
                                y,
                                z,
                                Blocks.BLACK_CONCRETE
                        );
                    }
                }

                message(
                        player,
                        "The walls don't look the same anymore."
                );
            }

            // Knock from behind.
            default -> {

                Vec3 look = player.getLookAngle();

                double x = player.getX() - look.x * 5.0;
                double y = player.getY() + 1.0;
                double z = player.getZ() - look.z * 5.0;

                s.level.playSound(
                        null,
                        x,
                        y,
                        z,
                        ModSounds.KNOCK,
                        SoundSource.HOSTILE,
                        2.5F,
                        0.8F
                );

                message(
                        player,
                        "There is someone behind you."
                );
            }
        }
    }

    // ------------------------------------------------------------
    // WHITEROOM EVENTS
    // ------------------------------------------------------------

    private static void whiteRoomEvent(
            Session s,
            ServerPlayer player
    ) {

        // The structure moves only when the player isn't watching it.
        if (!lookingAtPillar(s, player)) {

            moveWhitePillar(s);

            whiteMessage(
                    player,
                    "Something is different."
            );

        } else {

            // Give the player time to look elsewhere.
            s.nextEventAt += 50;
        }
    }

    // ------------------------------------------------------------
    // SERVER TICK
    // ------------------------------------------------------------

    public static void tick(MinecraftServer server) {

        if (SESSIONS.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Session> entry :
                new ArrayList<>(SESSIONS.entrySet())) {

            UUID id = entry.getKey();
            Session s = entry.getValue();

            long t = s.level.getGameTime() - s.enteredAt;

            ServerPlayer player =
                    server.getPlayerList().getPlayer(id);

            if (player == null) {

                if (t >= STAY_TICKS) {
                    exit(id, server);
                }

                continue;
            }

            // ----------------------------------------------------
            // RED ROOM
            // ----------------------------------------------------

            if (!s.white) {

                if (t == 200) {
                    console(player, "console.argguy.room3");
                }

                if (t == 450) {

                    s.level.playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            ModSounds.VOICE_BANGING,
                            SoundSource.HOSTILE,
                            2.4F,
                            1.0F
                    );
                }

                if (t == STAY_TICKS - 60) {

                    s.level.playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            ModSounds.CONNECTION_LOST,
                            SoundSource.HOSTILE,
                            2.0F,
                            1.0F
                    );

                    console(player, "console.argguy.lost");
                }

                if (t >= s.nextEventAt) {

                    redRoomEvent(s, player);

                    s.nextEventAt = (int) t
                            + 140
                            + s.level.getRandom().nextInt(160);
                }
            }

            // ----------------------------------------------------
            // WHITEROOM
            // ----------------------------------------------------

            else {

                if (t == 200) {
                    whiteMessage(
                            player,
                            "Look carefully. One thing doesn't belong."
                    );
                }

                if (t == 450) {
                    whiteMessage(
                            player,
                            "There is no echo."
                    );
                }

                if (t >= s.nextEventAt) {

                    whiteRoomEvent(s, player);

                    s.nextEventAt = (int) t
                            + 150
                            + s.level.getRandom().nextInt(130);
                }

                // Hidden early escape condition.
                BlockPos playerPos = player.blockPosition();

                boolean standingOnExit =
                        playerPos.getX() == s.cx + s.exitX
                        && playerPos.getZ() == s.cz + s.exitZ
                        && playerPos.getY() <= s.cy + 2;

                if (standingOnExit) {

                    s.escapeProgress++;

                    if (s.escapeProgress == 20) {
                        whiteMessage(
                                player,
                                "Something feels wrong beneath your feet."
                        );
                    }

                    if (s.escapeProgress >= 60) {

                        whiteMessage(
                                player,
                                "..."
                        );

                        exit(id, server);
                        continue;
                    }

                } else {
                    s.escapeProgress = 0;
                }
            }

            // Automatic return after 45 seconds.
            if (t >= STAY_TICKS) {
                exit(id, server);
            }
        }
    }

    // ------------------------------------------------------------
    // ROOM BOUNDS
    // ------------------------------------------------------------

    private static AABB roomBox(Session s) {

        return new AABB(
                s.cx - s.radius - 2,
                s.cy - 1,
                s.cz - s.radius - 2,
                s.cx + s.radius + 3,
                s.cy + ROOM_HEIGHT + 2,
                s.cz + s.radius + 3
        );
    }

    // ------------------------------------------------------------
    // EXIT AND RESTORATION
    // ------------------------------------------------------------

    public static void exit(UUID id, MinecraftServer server) {

        Session s = SESSIONS.remove(id);

        if (s == null) {
            return;
        }

        ServerLevel level = s.level;

        // Remove room watcher entities.
        for (ArgGuyEntity watcher :
                level.getEntitiesOfClass(
                        ArgGuyEntity.class,
                        roomBox(s)
                )) {

            if (watcher.entityTags().contains(
                    ArgGuyEntity.ROOM_TAG
            )) {
                watcher.discard();
            }
        }

        // Restore the actual blocks that existed before room creation.
        for (Map.Entry<BlockPos, BlockState> entry :
                s.originalBlocks.entrySet()) {

            level.setBlock(
                    entry.getKey(),
                    entry.getValue(),
                    3
            );
        }

        // Return player to original location.
        ServerPlayer player =
                server.getPlayerList().getPlayer(id);

        if (player != null) {

            player.removeEffect(MobEffects.DARKNESS);

            player.teleportTo(
                    s.ox,
                    s.oy,
                    s.oz
            );

            player.setYRot(s.yaw);
            player.setXRot(s.pitch);
            player.setYHeadRot(s.yaw);
            player.setYBodyRot(s.yaw);

            if (s.white) {
                whiteMessage(
                        player,
                        "You are somewhere familiar again."
                );
            } else {
                console(player, "console.argguy.back");
            }
        }
    }
}
