package com.argguy;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Every sound event; the files live in assets/argguy/sounds and are mapped in sounds.json. */
public final class ModSounds {
    public static final SoundEvent STEP = reg("step");
    public static final SoundEvent STEP_RUN = reg("step_run");
    public static final SoundEvent BREATH_SLOW = reg("breath_slow");
    public static final SoundEvent BREATH_HEAVY = reg("breath_heavy");
    public static final SoundEvent BREATH_PANT = reg("breath_pant");
    public static final SoundEvent WHISTLE = reg("whistle");
    public static final SoundEvent CHIME = reg("chime");
    public static final SoundEvent DRONE = reg("drone");
    public static final SoundEvent CONNECTION_LOST = reg("connection_lost");
    public static final SoundEvent VOICE_BANGING = reg("voice_banging");
    public static final SoundEvent CAPTURE = reg("capture");
    public static final SoundEvent CRACK = reg("crack");
    public static final SoundEvent CHUCKLE = reg("chuckle");
    public static final SoundEvent SHRIEK = reg("shriek");
    public static final SoundEvent HEARTBEAT = reg("heartbeat");
    public static final SoundEvent TINNITUS = reg("tinnitus");
    public static final SoundEvent STING = reg("sting");
    public static final SoundEvent WHISPER = reg("whisper");
    public static final SoundEvent KNOCK = reg("knock");
    public static final SoundEvent RADIO = reg("radio");
    public static final SoundEvent CRACKLE = reg("crackle");

    private static SoundEvent reg(String name) {
        Identifier id = ArgGuyMod.id("entity.argguy." + name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Forces the class to load so the registrations above run. */
    public static void init() {}

    private ModSounds() {}
}
