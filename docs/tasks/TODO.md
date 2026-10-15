# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- Ice Apocalypse (Will do item 6): the snow system waits for the user's answers (below); everything else of it is done.

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- Ice Apocalypse snow system (the user, turn 30: "Optional snow system, can be turned off. Opposite of destruction - adds
  a layer of snow on each exposed block (the thin flat snow, configurable layers per phase, accumulate over the phase's
  day duration). Additional optional background snow adds snow to under cover areas, like the background conversions
  system. I would default this to 1 layer per phase. I would default this to not being infinite in the last phase ...
  If snow is removed down to the ground, in later phases (like the last phase) it should progressively build up that
  snow to the overall accumulated level over time"; and "the user able to specify how high each phase can add snow
  layers to the surface"). Claude's reading: per phase `snowLayers` (layers that phase adds over its days) and
  `backgroundSnowLayers`, the accumulated depth being the sum so far; one `snow.enabled` switch (off in the main config,
  on in the Ice preset), like the Simple Difficulty and Pollution switches. Questions, each with Claude's view [user]:
  a. Deeper than one block: full snow layers stacked (look like snow blocks; daylight still gets through, so nothing
     under them goes dark, and the ground the apocalypse measures from never moves), or real snow blocks under the top
     layer (dark underneath, lighting work, the snow becomes the ground)? Claude: stacked full layers.
  b. Within a phase each spot gets its layer at its own random moment over the phase's days (snow thickens evenly, as
     conversions spread), or everywhere at the phase start? Claude: spread over the days.
  c. Last phase: adds its layers over its days, then stops (your default), with an `endless` option that keeps adding
     at that rate? Claude: yes, `endless` off.
  d. Refill: about one layer per spot every minute (1/20 day at the vanilla day length) until back to the accumulated
     depth, starting when a player breaks a block there, in every phase (not only the last). Terrain that unloads and
     loads again comes back fully snowed at once (telling dug-out snow apart would need saved data per column).
     Claude: yes to both.
  e. Where snow lies: vanilla's rules (not on ice or packed ice, paths, farmland, bottom slabs, normal stairs, plants or
     water; on leaves yes), so frozen seas stay bare ice; and none where block light is 10 or more (torches, lava), as
     vanilla, or it would melt and come back. Claude: yes.
  f. Background snow: its own number per phase; in the Ice preset 1 per phase from three phases after the open snow
     (your three-phase lag of background conversions). Under glass roofs too, as sky light passes glass. Claude: yes.
  g. Everywhere in the apocalypse's dimensions, deserts included, whatever the biome's temperature? Claude: yes.
  h. A conversion or destroy rule that would take snow again wins (no snow laid there, so nothing flickers), and snow
     grows silently (no particles or sound)? Claude: yes.
- Ice Apocalypse preset content (Claude's draft, the user's to change): water freezes over (30 % in phase 1, all from
  phase 2) and the ice grows a layer thicker as `convertDepth` grows (2 in phases 5-6, 8 by phase 11); farmland turns to
  dirt; small plants turn to snow layers (30 % in phase 1, all in phase 2), lily pads go; vines, cacti and gourds die in
  phases 2-3, leaves in phases 3-4; lava sets in phase 5 (sources to obsidian, the rest to cobblestone); Simple
  Difficulty degrees are the Solar ones turned negative (-1 to -9 under the open sky, 0 to -7 wherever sky light
  reaches), day and night (`sunNeedsDaytime` false); no erosion, evaporation, fire or sun damage; shaded ground follows
  three phases later, without water (sky light passes 4 blocks into water: the seas would freeze 4 layers deep at once).
  Weather unchanged (rain shows as snow only in cold biomes; the mod changes no biome) [user].

## Waiting for the user's test (client side: Claude cannot see a client)
Turn 30. Tick the boxes and write what you saw into prompt.txt.
- [ ] Bigger sun: `sun.grow=true`, then `/solar phase 1` ... `/solar phase 11`: the sun grows a step a phase toward
  `sun.maxSize` (3) over each phase's length; phase 11 (its `days` is 0) grows its step in 10 seconds. To see a phase's
  growth quickly, `/time add` within it.
- [ ] Phase sounds as music: `splash.soundAsMusic=true`; `/playsound minecraft:music.game music @p`, then
  `/solar announce`: the music fades out over half a second and the thunder plays (the music slider sets its volume).
  While it plays, `/playsound minecraft:music.game music @p` plays nothing. With much solar fire around it plays out
  in full.
