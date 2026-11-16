package com.argguy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * ARGGUY - an "Umbrella Man"-style interloper (see the Interloper Gmod ARG).
 *
 * Interloper behaviour it copies:
 *  - appears with a wind chime; operates in groups (silent watchers stand far away);
 *  - floats through the world as if noclipping while it hunts (walls mean nothing);
 *  - catches you and DRAGS YOU OUT OF THE MAP into the Red Room (see RedRoom);
 *  - "model mutilation": while hunting its model stretches and warps (client side, slow, no flicker);
 *  - Source-engine console text in chat: hint nodes, demo recording, CONNECTION LOST.
 * Plus the ARGGUY rules from the YouTube ARG:
 *  - holds perfectly still while anyone looks straight at it;
 *  - invulnerable; only the friends' Containment Drive can "compress" it (and that goes badly).
 *
 * PHOTOSENSITIVITY: nothing here flashes or strobes. The only screen effect is vanilla Darkness (slow pulse).
 */
public class ArgGuyEntity extends Monster {
    public static final String ESCAPED_TAG = "argguy_escaped";
    public static final String WATCHER_TAG = "argguy_watcher";   // silent, motionless group member
    public static final String ROOM_TAG = "argguy_room";         // watcher inside the Red Room

    private static final int HUNT_LENGTH = 1500;      // 75 s
    private static final int HUNT_COOLDOWN = 3600;    // 3 min between hunts
    private static final double WATCH_DOT = 0.96;

    private int huntTicks;
    private int whistleIn = -1;
    private int huntCooldown = 100;
    private boolean frozen;
    private boolean wasFrozen;

    private int breathCd = 20;
    private int ambientBlock = 400;
    private int heartCd;
    private int tinnitusCd;
    private int darkCd;
    private int shriekCd = 80;
    private int stingCd;
    private int stepCd;
    private int watcherLife;

    private int followLeft;
    private int followTick;
    private int followIndex;

    private int captureProgress;
    private long lastCaptureTick;

