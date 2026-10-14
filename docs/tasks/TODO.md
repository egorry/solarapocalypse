# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- Simple Difficulty switches (`entities.simpleDifficultySunTemperature`, `entities.simpleDifficultyBackgroundTemperature`):
  the user (turn 29): "I'm not sure they're needed? ... each phase dictates what SimpleDifficulty's temperature is
  anyway ... What would you recommend here: keep it as is, or remove/revert? If you can remember the why". The why: the
  user's own turn 27 to-do item, "simpleDifficultySunlightThirst=true and simpleDifficultyBackgroundThirst=true global
  vars to enable/disable SimplyDifficulty integration without needing a simpleDifficultyIntegration bool"; in turn 28
  they chose temperature switches over a new thirst feature. Claude's view: keep. Turning the heat off otherwise means
  zeroing up to 22 values (and losing them for later), and it is the same shape as the Pollution switches the user
  designed (`destructionPollution`, `solarFirePollution`: on/off besides amounts). Removing is a two-line revert; the
  same question then covers the two Pollution switches (an empty list or 0 % does the same) [low, user].

## Waiting for the user's test (client side: Claude cannot see a client)
Turn 29 retest. Tick the boxes and write what you saw into prompt.txt.
- [ ] Teleport into a sea (your turn 29 find): phase 10, teleport into a sea never loaded, below its surface (as your
  `/tp` to Y 36): it arrives drained, with no walls of water draining in view.
- [ ] Flows in a drained sea (your water that was not clearing): water that starts flowing into the drained sea goes
  within seconds (the engine's next round), not only at the next layer (20 minutes at 1 layer a day).
- [ ] Pollution of the Realms (`-Pcompat_mods` now puts it and ForgeEndertech in `run/mods`): from phase 2, dust where
  the erosion destroys blocks (0.5 % of them) and carbon and sulfur above new solar fire (0.5 %); from phase 11
  (`pollution.pollutionEndsPhase`) none, and the gas goes as the line passes. How it looks, and how the server copes.
- [ ] Carried from turn 28: Simple Difficulty switches (if you keep them, the decision above):
  `entities.simpleDifficultySunTemperature=false` stops the sun's degrees, `entities.simpleDifficultyBackgroundTemperature=false`
  the background heat's (both on by default).

## Will do (Cubic Chunks)
Order (the user, turn 29): the items here that do not wait for the user's assets; then the functionality the asset
items need (items 1 and 2), built and kept on today's behaviour by default until the assets arrive (then switched on);
then the Ice Apocalypse config (below, moved up); all before vanilla worlds.
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
5. The user's item (turn 28): "Optional make sun bigger per phase (in a compatible way. Defaults to off though).
   Respects phases value, and if phases=1 then progressively expand the sun to max over 10 seconds (the fallback),
   otherwise honor the phase's days if set to something other than 0" [low, user: optional, off by default]. Claude's
   reading, to confirm when it starts: the sun grows from vanilla size to a configured maximum over the phases
   (`phases.count`), each phase's share over its `days`; with one phase, or a phase whose `days` is 0, it grows over 10
   seconds instead. Compatible way: scale only the sun's quad in the vanilla sky render (client), not replace the sky
   renderer, which sky mods also replace.
6. Ice Apocalypse config (the user, turn 29; was under "Later", as it does not need the bottom-up direction): "the ice
   apocalypse item technically doesn't rely on nor even use directional apocalypse, so we can easily make its changes
   and create an alternate config file for it. I would keep it as the same config file, but duplicated and changed to
   fit the ice apocalypse, that the player would have to manually rename. That's how other configs should be. Solar
   Apocalypse is the default that is actually loaded on first boot." The earlier note: "mostly the same as normal
   config, but ice-ified, including SimpleDifficulty support and cold variants of some existing settings (cold damage,
   background cold damage e.t.c)". Claude agrees: one config format, presets as whole config files. Notes for when it
   starts: cold needs settings the mod lacks (negative Simple Difficulty degrees, cold instead of fire damage, freezing
   instead of evaporating); how the file reaches players (shipped in the jar and written next to the main config on
   first boot if missing, named like the user's `solarapocalypse - <name>.cfg`) is for the user to say [after the asset
   foundations, user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).

## Later (relies on bottom-up mechanic from above)
- "Lava Apocalypse" preset for an infinite phase coming from below, converting air into lava.
- ("Ice Apocalypse" moved up to Will do item 6, turn 29: it does not need the bottom-up direction.)