- [ ] Ice Apocalypse preset: `run/config/solarapocalypse - Ice Apocalypse.cfg` appears on the next start. To try it,
  keep your `solarapocalypse.cfg` under another name and rename the preset to it (a fresh world, or `/solar set`), with
  `-Pcompat_mods` for Simple Difficulty's cold.

Carried from turn 29:
- [ ] Teleport into a sea (your turn 29 find): phase 10, teleport into a sea never loaded, below its surface (as your
  `/tp` to Y 36): it arrives drained, with no walls of water draining in view.
- [ ] Flows in a drained sea (your water that was not clearing): water that starts flowing into the drained sea goes
  within seconds (the engine's next round), not only at the next layer (20 minutes at 1 layer a day).
- [ ] Pollution of the Realms (`-Pcompat_mods` puts it and ForgeEndertech in `run/mods`): from phase 2, dust where
  the erosion destroys blocks (0.5 % of them) and carbon and sulfur above new solar fire (0.5 %); from phase 11
  (`pollution.pollutionEndsPhase`) none, and the gas goes as the line passes. How it looks, and how the server copes.
- [ ] Simple Difficulty switches (kept, your turn 30 answer): `entities.simpleDifficultySunTemperature=false` stops the
  sun's degrees, `entities.simpleDifficultyBackgroundTemperature=false` the background heat's (both on by default).

## Will do (Cubic Chunks)
Order (the user, turn 29): the items here that do not wait for the user's assets; then the functionality the asset
items need (items 1 and 2), built and kept on today's behaviour by default until the assets arrive (then switched on);
then the Ice Apocalypse config (below, moved up); all before vanilla worlds. Turn 30: item 5 (the bigger sun) and the
foundations of items 1 and 2 are done; item 6 is in progress.
1. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
   `textures/font/splash.png`) [lowest until the user has made them, user]. Foundation in place: `splash.titleFont`
   defaults to `solarapocalypse:textures/font/splash.png` and uses Minecraft's font while it is missing; a phase's
   `sound` takes any sounds.json name, and `splash.soundAsMusic` plays it as music (item 2). When the files come: ship
   them, point the phases' `sound` at them and turn `soundAsMusic` on by default.
2. Phase stingers (the user, turn 27): "Sound of fire cancels out the phase lighting strike sound. This will cut my
   future custom stingers short. Can this be fixed? The stingers will act as music, and should interrupt vanilla music
   (fade out if possible)." Built in turn 30, off until the stingers exist (`splash.soundAsMusic`, `musicFadeOutTicks`
   10: the user's "quickly, over half a second or so"). Left: turning it on with item 1 [low, user: with item 1].
3. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
   user]. Turn 19's offset on seed -5479930802988752619 (1,701 positions above the ground, up to 6) was CWG's snow
   populator turning surface water into ice after the model, so the self-test counted it as ground; turn 20 rerun with
   ice not counted: 0.
6. Ice Apocalypse config (the user, turns 29 and 30). Turn 29: "create an alternate config file for it ... duplicated
   and changed to fit the ice apocalypse, that the player would have to manually rename. That's how other configs
   should be. Solar Apocalypse is the default that is actually loaded on first boot." Turn 30: "one config file per
   preset, only the main one is loaded, the others are just created adjacently"; "So really for the ice apocalypse we
   just need: Negative temperature support, updated comments; Conversions (already exist) or evaporation counterpart -
   freeze pass; Optional snow system". Done in turn 30: the preset file, negative Simple Difficulty degrees, freezing
   by conversions (the user left it to Claude: conversions are enough, below), no solar fire. Left: the snow system
   (the questions above), then the preset's snow defaults [user's order: now].
   Freezing (Claude's decision, as asked): a rule like `fluid:water -> minecraft:ice` freezes the top layer, and with
   `convertDepth` growing per phase the ice grows a layer thicker per phase, top down. An evaporation-like freeze pass
   would not work as evaporation does (it relies on the top block going, and ice stays) and would repeat the layers
   conversions already have. Cold damage without Simple Difficulty is not in the user's turn 30 list, so the preset has
   none (a frost damage like the sun's would be a small addition if wanted).

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).

## Later (relies on bottom-up mechanic from above)
- "Lava Apocalypse" preset for an infinite phase coming from below, converting air into lava.
- ("Ice Apocalypse" moved up to Will do item 6, turn 29: it does not need the bottom-up direction.)