    public ArgGuyEntity(EntityType<? extends ArgGuyEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 250.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 64.0F));
        // no melee: it does not hurt you, it CAPTURES you (see capture())
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ------------------------------------------------------------------ public hooks

    public boolean isEscaped() { return this.entityTags().contains(ESCAPED_TAG); }
    public boolean isWatcher() { return this.entityTags().contains(WATCHER_TAG); }
    public boolean isHunting() { return !isWatcher() && (huntTicks > 0 || isEscaped()); }

    public void forceHunt() {
        if (!isHunting() && whistleIn < 0 && !isWatcher()) beginHunt();
    }

    public void forceEscape(Player nearTo) {
        if (!level().isClientSide()) escape(nearTo);
    }

    /** Turns this instance into a silent, motionless Red Room watcher. */
    public void makeRoomWatcher() {
        addTag(WATCHER_TAG);
        addTag(ROOM_TAG);
        setNoAi(true);
        setCustomName(Component.literal("CAPTURE"));
        setCustomNameVisible(true);
    }

    public boolean isWatchedBy(Player p) {
        if (p.isSpectator()) return false;
        Vec3 to = this.getEyePosition().subtract(p.getEyePosition());
        double d = to.length();
        if (d < 0.01) return true;
        Vec3 view = p.getViewVector(1.0F).normalize();
        return view.dot(to.scale(1.0 / d)) > WATCH_DOT && p.hasLineOfSight(this);
    }

    /** The friends' Containment Drive: only works while the user is watching it. 5 passes. */
    public boolean tryCapture(Player by) {
        if (level().isClientSide() || isWatcher() || !isWatchedBy(by)) return false;
        long now = level().getGameTime();
        if (now - lastCaptureTick < 20) return true;
        lastCaptureTick = now;
        captureProgress += 20;
        say(ModSounds.CRACKLE, 2.5F, 0.7F + captureProgress / 250.0F);
        by.sendOverlayMessage(Component.translatable("message.argguy.compressing", Math.min(100, captureProgress)));
        if (captureProgress >= 100) escape(by);
        return true;
    }

    // ------------------------------------------------------------------ the ending

    private void escape(Player near) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer pl : sl.players()) {
            pl.sendSystemMessage(Component.translatable("message.argguy.crash1"));
            pl.sendSystemMessage(Component.translatable("message.argguy.crash2"));
            pl.sendSystemMessage(Component.translatable("message.argguy.crash3"));
            pl.sendSystemMessage(Component.translatable("message.argguy.crash4"));
        }
        say(ModSounds.SHRIEK, 6.0F, 0.6F);
        for (int i = 0; i < 5; i++) {
            ArgGuyEntity clone = spawnNear(sl, near, 14, 26);
            clone.addTag(ESCAPED_TAG);
            clone.setPersistenceRequired();
        }
        this.discard();
    }

    public static ArgGuyEntity spawnNear(ServerLevel level, Player around, double min, double max) {
        RandomSource r = level.getRandom();
        double ang = r.nextDouble() * Math.PI * 2.0;
        double dist = min + r.nextDouble() * (max - min);
        int x = Mth.floor(around.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(around.getZ() + Math.sin(ang) * dist);
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        ArgGuyEntity e = new ArgGuyEntity(ArgGuyMod.ARGGUY, level);
        e.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        e.setPersistenceRequired();
        level.addFreshEntity(e);
        return e;
    }

    // ------------------------------------------------------------------ helpers

    private void say(SoundEvent s, float vol, float pitch) {
        level().playSound(null, getX(), getEyeY(), getZ(), s, SoundSource.HOSTILE, vol, pitch);
    }

    private void sayAt(Vec3 pos, SoundEvent s, float vol, float pitch) {
        level().playSound(null, pos.x, pos.y, pos.z, s, SoundSource.HOSTILE, vol, pitch);
    }

    private static void console(Player p, String key) {
        p.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    private static Vec3 behind(Player p, double dist, double up) {
        Vec3 look = p.getLookAngle();
        Vec3 h = new Vec3(look.x, 0, look.z);
        h = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
        return p.position().subtract(h.scale(dist)).add(0, up, 0);
    }

    private void faceTowards(Player p) {
        Vec3 d = p.getEyePosition().subtract(getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * 57.29578F) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 57.29578F);
        setYRot(yaw);
        setYHeadRot(yaw);
        setXRot(pitch);
        setYBodyRot(yaw);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        boolean run = isHunting() && getDeltaMovement().horizontalDistanceSqr() > 0.012;
        this.playSound(run ? ModSounds.STEP_RUN : ModSounds.STEP, run ? 1.8F : 1.4F, 0.85F + random.nextFloat() * 0.15F);
    }

    // ------------------------------------------------------------------ hunt

    private void beginHunt() {
        say(ModSounds.CHIME, 3.2F, 0.95F + random.nextFloat() * 0.1F);   // the wind chime: it has arrived
        whistleIn = 55;
        if (level() instanceof ServerLevel sl) {
            Player p = sl.getNearestPlayer(this, 64.0);
            if (p != null) {
                console(p, "console.argguy.hunt");
                // it operates in groups: one or two silent watchers far away
                int n = 1 + random.nextInt(2);
                for (int i = 0; i < n; i++) {
                    ArgGuyEntity w = spawnNear(sl, p, 28, 44);
                    w.addTag(WATCHER_TAG);
                    w.setNoAi(true);
                }
            }
        }
    }

    private void setNoclip(boolean on) {
        if (this.noPhysics == on) return;
        this.noPhysics = on;
        this.setNoGravity(on);
        if (!on) {
            // it may have drifted into stone: nudge it up until it is free
            for (int i = 0; i < 24 && !level().noCollision(this); i++) setPos(getX(), getY() + 1.0, getZ());
        }
    }

    /** Floats straight at the player through anything. Close enough = capture. */
    private void noclipChase(Player p) {
        Vec3 to = p.position().subtract(position());
        double dist = to.length();
        getLookControl().setLookAt(p, 60.0F, 60.0F);
        if (dist > 1.5) {
            Vec3 mv = to.scale(0.32 / dist);
            setPos(getX() + mv.x, getY() + mv.y, getZ() + mv.z);
            setDeltaMovement(mv);
            if (--stepCd <= 0) {                     // heavy footfalls even while it glides
                say(ModSounds.STEP_RUN, 2.2F, 0.85F + random.nextFloat() * 0.15F);
                stepCd = 7;
            }
        } else if (p instanceof ServerPlayer sp && !RedRoom.isIn(sp.getUUID())) {
            capture(sp);
        }
    }

    /** Drag the player out of the map. Then it relocates itself far away. */
    private void capture(ServerPlayer sp) {
        console(sp, "console.argguy.capture1");
        console(sp, "console.argguy.capture2");
        console(sp, "console.argguy.capture3");
        say(ModSounds.CAPTURE, 5.0F, 1.0F);
        RedRoom.enter(sp);

        if (level() instanceof ServerLevel sl) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            int x = Mth.floor(sp.getX() + Math.cos(ang) * 70.0);
            int z = Mth.floor(sp.getZ() + Math.sin(ang) * 70.0);
            BlockPos pos = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
        huntTicks = 0;
        whistleIn = -1;
        huntCooldown = HUNT_COOLDOWN * 2;
        setTarget(null);
    }

    // ------------------------------------------------------------------ brain

    @Override
    public void tick() {
        final double ox = getX(), oy = getY(), oz = getZ();
        super.tick();
        if (level().isClientSide()) return;

        if (isWatcher()) { tickWatcher(); return; }

        Player p = level().getNearestPlayer(this, 64.0);
        Player watcher = null;
        for (Player pl : level().players()) {
            if (distanceToSqr(pl) < 48.0 * 48.0 && isWatchedBy(pl)) { watcher = pl; break; }
        }
        frozen = watcher != null;
        boolean newlyWatched = frozen && !wasFrozen;
        wasFrozen = frozen;
        stingCd--;

        if (p == null) {
            huntTicks = 0;
            whistleIn = -1;
            if (getTarget() != null) setTarget(null);
        } else {
            double d = distanceTo(p);
            boolean escaped = isEscaped();

            if (newlyWatched && stingCd <= 0 && distanceTo(watcher) < 22.0F) {
                stingCd = 1200;
                say(ModSounds.STING, 4.0F, 1.0F);
            }

            huntCooldown--;
            if (!escaped && huntTicks <= 0 && whistleIn < 0 && huntCooldown <= 0 && d < 30.0) beginHunt();
            if (whistleIn >= 0 && --whistleIn < 0) {
                say(ModSounds.WHISTLE, 6.0F, 0.97F + random.nextFloat() * 0.06F);
                huntTicks = HUNT_LENGTH;
                huntCooldown = HUNT_COOLDOWN;
            }
            if (huntTicks > 0) huntTicks--;

            boolean hunt = isHunting();
            Objects.requireNonNull(getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.20);

            if (hunt && !frozen) {
                noclipChase(p);
            } else if (!hunt && !frozen) {
                if (d > 18.0) getNavigation().moveTo(p, 0.8);
                else getNavigation().stop();
                getLookControl().setLookAt(p, 60.0F, 60.0F);
            }

            sounds(p, d, hunt);
            playerOnlyEffects(p, d, hunt);
        }

        boolean hunting = isHunting() && p != null;
        setAggressive(hunting);          // synced to clients: the renderer uses it for "model mutilation"
        setNoclip(hunting);

        if (frozen) {
            setPos(ox, oy, oz);
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
        }
    }

    /** Silent group member: stands still and stares, breathes quietly, leaves when the hunt is over. */
    private void tickWatcher() {
        Player p = level().getNearestPlayer(this, 80.0);
        boolean room = entityTags().contains(ROOM_TAG);
        if (!room && (p == null || ++watcherLife > 2400)) { discard(); return; }
        if (p != null) faceTowards(p);
        if (--breathCd <= 0) {
            say(ModSounds.BREATH_SLOW, room ? 1.2F : 0.8F, 0.85F);
            breathCd = 90 + random.nextInt(40);
        }
    }

    private void sounds(Player p, double d, boolean hunt) {
        boolean sprinting = hunt && getDeltaMovement().horizontalDistanceSqr() > 0.012;

        if (--breathCd <= 0) {
            if (sprinting) {
                say(ModSounds.BREATH_PANT, 2.0F, 0.95F + random.nextFloat() * 0.1F);
                breathCd = 20 + random.nextInt(6);
            } else if (hunt) {
                say(ModSounds.BREATH_HEAVY, 1.8F, 0.9F + random.nextFloat() * 0.1F);
                breathCd = 48 + random.nextInt(8);
            } else {
                say(ModSounds.BREATH_SLOW, 1.6F, 0.92F + random.nextFloat() * 0.08F);
                breathCd = 72 + random.nextInt(8);
            }
        }

        if (sprinting && d < 24.0 && --shriekCd <= 0) {
            say(ModSounds.SHRIEK, 4.0F, 0.9F + random.nextFloat() * 0.15F);
            shriekCd = 70 + random.nextInt(80);
        }

        if (--ambientBlock <= 0) {
            if (hunt && d < 32.0 && random.nextInt(2) == 0) {
                switch (random.nextInt(4)) {
                    case 0 -> sayAt(behind(p, 1.4, 1.5), ModSounds.BREATH_HEAVY, 2.6F, 0.9F);
                    case 1 -> sayAt(behind(p, 1.8, 1.5), ModSounds.WHISPER, 3.0F, 0.9F);
                    case 2 -> sayAt(behind(p, 3.0, 1.0), ModSounds.KNOCK, 3.5F, 0.9F);
                    default -> { followLeft = 5 + random.nextInt(5); followIndex = 0; followTick = 0; }
                }
            } else {
                SoundEvent[] pool = hunt
                        ? new SoundEvent[]{ModSounds.CRACK, ModSounds.CHUCKLE, ModSounds.DRONE, ModSounds.WHISPER, ModSounds.KNOCK, ModSounds.VOICE_BANGING}
                        : new SoundEvent[]{ModSounds.WHISPER, ModSounds.KNOCK, ModSounds.RADIO, ModSounds.CRACKLE, ModSounds.CONNECTION_LOST};
                say(pool[random.nextInt(pool.length)], 3.0F, 0.9F + random.nextFloat() * 0.15F);
            }
            ambientBlock = 500 + random.nextInt(700);
        }

        if (followLeft > 0 && --followTick <= 0) {
            double dist = Math.max(1.2, 4.2 - followIndex * 0.45);
            sayAt(behind(p, dist, 0.1), ModSounds.STEP, 2.0F + followIndex * 0.15F, 0.86F + random.nextFloat() * 0.14F);
            followIndex++;
            followLeft--;
            followTick = 8;
        }
    }

    private void playerOnlyEffects(Player p, double d, boolean hunt) {
        if (!hunt || !(p instanceof ServerPlayer sp) || d > 24.0) return;
        double close = Mth.clamp(1.0 - d / 24.0, 0.0, 1.0);

        if (--heartCd <= 0) {
            sp.playSoundToPlayer(ModSounds.HEARTBEAT, SoundSource.HOSTILE, (float) (1.3 + close * 1.6), (float) (0.9 + close * 0.25));
            heartCd = (int) (44 - close * 26);
        }
        if (d < 8.0 && --tinnitusCd <= 0) {
            sp.playSoundToPlayer(ModSounds.TINNITUS, SoundSource.HOSTILE, (float) (0.8 + (1.0 - d / 8.0) * 1.6), 1.0F);
            tinnitusCd = 50;
        }
        if (--darkCd <= 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));   // slow pulse, safe
            darkCd = 100;
        }
    }
}
