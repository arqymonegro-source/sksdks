# ARGGUY - ARG horror mod (Fabric, source project) - Interloper edition

The ARGGUY entity from your YouTube ARG, rebuilt to behave like the Interloper (Gmod / Source engine ARG) "Umbrella Men",
plus the hunt/audio design from Flowers (Whistle Occurence).

> **Not compiled or tested.** No Java compiler or internet in my sandbox, and I could not check the code against the
> Minecraft 26.1.2 API. Written for Fabric / Java 25 (the setup your coolaliens mod uses). Expect a few small compile
> errors; see "Version-sensitive spots".

## Build
Requires **JDK 25** and **Gradle 9.4+** (Minecraft 26.1.2 needs Java 25).
1. `gradle.properties` is already filled in (Fabric Loader 0.19.3, Fabric API 0.150.0+26.1.2, Loom 1.16.3).
2. Gradle wrapper: either copy `gradlew`, `gradlew.bat` and the `gradle/` folder from your working **coolaliens** project,
   or run `gradle wrapper` once with a system Gradle.
3. `./gradlew build` -> `build/libs/argguy-0.1.0.jar` (ignore the `-sources.jar`). Drop it in `mods/` together with Fabric API.
   Paste compile errors to me and I will fix them.

## Interloper-style features
| Interloper | In the mod |
|---|---|
| Umbrella Men appear with a **wind chime** | the hunt starts with a real windchime sound (`windchime` + the older cold chime) |
| They **operate in groups** | one or two silent **watchers** (tag `argguy_watcher`) stand far away, motionless, always facing you |
| They **float like noclip** (like the DM Editor) | while hunting it glides straight at you **through blocks** (noclip, no gravity) |
| They **drag the POV out of the map** | when it touches you it **captures** you: you are teleported into the **Red Room** (see below), then it relocates itself ~70 blocks away |
| **Red Room / Whiteroom** | sealed room high above the map, 4 silent "CAPTURE" copies in the corners; 20% it is the **Whiteroom** (white walls, purple/black *missing-texture* floor). You are put back after ~45 s |
| **Model mutilation** | while hunting the model stretches and warps (client side, slow, never flickers) |
| **Source console text** | chat shows console lines: `[HINT] 0 nodes created`, `demo_recording: type 5`, `CONNECTION LOST`, `we got one.` |
| **Demo types 1-7**, playback never matches generation | `Demo Recorder` item: right click classifies the moment (1 nothing, 3 it is near, 5 it hunts, 6 in the Red Room, 7 *COMMITTED TODAY* = escaped) |
| Trumpets and banging over voice chat, malformed packets | new sounds `voice_banging` (bitcrushed brass honks + bangs, packet dropouts), `connection_lost`, `capture` |
| Type 5 / DMNPC naming | the room watchers carry the visible name `CAPTURE` |

## Kept from before (your ARG lore)
- Freezes completely while any player looks at it. Invulnerable. Containment Drive (5 passes) = the friends' ending:
  fake crash lines, it escapes, **5 permanent clones** (`argguy_escaped`) that hunt forever.
- Three tapes, now written as demo logs: `Demo: AZUL`, `Demo: The Friends`, `Demo: Unknown Team`
  (edit `assets/argguy/lang/en_us.json`, keys `tape.argguy.*`, and replace the `[...TODO]` URLs/codes).
- Constant breathing, heavy steps, rare ambient sounds, heartbeat/ringing/behind-you sounds only the hunted player hears.

## Commands (open to everyone for now, see TODO in ArgGuyMod)
`/argguy summon` | `/argguy hunt` | `/argguy ending` | `/argguy room` (test the Red Room) | `/argguy exit` (get out)

## Known limits
- Red Room state is in memory. If the server restarts while someone is inside, they stay in the room: `/argguy exit`.
- The room is built at y=240 (overworld) / y=110 (other dimensions) directly above the player and replaces whatever
  blocks are there; it is removed afterwards (leaving air).
- If a player logs out inside it, they are not teleported back until they run `/argguy exit`.

## Photosensitivity (important - the lore victim is epileptic)
Nothing flashes or strobes: no screen flash, no rapid flicker, model warping has periods of several seconds, and the
only screen effect is vanilla Darkness (slow pulse). Keep it that way when you extend it, and add a photosensitivity /
horror warning to the mod page and the ARG videos.

## Version-sensitive spots (likely compile errors)
1. `client/*` render classes (render-state API changed a lot): `ArgGuyRenderer.extractRenderState`, `ArgGuyModel`.
2. `ArgGuyMod`: `EntityType.Builder.build(key)`, `Item.Properties.setId`, `Identifier`, Fabric `EntityRendererRegistry`.
3. `ArgGuyEntity`: `noPhysics`, `level().noCollision(this)`, `getHeightmapPos`, `setYBodyRot`.
4. `RedRoom`: `ServerPlayer.teleportTo(x,y,z)`, `MinecraftServer.getPlayerList().getPlayer(uuid)`, `ServerTickEvents`.
5. `ModelPart.xScale/yScale/zScale` (used by the mutilation effect).
6. Item `use` / `interactLivingEntity` return types; command permission check (left out on purpose).
7. Limbs may be left/right swapped vs the Figura avatar (symmetrical, harmless).
