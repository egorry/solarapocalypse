# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- Simple Difficulty switches (the user's to-do item, turn 27): "simpleDifficultySunlightThirst=true and
  simpleDifficultyBackgroundThirst=true global vars to enable/disable SimplyDifficulty integration without needing a
  simpleDifficultyIntegration bool (which I think is stupid)". The mod has no such bool: the integration is on whenever
  Simple Difficulty is installed, and each phase's `sunTemperature` / `backgroundTemperature` (0 = none) is its switch.
  Claude reads "Thirst" two ways: (a) new: the sun makes players thirsty (Simple Difficulty's thirst drains faster in
  the sun, and in the background heat), these two being its global switches; (b) global on/off switches for the
  temperatures there now. Which one? Claude's view: (a) suits Simple Difficulty (its API has thirst exhaustion); then
  also say whether the amounts are per phase (like the temperatures) or one global rate [low, user].
- Versions for the current pack (turn 27: the user's current pack is Cubic Chunks SRP Test, not Cubic Chunks): the dev
  setup and the Cubic Chunks patch are pinned to the old pack's Cubic Chunks 0.0.1271 and CubicWorldGen 0.0.152 (and
  its Simple Difficulty and BuildCraft for `-Pcompat_mods`). If the current pack has other versions, tell Claude which,
  or let it read that pack's mods folder once (it looks at instances only when asked) [Claude: before a release].

## Waiting for the user's test (client side: Claude cannot see a client)
Turn 27 retest. Tick the boxes and write what you saw into prompt.txt.
- [ ] Solar fire steps (turn 26, not ticked in turn 27): walking and jumping through solar fire sounds like stone.
- [ ] Phase jumps: from phase 1, `/solar phase 11`: only phase 11's message, sound and splash (each start is still in
  the log).
- [ ] A sea drained before you see it: `evaporation.mode=LAYERS` (the default again) with your phases, `/solar set 10`
  (the level is then far down), then teleport over a sea never loaded: it arrives drained down to the level, and a
  sea deeper than 16 blocks drains its lower part within a second or so of arriving, not minutes later.
- [ ] Optional: water you see start flowing (not leftovers): coordinates, and what changed next to it just before.

## Will do (Cubic Chunks)
1. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
   `textures/font/splash.png`) [lowest until the user has made them, user].
2. Phase stingers (the user, turn 27, a future item): "Sound of fire cancels out the phase lighting strike sound. This
   will cut my future custom stingers short. Can this be fixed? The stingers will act as music, and should interrupt
   vanilla music (fade out if possible)." Claude's notes: the client plays a limited number of sounds at once and
   takes the oldest one's channel for a new one when full, so many fires crackling cut the phase sound short; music
   streams on channels of its own. Likely way: the client plays the stinger as a streamed sound in the music category,
   stops (or fades) vanilla music when it starts, and holds new music back until it ends [low, user: future; with item
   1].
3. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
   user]. Turn 19's offset on seed -5479930802988752619 (1,701 positions above the ground, up to 6) was CWG's snow
   populator turning surface water into ice after the model, so the self-test counted it as ground; turn 20 rerun with
   ice not counted: 0.
4. Pollution of the Realms integration investigation (the user, turn 26) [low, Claude: after the retest fixes]. The
   user's plan (turn 27): "If we can have solar fire create carbon/sulfur, and dust on block destruction without
   causing too much to be created, some way to limit that. Even 1 of each type per XZ coordinate would still be lots
   of pollution blocks created. I think the best way is probably a global percentage of solar fires and block
   destruction. Then I think for phase 11 (infinite * destruction) that it clears any pollution above the SURFACE/Y
   line and stops producing any pollution. These should be config options. I can't see a need for per-phase, so global
   options that take the phase n integer is probably best here. Something like:"
   `destructionPollution=true`, `destructionPollutionPercent=5`, `solarFirePollution=true`,
   `solarFirePollutionPercent=5`, `pollutionStartsPhase=2`, `pollutionEndsPhase=11`.
   Found (turn 27): Pollution of the Realms is `adpother` 1.12.2-1.2.25.0 (needs ForgeEndertech), in the user's current
   pack (Cubic Chunks SRP Test); its pollution package has carbon, sulfur and dust as gas blocks. Not started.

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).

## Later (relies on bottom-up mechanic from above)
- "Lava Apocalypse" preset for an infinite phase coming from below, converting air into lava.
- "Ice Apocalypse" preset mostly the same as normal config, but ice-ified, including SimpleDifficulty support and cold variants of some existing settings (cold damage, background cold damage e.t.c)
