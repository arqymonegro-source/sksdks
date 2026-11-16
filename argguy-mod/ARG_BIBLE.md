# ARGGUY - ARG design notes (Interloper edition)

## What I took from Interloper (from the public fan wiki / recaps, paraphrased)
Interloper is a Source-engine ARG: hidden **demos** (numbered Type 1-7; Type 5 = first-person of an NPC-like entity),
a sentient hidden layer inside the game's code, the **Umbrella Men** ("interlopers": a wind chime when they appear,
noclip-style floating, groups, model mutilation, dragging the POV out of the map), the **Red Room / Whiteroom**,
a cut dev tool (**DM Editor**), console text, CONNECTION LOST, data rot, found-footage presentation.
Everything above is re-implemented as Minecraft mechanics with original wording/sounds; nothing is copied.

## Your lore, mapped
| Lore | Mod |
|---|---|
| **AZUL channel** (victim POV; she dies, the entity is responsible; she is epileptic) | `Demo: AZUL`: a log of a recording where the chime plays and a figure appears behind the camera. No flashing effects in the mod, by design. |
| **Friends channel** (capture + delete it) | `Demo: The Friends` + `Containment Drive`: never look away; 5 compression passes. |
| **Unknown team channel** (capture to study it, it seems sentient) | `Demo: Unknown Team`: the demo-type classification memo; "compression is not deletion". |
| **Ending** (computer destroyed from inside, it reaches the internet, more victims) | Drive hits 100%: `] ARGGUY.EXE has stopped responding`, `network interface DISABLED -> ENABLED`; 5 permanent clones. Demo Recorder then reports **Type 7: COMMITTED TODAY**. |

## Player loop
1. Hunt starts: windchime -> whistle -> console line -> silent watchers appear far away.
2. It floats through walls toward you. Looking at it freezes it (and the freeze is what makes the drive work).
3. If it reaches you unwatched: `we got one.` -> Red Room (or Whiteroom) -> back after ~45 s, it relocates.
4. Use the **Demo Recorder** to classify what happened; read the three channel demos to learn the rules.

## ARG hooks
- Codes/URLs: `tape.argguy.*.line5` in `lang/en_us.json` (3 channels).
- The windchime and whistle are the cross-media cue: use the same audio in the videos.
- The Demo Recorder "type" text can be changed to puzzle clues; Type 7 is a good reward for finishing the story.

## Ideas for later
- A real "demo file" players can export; a **fake error screen** (static, non-flashing); in-world graffiti that returns
  after you remove it (data rot); "hint node" markers; DM-Editor-style noclip cameras for the researchers' side.

## Safety rules
- No strobing, rapid flicker, or high-contrast flashing, ever (slow fades / vanilla Darkness only).
- Add a config to disable stings and screen effects; put a photosensitivity + horror warning on the mod page and videos.
