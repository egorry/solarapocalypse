# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- (none)

## Waiting for the user's test (client side: Claude cannot see a client)
Turn 28 retest. Tick the boxes and write what you saw into prompt.txt.
- [ ] A sea while you explore (your Retest test): phase 10, fly fast over a sea never loaded. At the edge of your view
  the water comes down to the level with the rest, and no sea is left standing in the cube under its drained top
  (Y 32-47 there), even while cubes keep loading.
- [ ] Hanging falls: water falling one or two blocks with nothing above it no longer stays; it drains away within
  seconds (vanilla flow). Flat flowing water below the `LAYERS` level still waits for the level, as all water there.
- [ ] Simple Difficulty switches: `entities.simpleDifficultySunTemperature=false` stops the sun's degrees,
  `entities.simpleDifficultyBackgroundTemperature=false` the background heat's (both on by default).
- [ ] From turn 27, not ticked: a sea drained before you see it: `evaporation.mode=LAYERS` with your phases,
  `/solar set 10`, then teleport over a sea never loaded: it arrives drained down to the level, and a sea deeper than
  16 blocks drains its lower part within a second or so of arriving.
- [ ] Optional: water you see start flowing (not leftovers): coordinates, and what changed next to it just before
  (gravel falling? see the falling gravel item below).

## Will do (Cubic Chunks)
1. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
   `textures/font/splash.png`) [lowest until the user has made them, user].
2. Phase stingers (the user, turn 27, a future item): "Sound of fire cancels out the phase lighting strike sound. This
   will cut my future custom stingers short. Can this be fixed? The stingers will act as music, and should interrupt
   vanilla music (fade out if possible)." Claude's notes: the client plays a limited number of sounds at once and
   takes the oldest one's channel for a new one when full, so many fires crackling cut the phase sound short; music
   streams on channels of its own. Likely way: the client plays the stinger as a streamed sound in the music category,
   stops (or fades) vanilla music when it starts, and holds new music back until it ends [low, user: future; with item
   1]. The user (turn 28): "For when we get to it: phase sounds as music, especially fading out Minecraft music
   (quickly, over half a second or so, agree?): yes." Claude agrees: half a second (10 ticks) reads as a cut, not a
   gap; a client setting if it needs tuning.
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
5. The user's item (turn 28): "Optional make sun bigger per phase (in a compatible way. Defaults to off though).
   Respects phases value, and if phases=1 then progressively expand the sun to max over 10 seconds (the fallback),
   otherwise honor the phase's days if set to something other than 0" [low, user: optional, off by default]. Claude's
   reading, to confirm when it starts: the sun grows from vanilla size to a configured maximum over the phases
   (`phases.count`), each phase's share over its `days`; with one phase, or a phase whose `days` is 0, it grows over 10
   seconds instead. Compatible way: scale only the sun's quad in the vanilla sky render (client), not replace the sky
   renderer, which sky mods also replace.

6. Falling gravel (Claude, turn 28) [low, Claude: an unproven cause]: the user's Retest save has 1,617 gravel blocks
   with a pending tick (gravel placed by world generation is told to check its support once); gravel lies beside each
   of the frozen falls. When such a tick runs after the engine took what held the gravel, it falls (vanilla), and
   leaving and landing tells the blocks around it, which sets water there flowing (the flows the user keeps seeing).
   Not proven; the turn 28 retest asks for a live sighting. If it holds: the engine makes such gravel settled (it
   already does for sand and gravel its rules place) or clears its pending tick.

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).

## Later (relies on bottom-up mechanic from above)
- "Lava Apocalypse" preset for an infinite phase coming from below, converting air into lava.
- "Ice Apocalypse" preset mostly the same as normal config, but ice-ified, including SimpleDifficulty support and cold variants of some existing settings (cold damage, background cold damage e.t.c)
