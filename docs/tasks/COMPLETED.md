# Completed

Action items, newest first. Player-facing details are in [../CHANGELOG.md](../CHANGELOG.md).

## Turn 30 (2026-10-15)
- The user's answers: Simple Difficulty switches kept ("Keep the SD system as it is"; the waiting item closed). Presets
  are one file each, only the main one loaded, the others written next to it. No testing this turn: not a blocker
  (the turn 29 retest carries over).
- Bigger sun (Will do item 5, the user's turn 28 item, off by default): `sun.grow`, `sun.maxSize` 3; equal steps per
  phase over its length, 10 s with one phase or `days` 0 (Claude's reading in TODO since turn 28). The server sends the
  size; the coremod (now `@SortingIndex(1001)`) patches `RenderGlobal.renderSky`'s sun size to `client/SkyHook`.
  `SolarCoreTest` checks the patch on the dev (MCP) and pack (SRG) class files; `TimelineTest` the growth.
- Phase stingers' foundation (Will do items 1 and 2, kept off until the user's assets): `splash.soundAsMusic` plays a
  phase sound as streamed music (fire can no longer cut it short), vanilla music fades over `musicFadeOutTicks` 10 and
  is held until it ends (`client/Stinger`). The splash font already fell back while missing.
- Ice Apocalypse (Will do item 6, in progress): the preset file `solarapocalypse - Ice Apocalypse.cfg` (written and
  kept up to date next to the main config, never loaded; an `about` note), its defaults (freezing by conversions,
  Claude's decision as asked: conversions are enough; plants to snow, lava sets, negative degrees, `sunNeedsDaytime`
  false, `TOP_Y`), negative Simple Difficulty degrees (comments only: the code was sign-neutral; Simple Difficulty
  clamps to 0-25 and hurts at 2 or less). Water that freezes no longer fizzes. The snow system: questions filed.
- Research (five read-only agents, `research30` notes in RESEARCH.md section 13) and an adversarial review of the
  diff; self-test passes with `-Pcompat_mods` (SPEC section 7, turn 30).

## Turn 29 (2026-10-15)
- The user's retest (turn 28 list): the sea while exploring, hanging falls and the turn 27 sea test ticked; the
  optional flow sighting answered (below). Simple Difficulty switches not tested yet (carried).
- Teleports "do not honor our hook" (the user: "chunks loaded when you teleport are completely vanilla and then subject
  to catchup destruction"; their `/tp` to -708.5 36 4.5 and screenshot). Cause: Cubic Chunks generates and lights the
  cubes nearest the player first, so landing at Y 36 in a sea lit the cube Y 32-47 before the sea's top cube; its water
  then waited for the water above (not loaded yet) and was sent full, and the engine drained it later, in view (the
  walls of water). Flying high, the top cubes come first, which is why exploring worked. Fixed: before such a cube is
  updated, the cube above is loaded and lit first when a standing liquid on its top layer lies under the column's top
  and would change in the open; that runs the hook for it too, up to the sea's top. Self-test: a sea cube lit before
  the one above it.
- Water that did not clear (save `Retest 2`, read-only; the user's screenshots at -439 28 -395 and -470 45 -480):
  vanilla flows that had settled (still water at levels 1 and 9: a 17-block fall from Y 45 at -470 -480, a level-1
  spread over a fall at -439 -395), with no source left. They started after the engine's last look at their cubes,
  and a cube is looked at again only when something in it falls due (the next layer: 20 minutes at 1 layer a day).
  Fixed: a liquid flowing into a block queues its cube for the engine's next round (a few seconds), whatever started
  the flow. This also covers to-do item 6 (falling gravel, Claude's turn 28 guess; the user saw no gravel there),
  closed.
- Pollution of the Realms (to-do item 4) with the user's answers: (i) dust from destroyed blocks, yes; (ii) carbon and
  sulfur once when a solar fire is lit, yes; (iii) "phase 11 won't have pollution of the realms emissions ... This
  doesn't mean apply that logic to infinite phases, the player can do that if they want at their own detriment (so add
  a warning somewhere in the config for that) ... Happy for even 0.5 to be the defaults for both ... a block whitelist
  ... * @ 0.5% or minecraft:dirt @ 5% e.t.c. Would that work better?" Yes: a list beats one percent, first match
  wins; (iv) "A phase outside of pollutionEndsPhase would clean up all pollution. If a user wants to retain pollution,
  they can set pollution events to 0% chance". Done: the `pollution` section (`destructionPollution`,
  `destructionPollutionBlocks` default `* @ 0.5%`, `solarFirePollution`, `solarFirePollutionPercent` 0.5,
  `pollutionStartsPhase` 2, `pollutionEndsPhase` 11, with the infinite-phase warning); `-Pcompat_mods` installs AdPother
  and ForgeEndertech (the current pack's versions; their CurseForge ids read from that pack's instance file,
  read-only). Self-test (with Pollution of the Realms installed): after 8 layers of erosion, 6,010 dust units for
  destroyed blocks and 608 carbon and sulfur units for solar fire; with the running phase made `pollutionEndsPhase`,
  the gas around spawn went from 13,768 blocks to 0 (39,571 cleared). Its gas blocks are not solid and let all light
  through (opacity 0), so they never shade the sun. Its own carbon (from vanilla fire) drifts up to the highest loaded
  cubes; the self-test's line checks skip gas.
- OUTOFSCOPE.md said players joining later get no announcement replay (the user: "We've made it so they get the current
  phase, and only the current phase on join, right?"): right since turn 21 or so; the line now says that, and SPEC.md
  has it.
- The user's question on the Simple Difficulty switches (keep or revert, and why they exist): answered in TODO.md
  "Waiting for the user's decision" (Claude: keep).
- The user's order for after the Will do items (asset foundations, then the Ice Apocalypse config, before vanilla
  worlds) and their Ice Apocalypse plan ("Makes sense?": yes): TODO.md.
- Found by the self-test with Pollution of the Realms: the engine's check whether blocks next to a fire spot can burn
  looked through each block class's methods, which on a dedicated server throws for a modded block whose methods name
  client-only classes (AdPother's gas: `ParticleManager`), and stopped the server. Fixed: such a class counts as one
  whose flammability is not remembered (asked each time instead).
- Self-test: the Simple Difficulty check now measures where the pigs were put (a hurt pig ran into shade in a forest
  seed and read 1.0 instead of 4.0).

## Turn 28 (2026-10-14)
- The user's retest (turn 27 list): solar fire steps sound like stone, and `/solar phase 11` announces only phase 11:
  both ticked. The sea drained before sending and the live flow sighting carry to the turn 28 retest.
- Simple Difficulty switches (the turn 27 question): the user chose (b), "on/off switches. Basically so the user can
  have SimpleDifficulty installed, but disable either of the temperature features. I apologize, I didn't mean thirst
  at all, but the temperature you get from direct sunlight or background." Done: `entities.simpleDifficultySunTemperature`
  and `entities.simpleDifficultyBackgroundTemperature` (default true), named for temperature instead of the "Thirst" in
  the user's turn 27 names; false turns that part off whatever the phases say.
- Versions (the turn 27 question): "Current versions are all correct, no need to change anything." Closed.
- The user's sea test (save `Retest` and its screenshot; scanned read-only): "I can see (new) ocean in the distance not
  modified, and my character is near some flowing water sticking up out of the ground that was never culled. All
  phase 10."
  - Ocean in the distance: in the save, the columns 9-13 from the player (the edge of the loaded area) had their sea's
    top cube (Y 48-63) drained and the cube under it (Y 32-47) still full, Y 34-47, far above the level (Y 36). The
    hook drains the top cube and queues the one below for the next tick, but its own work (thousands of changes per
    cube while the player flew) counted against the engine's 4 ms and 2,048 changes per tick, so the engine got no
    budget at all while cubes kept loading and the queued cubes waited. Fixed: work done before sending no longer
    counts against `tickBudgetMs` and `maxBlockChangesPerTick` (it sends nothing; Cubic Chunks' generation time limit
    already covers it). Columns past the view distance are generated for population but not lit or sent: they wait,
    untouched, until a player comes near (as before).
  - Flowing water sticking up: falls one or two blocks high at Y 29-31 with air above them, and flat flows, near
    gravel. The mod removed the water above a fall without telling it (no block physics), so it hung there; all of it
    was below the `LAYERS` level (Y 36 at day 7.85: phase 3 from day 0.75 at 4 layers a day), so evaporation was not
    due there yet (Y 31 at day 9.0). The sea itself went by the phase 7+ destroy list (`*` takes water: hardness 100),
    down to the ground. Fixed: a fall under a liquid the engine takes now gets its update and drains the vanilla way.
    What set the water flowing: likely gravel falling (1,617 pending gravel ticks in the save, gravel beside the
    flows), not proven (TODO.md item 6).
- The user on instances: "one of your task things was looking into my current save in one of my instances to try and
  find the flowing water location ... I even named the save I was operating on ... for the water problem it wasn't
  required at all." Right: the save they named is in this workspace (`run/saves`); CLAUDE.md now says so next to the
  instances rule.
- Stingers (to-do item 2): the user's yes to music with vanilla music fading out over about half a second; filed there.
- The user's new to-do item 5 (a bigger sun per phase, optional, off by default): filed with Claude's reading.
- Pollution of the Realms investigation (to-do item 4, next by priority once the fixes were done): AdPother's API read
  from its jar (copied read-only from the user's current pack, where they said looking was fine); findings in
  RESEARCH.md section 12, Claude's way in TODO.md "Waiting for the user's decision".

## Turn 27 (2026-10-12)
- The user's retest (the turn 26 list; ticked without a note = worked): `-Pcompat_mods` (their run configuration
  fixed), terrain updated before sending, break effects at each block, dark spots (every fire spot lit at night). The
  production jar loaded in a copy of their pack ("I did try this last turn, and yes it loaded fine"). Solar fire steps
  not ticked: carried to the turn 27 retest.
- Closed with those results: dark spots (to-do item 1, turns 19-26) and the cubes that looked covered after `/time add`
  (turn 11; none in the turn 26 test). Reopen either if seen again.
- Water slivers: "So for the water slivers to-do item, you never did any engine changes? In that case, don't worry
  about this." Right: that item was a recheck after turn 11's engine fix (which removed one cause); turn 26 changed only
  the default mode. Closed, as the user says. "Can we change this back to LAYERS as default, if that's how it was
  before?": yes, `evaporation.mode` defaults to `LAYERS` again. The user: `INSTANT` left no water in their tests; the
  flowing water was with `LAYERS`.
- Flowing water (the user's "Water Flowing Test" save, phase 8, and its screenshot; scanned read-only): around
  398 73 240, 2,102 water blocks, only 123 of them sources (in caves, Y 0-25); the rest is settled flow (still water
  with a level, 1,640 of it falling) at Y 33-51, lying on vitrified sand and settled blocks. All of it is below the
  `LAYERS` level, which had come down to Y 52 (phase 3 from day 0.75, 4 layers a day, the engine at day 3.85): it goes
  as the level passes (Y 44 by day 5.75), except where it lies under an overhang (shaded, so never, as any covered
  water). What set the water flowing after the sea came down is not found: with `blocks.blockPhysics` off the mod's
  changes notify no neighbours, and a save does not show the cause (TODO.md retest asks for a live sighting).
- Flowing liquids in evaporate lists ("is it possible to include the moving version of liquids for evaporation
  alongside their still versions"): the defaults already take them (`material:water` and `*` match flowing water at
  every level). An entry naming the still block now takes the flowing one too (`minecraft:water` takes
  `minecraft:flowing_water`, lava alike); the self-test checks it.
- "I'm guessing that the coremod affecting chunk generation doesn't apply to water?": it does (every rule, evaporation
  too), but with `LAYERS` water below the level is not due yet, and a sea deeper than one cube drains a cube at a time:
  when the cube below reached the player first, its water was still under water, and it then waited at the end of a
  round that holds every cube just loaded (minutes after a teleport). Fixed: cubes a pass before sending opens go first
  on the next tick.
- `/solar phase 11` played every phase's sound, splash and message up to 11 (the user's tweak): a jump across several
  phases now announces only the one it lands in (each start is still logged).
- The user (mid-turn): Cubic Chunks SRP Test is their current pack, Cubic Chunks the old one (why Pollution of the
  Realms is only there); they test in this workspace, so Claude looks at their instances only when asked (CLAUDE.md).
  The versions question went to TODO.md.
- Filed: phase stingers (the fire's sound cuts the phase sound short; stingers to act as music), the Simple Difficulty
  "Thirst" switches (a question for the user), the user's Pollution of the Realms plan (TODO.md).

## Turn 26 (2026-10-12)
- The user's test pass (the turn 25 checklist; ticked without a note = worked). Worked: the mod loading as a coremod
  (its log line, `/solar status` showing "updated before sending", no warning); no client lag in phase 11 and no
  effects from terrain done before sending; Simple Difficulty temperatures rising in the sun and the heat, normal in
  sealed rooms and caves; BuildCraft oil catching fire and burning away; settled sand and gravel (looks, sounds, drops,
  pick block, does not fall); no covered-looking areas after `/time add`. The user's config keeps Claude's Simple
  Difficulty values, so they stay the defaults (the decision is closed).
- `-Pcompat_mods` "didn't work": the user's run configuration says `-Pcompact_mods`. They copied the pack's jars into
  `run/mods` instead, which works too (TODO.md retest).
- Updated before sending: "I arrive at normal vanilla chunks, then after a short delay (5-10 seconds) they then apply
  all previous and the current phase rules quickly" (15-25 s with it off). The hook did run (18,072 cubes in their
  log), but Cubic Chunks lights a cube before the cubes above it (it goes by distance from the player), so a new
  surface's sky was unknown and nothing that needs the sun changed. Fixed: the hook generates the cubes up to
  `world.skyClearance` above a new surface first, and is no longer held by the engine's time budget (it runs inside
  Cubic Chunks' cube generation, whose own time limit counts it).
- Break effects: "Block particles appears to only happen one per chunk, in its corner. It needs to happen per block.
  Not sure if water evap has particles either." The effect packet kept the engine's reused position object, which had
  moved on by the time the network thread wrote it. Fixed (the fizz and smoke too).
- Dark spot at 61 47 -397 (Full Phase Test world, screenshot in `test_log_files`), on a former sea floor, fire later:
  full sky light on the client (not the turn 25 lead). The ground was a cube's top layer; its fire stands in the cube
  above, which was not looked at again after the ground's conversion until the next phase. The turn 19 spot was the
  same. Fixed: that cube looks again in the same round. Self-test, same world with and without: 2,131 against 2,100
  fires by the end of phase 3.
- Water slivers: "a regression I think, and it didn't really need to be done anyway. Are we able to revert that back
  to before it?" The `evaporation.mode` default goes back to `INSTANT` (whole bodies at their phase's start, the
  default until the phase defaults of 2026-10-08 made it `LAYERS`, a level coming down 4 layers a day). Their observation
  "Causes some leftovers, which I think mostly is moving water, which isn't picked up by evaporation": Forge has no
  event for ordinary water flow, so the cause is not clear without a spot (TODO.md retest).
- Solar fire steps sound like stone (the user's tweak; silent since turn 11, also the user's ask then).
- New to-do item from the user: Pollution of the Realms integration (TODO.md, Will do).

## Turn 25 (2026-10-12)
- The user's decisions: the Cubic Chunks mismatch stays a chat warning (not Forge's error screen); the user tunes the
  Simple Difficulty temperatures while testing. Their ask: one more turn on to-do items that need no test, and a test
  checklist for turn 26 (TODO.md, "Waiting for the user's test").
- Dark spots lead (to-do item 1): confirmed in Cubic Chunks' code and guarded. When it tracks a cube, the column takes no
  engine change that tick, and the columns beside it count the edge row it marks; the hook no longer changes the cube
  above a new cube (players may have it). Self-test: 256 for the loaded column, 16, 16 and 1 beside it.
- `-Pcompat_mods` puts Simple Difficulty and BuildCraft (the pack's versions, deobfuscated) into `run/mods` for testing
  the integrations in the dev client; the self-test checks BuildCraft oil under the fire rules when it is loaded.
- Publishing no longer lists MixinBooter as required (was "Before a release").
- The sun pig that died faster with Simple Difficulty loaded (turn 23 note): not Simple Difficulty (it has no hook on
  mobs), but the self-test's timing. The pig burns through phase 1's catch-up, which took 200 ticks in that world
  instead of 140. The test pigs are now invulnerable until phase 4.
- Not started: the vanilla-world block engine (TODO.md), several turns of work; after the test pass rather than half
  done before it.

## Turn 24 (2026-10-12)
- The user's question: can the Cubic Chunks hook be optional, config-based and without a mixin mod (no hard
  dependencies)? Yes: a plain Forge coremod (one class, `core/SolarCore`) adds one call to Cubic Chunks' `Cube.trackSurface`,
  and `performance.updateBeforeSending` (default on) switches it in game. The mod's mixin build setting only serves dev
  runs; the jar declares no mixins and needs no MixinBooter.
- The user's follow-up: does a coremod tie the mod to one Cubic Chunks version, and is it the best option? Only the
  feature is tied, as a mixin would be (both patch the same method): without `Cube.trackSurface` the patch is skipped
  with a warning and cubes change from the queue as before. The method is unchanged at Cubic Chunks' latest source
  (2025-11). A mixin could hook the same spot, but a normal mod can only add one through MixinBooter; the coremod needs
  nothing.
- The user's next ask: with a different Cubic Chunks version, do not crash and tell the player what to change. Done:
  the patch skips itself (or switches off for the session if Cubic Chunks' classes differ), everything else runs, and
  the log and the host or operators in chat get the version and the fixes (the tested Cubic Chunks, or the option off).
  Self-test: the warning text with a simulated mismatch, none with the real one. Not Forge's error screen: that one
  stops the game from loading (TODO.md, the user's decision).
- The hook before players get a cube (Claude's optional to-do item, "pre-first-light hook"): done after the first light
  instead (before it would need the engine to run without the column's height map). Saves the pop-in and the packets,
  not the lighting.
- RESEARCH: Cubic Chunks marks every x/z of a column's heights whenever it tracks a new cube there, which wraps that
  tick's heightmap packet; a lead for the dark spots (TODO.md).

## Turn 23 (2026-10-11)
- The user's request: the optional to-do items and the block-breaking effects (vanilla style only; their own sounds
  later), to test together. Done this turn: break effects, Simple Difficulty, burning liquids. The pre-first-light
  hook (Cubic Chunks mixin) is next.
- Break effects (`blocks.breakEffects`, radius, per-tick cap per player). Server side wiring only; the user tests it.
- Simple Difficulty integration (DESIGN.md): `phase_n.sunTemperature` / `backgroundTemperature` (default values are
  Claude's, in TODO.md for the user's say). Self-test with Simple Difficulty loaded (temporarily, in `run/mods`): the
  modifier is registered and adds 4 in the sun, 1 under the roof, 0 deep in phase 4.
- Burning liquids: already handled by the fire rules once a fluid says it burns (BuildCraft oil and fuels do,
  flammability 200: vanilla fire burns them away). Fixed on the way: BuildCraft's fluids say they block movement, so a
  non-burning one could get solar fire and an oil pool counted as ground in a recorded surface.

## Turn 22 (2026-10-11)
- Block ID renamed to `solarapocalypse:settled_block` (the user: says what it is; plain "settled" could clash with a
  future vanilla name). The client check stays in TODO.md for the user, as they asked.
- Verbose debug logging mode (the user's low to-do, turn 6: "You can add a verbose debug mode if you want"):
  `performance.statusLogSeconds` logs the `/solar status` lines every that many seconds while a phase runs. Claude's
  take on what "verbose" should hold, as the user left it open; more can go in on request. Self-test (status log every 30 s) logs it; the self-test passes with the new block ID.

## Turn 21 (2026-10-11)
- The user's ice question (icy biomes, vanilla and modded): in CubicWorldGen worlds the surface model decides, so ice a
  biome generates as its surface is ground and ice made later is not. For the recorded surface (no CWG), ice resting
  on ground now counts as ground; ice on a liquid (frozen water) is still looked through. Ground-like features in a
  recorded surface (ice spikes, boulders) filed in OUTOFSCOPE.md.
- Default background conversions (the user's scheme): phases 4 to 9 have phases 1 to 6's convert rules; phase 10 has
  them all through carrying (phases 7 to 10 have no convert rules of their own, so nothing is left to add there).
- The user's modifier question answered: background rules take every modifier except layers (`layer=`, `depth=`
  ignored with a warning; `convertDepth` only turns them off at 0), as the user voted.
- Non-falling sand and gravel (the user's item, raised to medium by them): `solarapocalypse:settled_block`. Self-test checks: rules place it and treat it as vanilla (settled red sand vitrifies), drops and pick block are vanilla sand, in mid-air vanilla gravel fell and settled stayed; on turn 17's seed the 4 blocks that fell into a cave are 0. Self-test passes.

## Turn 20 (2026-10-11)
- Floating water item closed: the user saw malformed beaches near the player, not distant terrain, and is happy with
  it as it is; their point that the default phases' catch-up takes newly loaded water at once confirmed in code.
- Fire values: the current `igniteFlammablePercent` defaults are the user's lower ones; Claude sees no reason to lower
  them further (in dense forests the percent hardly matters, one fire spreads through). Item closed.
- Dark spots item: the world type is Cubic Chunks' default Custom Cubic preset (the user).
- Turn 19's "TODO #14" was #12 after the renumbering (the CubicWorldGen model note).
- Background conversions (to-do #1, the user's item): `phase_n.backgroundConvert`, empty by default (rules for the
  default phases wait for the user's decision). Self-test check: grass in the sun and under a roof turns to dirt, under
  a sealed lid it stays (sky light 0).
- Ice is not ground: turn 19's CubicWorldGen model offset was CWG's snow populator freezing surface water after the
  model (rerun: 0). Without CWG, a column first seen frozen recorded the ice as its ground: fixed.
- Terrain cut ahead of the TOP_Y line (turn 17) closed: converted sand or gravel falling into caves by vanilla's own
  check on placement. The self-test counts falls apart now (seed 699324865493784867: gone below the line 0, fell 4).
  Filed with the non-falling gravel and sand item.
- Water bottle check: its 0 of 3 on one seed was the check (doused fire relights on the cube's next look, by design);
  it now counts dousing at any tick (3 of 3).
- Dark spots investigated (findings in TODO.md): fixed ground uncovered two or more cubes down waiting for the next
  phase or day.
- Review of the turn's changes: modded leaves without `decayable`, background rules where the surface is unknown,
  stale sky light after a pass, timing of rules in `convertDepth` 0 phases, no fire on ground whose background
  conversion is pending. Self-test (random seed) passes with all of it.

## Turn 19 (2026-10-11)
- The user's question on mixed destruction and evaporation (the floor eaten under the water, evaporation reaching it
  later): answered, that is the behaviour before turn 16's hold. Scanned copies of their test saves for liquid on air
  (new `scripts/cubescan.py`): floating sea sheets only in the save from before the hold, a few spots in one after it
  from unloaded cubes (fixed in turn 18). Water-aware erosion filed in IDEAS.md as asked.
- Leaf culling (to-do, the user's call: only without `blocks.dropItems`): leaves only a removed log held go with it.
  Self-test check added; the self-test passes.
- Vanilla fire seed cap dropped (OUTOFSCOPE.md); the under-cover spot item widened to dark spots in general.

## Turn 18 (2026-10-11)
- The user's water report (turn 16 code: the water shaved a layer at a time while the ground went under and around it,
  water floating over caverns, odd beaches): they had expected the erosion to wait for all water to drain, and chose
  the lead-in phase that only evaporates (SPEC section 3, Evaporation). Fixed one way floating water happens: ground
  under a cube that is not loaded (Cubic Chunks unloads cubes far above a player) was not held. Self-test passes.
- Bench census fix: drained floors were counted twice and wet floors counted as drained (turn 17's reported numbers
  came from runs before that line).
- Throughput (to-do #1, the user's high priority): counting the change cap in cubes (Claude's suggestion) measured
  and dropped: a changed cube carries ~220 changes, so 2048 changes is ~9 cubes a tick and the layers per cube resent
  came out the same (SPEC section 4). Item closed: the cap and fire are the levers. Bench variants `cap<n>` and
  `budget<ms>`, and cubes changed per tick in its result.

## Turn 17 (2026-10-11)
- `blocks.solarFireModel` defaults to `SIMPLE` (the user prefers its look, apart from vanilla fire, and found it
  performs much better).
- Confirmed by the user's test: fire light 8 is fine (mobs do not spawn on the fire, and later phases kill them
  anyway); the spawner flames fix; the opaque vitrified sand texture.
- Read the user's spark profiles: server thread only (spark's `/spark` in singleplayer samples the integrated
  server), so they do not cover the fire model, which is drawn by the client; no retest needed, as the user already
  saw SIMPLE perform better (SPEC section 4: what the server thread did, and `/sparkc` for the client).
- Answered the water question (settings for seas draining before the land): the user's config already does it with
  turn 16's hold; checked on their settings with the new bench variants `sea` and `liquidsout` (SPEC section 3,
  Evaporation: the settings, the measurement, and a lead-in phase for every sea gone before any land erodes).
- Rain puts out solar fire (to-do, the user's turn 11 ask): `phase_n.rainDousesFire` (default true); the engine
  relooks at the surface cubes when rain starts and stops. Found by a review of this turn's work: fire an earlier
  phase lit waited for the running phase's own moment, so rain missed it and night or rain could leave it dark for up
  to a phase's length; it now keeps the moment of the phase that lit it. Self-test checks (both).
- The self-test's TOP_Y lag check found 4 terrain blocks cut ahead of the line on one seed; turn 16's code does the
  same there (TODO, terrain cut ahead of the TOP_Y line).

## Turn 16 (2026-10-11)
- Vitrified sand is opaque (the user: more sand than glass; same texture without its transparency); bench: ~23 % more
  layers where it is the surface, as sky light no longer goes through it.
- `blocks.solarFireLight` default 8 (the user's rule: lower light lags less, so 8; measured 839 / 602 / 483 ms of
  server time a layer at 15 / 8 / 4 with 85 % fire).
- Phase 11 of the defaults has no conversions (the user), through the new `convertDepth` 0 (no conversions at all
  while the phase runs, carried ones included; otherwise the carried rules still act in layer 1).
- Water against the erosion, option (b) (the user): ground under standing liquid waits until the liquid has gone, then
  catches up. Self-test check (a pillar under water stays, goes once the water is gone).
- Throughput (to-do item 1): the engine stops going down a column at the first block nothing can change yet (96
  layers a minute without fire and conversions, from 72; same results on the same seed). Bench variant `measure<n>`.
- Answered: lower fire light lags less; opaque vitrified sand helps, but phase 11 now has no conversions anyway; the
  one-layer vitrified test was benched by Claude (turn 15 see-through, turn 16 both); the client FPS tests (simple fire
  model, light 8) are for the user (TODO, waiting for the user's test).

## Turn 15 (2026-10-10)
- Defaults (the user's decisions): no fire in phase 11 (too hot for fire to burn), `maxBlockChangesPerTick` 2048.
- The user's lighter fire options: `blocks.solarFireLight` (0-15, default 15 until the user picks the default; sent
  to clients; flames drawn full-bright at any level) and `blocks.solarFireModel` (client, `SIMPLE`: crossed flames
  only, 4 faces instead of 12; optional, as the user asked). Correction: turn 14 put vanilla fire's model at ~40 faces;
  it is 12.
- Mobs killed by the sun, its heat or fire the apocalypse set drop nothing unless `blocks.dropItems` (the user: 757
  items from night spawns dying at once); players drop as usual. Self-test check.
- Spawner flames left after destruction (the user's report): Cubic Chunks clients keep a cube's tile entities when the
  cube is resent whole; the client now drops those whose block is gone, once a second.
- Measured (bench variants, the user's questions): stone stages in phase 11, one vitrified stage, no conversions, no
  fire, light 4 (SPEC section 4; numbers in the TODO stone stages and light default items). The user's turn 15 test
  read from their log: ~100 layers a day with fire, kept up with 200 without (SPEC section 7).
- Answered: the gradient (`layer=` rules) works in non-infinite phases too (the phase's own rules act once its
  destruction is done, by layer below the surface then; carried into later phases they act as the surface comes down;
  self-test since turn 10); water options (a) and (b) both change the destruction, not the evaporation (TODO, the
  user decides next turn); trees: the bandaid is enough (the user; OUTOFSCOPE).

## Turn 14 (2026-10-10)
- Cheaper fire in infinite phases, second part (the user's to-do item): the engine removes the old fire before its
  ground goes (it went through a neighbour update, uncounted, and the engine then counted a change that did nothing),
  and the block the erosion takes becomes the new surface's fire in the same change; no neighbour updates around
  changes of air and fire, and none to solar fire beside a change (it only minds its ground); flammability around a
  new fire remembered for ordinary blocks. Measured on one seed at a 10 ms budget: 33 layers a minute instead of 23,
  ~30 % fewer block changes and ~15 % less server time per layer; fire coverage unchanged (84.5-84.8 % for 85 %).
- Throughput measured (the user's questions: what the budget counts, whether 200 layers a day is possible, whether
  dropping conversions or evaporation helps, what makes their PC struggle, what causes player timeouts): fire is
  the cost (no fire: 57 layers a minute, a quarter of the server time per layer), half of it the fire's light, which
  runs outside the engine's budget; conversions and evaporation barely matter in stone (SPEC section 4).
- `scripts/probe_server.sh <tag> bench [fire0|light0]`: the dev throughput check (`debug/Bench`, `scripts/bench.cfg`,
  the user's turn 14 test config without the cap and at 10 ms).
- Filed: the user's enquiries on trees and on water against the SURFACE line (options in TODO, waiting), a lighter
  solar fire (waiting), block-breaking effects near the player (the user's later to-do), and the vanilla fire seed cap
  moved up (the user reports vanilla fire as their biggest FPS cost).

## Turn 13 (2026-10-10)
- The user's SURFACE "raw chunk deletion" again (phase 11 as phase 1): in the first phase on a depth line everything
  above the terrain (the sea, trees) was due at the phase start, so the first round took each cube's whole sea at once
  and took minutes, holding the erosion up; it now goes top down over the first tenth of the phase, as in later phases.
- Rounds nearest the players first (the user saw random chunks change).
- Sleeping steps, `/time` and `/solar set/add/phase` jump (user's decision, as Claude described); no death in one's
  sleep from erosion any more.
- Default phase 11 gradient from the user's list (layers 6 to 2, `convertDepth` 6).
- Default fire per phase from the user's table (solar and vanilla 0-75 %, phase 11 85 % solar, 100 % vanilla).
- Solar fire burns what touches it (the user: dropped items did not burn; nothing caught fire from it at all).
- Throughput research (the user: where 512 comes from, what each setting does, whether anything crashes).
- Self-test: above-terrain timing in a single infinite SURFACE phase; items burning on solar fire; terrain gone below
  the SURFACE line counted directly; terrain checks limited to cubes the engine works on.

## Turn 12 (2026-10-10)
- Engine clock per world (the user's SURFACE "raw chunk deletion" and TOP_Y pits and caverns): every cube is brought up
  to one clock, which moves a layer per round while the engine is behind; loads and late-waking cubes join at their
  neighbours' layer; saved per dimension so restarts carry on; `/solar reload` keeps it.
- Cheaper fire in infinite phases, first part (user upgraded it to a to-do item): one roll per surface block instead of
  a redraw on every layer.
- Gradient as the default phase 11 (user: yes).
- Self-test: TOP_Y line through solid terrain with the clock running ahead of the engine and a 2 x 2-column area
  reloading mid-way; the same check on the turn 11 engine reproduces the user's caverns.

## Turn 11 (2026-10-10)
- Carried rules per layer (user's design): top layer only in infinite phases, all `convertDepth` layers otherwise;
  latest phase decides per layer; `layer=` modifier (Claude's form instead of `@ 2`, user asked for a recommendation).
- Thrown water bottles put out solar fire (user: yes to potions).
- Per-phase `weather` (UNCHANGED / NONE / RAIN / THUNDER, user's names; INHERIT default added by Claude).
- Even erosion when the engine falls behind (lockstep steps, cut-short passes resume first): the user's 4-6 deep trenches.
- Silent solar fire steps (user).
- Phase sections above `phases.count`: confirmed never recreated; kept with a "not used" note.
- Self-test: carry per layer, config regen, water bottles, weather, evenness under a capped engine; the three rule
  checks turn 10 misreported as passing (synthetic phases with `convertDepth` 0) fixed by treating 0 as 1.
- Review workflow (3 reviewers, each finding checked by an independent verifier) over the turn's changes; fixed what it
  confirmed: a cube stepping in an old phase could be dropped for the new phase (high), a missed look where an older
  phase takes over a layer, a loop surviving a cut made in a deeper layer, huge numbers in `layer=`, `dimensions=`
  and `temperature` stopping the server, a thunderstorm at the end of every dry spell, weather holds in dimensions
  that share the overworld's, extra passes while keeping up, placed blocks waiting behind a cube's steps, and the
  status line's "behind" figure.

## Turn 10 (2026-10-09)
- Depth-staged conversions (`depth=k`, `depth=a-b`), re-looks when the line nears, cube below queued as the surface
  nears it; self-test: the user's grass-to-glass stages, a `depth=3-4` rule after 8 layers of erosion.
- Fixed: the cube below lost its look when a pass stopped early (column edit limit).
- Solar fire allowed next to ice and snow again (user, turn 10); only burnable neighbours keep it away.
- User's edit to the non-falling gravel and sand item kept (only with `blocks.blockPhysics` false).

## Turn 9 (2026-10-09)
- The user's working rules in CLAUDE.md (file everything, disagree openly, check before surprises, few-word references).
- `dimensions=` scope for convert, destroy and evaporate entries (filter first, no shadowing); self-test check.
- Placeholder announcements per phase; each crossed phase announced in turn; late joiners get the running phase's.
- `maxSunJump` removed; skip boost after `/time set` and `/time add`; dying in one's sleep in infinite phases.
- Placed blocks queue their cube.
- Red vitrified sand (variant property, drops red sand; default rule with preserveState).
- Docs: decisions answered, TODO reordered (world-gen preset validation last), webs to OUTOFSCOPE, the hold-the-clock
  idea dropped, non-falling gravel and sand filed.

## Turn 8 (2026-10-08)
- Fixed: crash (`ConcurrentModificationException` in `CubeEngine.run`) when a block callback loaded a cube mid-pass;
  vanilla fire is placed only where its six neighbours are loaded; the first mid-pass cube load is logged once.
- `blocks.nightDousesFire`: fire goes out spot by spot over the sunset, same spots relight over the sunrise.
- Infinite phases look at cubes every layer (was every 1/20 day).
- Vitrified sand block (drops sand), default phase 5 rule; placeholder texture.
- Blocks hanging on the side of a changed block (wall torches, ladders) pop off quietly too, not only those on top.
- Audit fixes: solar fire kept off flammable neighbours again with fire spread off; plants made where they cannot live
  are removed at once (no later drops); the time set-back guard reads the time of day; config rewritten on load (order,
  unused keys dropped); open decisions listed in TODO.md; time budget checked before every change inside a cube; solar
  fire stands on any ground that blocks movement and goes only with it; set-back guard 100; dev script keeps the
  previous logs.
- Audit of the previous prompt's asks against the repo (workflow), gaps reported to the user.

## Turn 7 (2026-10-08)
- Fixed (critical): fire was put out and redrawn at every phase start; outside infinite phases one roll now serves all
  phases, so `ignitePercent` is the total alight and lit fire stays.
- Time skips count: sleeping, `/time add`, and `/time set` (as the skip forward to that time of day); `maxSunJump`
  caps instead of ignoring. Unit test `ApocalypseClockTest`.
- Blocks that pop off a changed block (plants and crops on new paths, torches, top halves) go at once and drop nothing
  unless `blocks.dropItems`; top halves of tall plants and doors follow their bottom half instead of converting alone.
- Rule targets and selectors take block properties (`minecraft:anvil[damage=1]`); `preserveState` modifier; modifiers
  stack (`-> target @ 30% preserveState`).
- Solar fire also kept off diagonal neighbours and ice and snow.
- Defaults: cloth and carpet no longer burn at 30 % in phase 2 (all of it in phase 3).

## Turn 6 (2026-10-08)
- Fixed: the engine's time budget was a deadline from the tick's start, so a world tick longer than the budget left it
  no time (no block changes in the user's singleplayer test). Self-test stage with 12 ms slow world ticks.
- Fixed: a non-last infinite phase descended past its end in `reachTime` (blocks removed at the wrong rate).
- Per-phase `depthReference` with one depth line per reference; `TOP_Y` lines start at `world.topY`.
- Per-phase `evaporate` lists with `fluid:` and `temperature` selectors.
- `convertRuleMode` / `destroyRuleMode`.
- Config saves phases in number order; phase 1 `sunFireSeconds` default 0.

## Turn 5 (2026-10-08)
- Phase announcements: chat message, sound, client splash title (font texture, flicker colours, fades); one log line;
  `/solar announce`.
- `entities.sunAtNightFromPhase` (default 7).
- Selectors: comma lists and `!` exclusions; conversion loops cut with a warning.
- No vanilla fire while `doFireTick` is false or on `blocks.vanillaFireBlacklist` (TNT).
- `performance.maxBlockChangesPerTick` (512), checked inside cubes too.
- Load and fire statistics: `/solar status` engine line, `/solar fire`.
- Layer interval formula in the `layersPerDay` comment and the phase plan log.
- Conversion chances (`@ n%`), fixed per block by a position hash.
- Solar fire is not placed beside or under flammable blocks.
- The user's eleven-phase set (`run/config/solarapocalypse - default-phases.cfg`) is now the config default.
- Self-test: own fixture config (`scripts/selftest.cfg`); rules check incl. chances, first-hit pig check, change cap
  check; client start-up smoke run.

## Turn 4 (2026-10-07)
- Phase timing: days are the minimum; destruction from the phase start, then conversions (`convertDays`); the next
  phase waits for both.
- Infinite phases: conversions run ahead of the destruction; a finite depth after an infinite phase is warned about.
- Solar fire block (vanilla fire without ticking) and ignition: share of surface blocks after the conversions,
  flammables get vanilla fire; removed at the next phase, redrawn per layer in infinite phases.
- Defaults: `entities.spareFireImmune` and `entities.sunNeedsDaytime` false.
- Docs: answered questions removed from RESEARCH.md, update notes in DESIGN.md, IDEAS.md.

## Turn 3 (2026-10-07)
- Depth reference: per-column surface ("3D printer" layers, default) or a fixed top Y, configurable.
- Per-column surface from the CubicWorldGen generator (no generation); recorded per column elsewhere (saved with the column).
- Conversions act on the surface layer only (`convertDepth`, default 1), after the phase's destruction is done.
- Rule modes: CARRY (latest phase's rule wins) or ISOLATED; rule chains resolved.
- Background heat = sky light reaches the mob (no block counting).
- Docs: DESIGN.md and CHANGELOG.md moved to docs/, task lists in docs/tasks/.
- Self-test: CWG model accuracy, erosion line check.
- Time budget: fixed maximum and a share (default 75 %) of the tick time left by everything else.
- Liquids cannot make new sources during their evaporation phases (with `blocks.blockPhysics`).
- Trees and buildings above the surface erode top down at the start of a destroying phase.
- Phase plan summary and depth warning logged on every config load.

## Turn 2 (2026-10-07)
- Config with any number of phases, phase scaling, safe phase.
- Clock: saved progress, SUN or TICKS mode, pause while empty.
- CC block engine: conversions, erosion, evaporation, retroactive catch-up, time budget, client heightmap packet limit.
- Sun damage, fire, Fire Resistance option; `/solar` command.
- TimelineTest, SelfTest, no-CC smoke run.

## Turn 1 (2026-10-06)
- Workspace and git set up; Cubic Chunks soft dependency; dev mods installed into run/mods.
- Feasibility research (docs/RESEARCH.md) with probe and verification.
