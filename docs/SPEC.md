# Solar Apocalypse: how it works

The behaviour as built. The user's feature list is [DESIGN.md](DESIGN.md); feasibility research is
[RESEARCH.md](RESEARCH.md); what is left is in [tasks/TODO.md](tasks/TODO.md). Every setting named here is documented
in the generated `config/solarapocalypse.cfg`.

The config file is rewritten on every load: sections in order (phase_2 before phase_10), keys no longer used dropped
(logged), new keys added with their defaults. Sections for phases above `phases.count` are kept.

## 1. Time
- Progress is a `long` saved in the overworld's `data/solarapocalypse.dat`; 1 day = 24000 units. Nothing rewinds it.
- `clock.mode = SUN` (default) adds the forward movement of the overworld's `worldTime`: a day lasts as long as the sun
  takes, so any day-length mod is followed. Skipped time counts: sleeping and `/time add` as they move the clock,
  `/time set` (which sets the time back to that day's value) as the skip forward to that time of day; setting the time of
  day back by less than 100 counts nothing (a day-length mod such as Longer Days steps the time back by one each tick); `/time add` counts
  in full. The engine then catches the loaded terrain up: after sleeping at the normal lag-safe budget; after a
  `/time set` or `/time add`, for `performance.skipBoostSeconds` (30) with `skipTickBudgetMs` (20) and
  `skipMaxBlockChangesPerTick` (2048), faster with some lag. A big skip in an infinite phase leaves a large backlog
  (at 200 layers a day a night's sleep is about 90 layers per loaded column), which the engine works off over the
  following minutes. Players whose ground (bed or the block under them) the erosion of an infinite phase took during
  a skip die ("scorched by the sun"), checked once per skip; creative and spectator players are spared. It stops while `doDaylightCycle` is
  false. `TICKS` counts server ticks (`clock.ticksPerDay` per day).
- `clock.progressWhileEmpty = false` pauses while nobody is online; `true` keeps counting and terrain catches up on load.
- Every "days" setting is in apocalypse days, whose length the clock mode defines: in `TICKS` mode one day is
  `clock.ticksPerDay` ticks (`phase_n.days = 2` with `ticksPerDay = 72000` lasts 144000 ticks, two real hours).

## 2. Phases and depth
- Phase 0 (`phases.safeDays`) is quiet; phases 1..`phases.count` follow.
- A phase's `days` (0 = `phases.baseDays` x `phases.scaling`) is its minimum length. It runs two tasks: destruction
  from its start, then conversion. If they take longer than its days, the next phase waits until they are done; if they
  finish early, nothing happens until the days are over. Task times come from the config, never from CPU load: the
  time budget (section 4) only delays when the changes show up, not the timeline.
- `depth`: layers destroyed by the end of the phase, counted down from the column's **reference** (layer 1 = the
  reference block), or `infinite`. Never decreases; once infinite, every later phase is infinite at the last positive
  `layersPerDay`.
- `world.depthReference`:
  - `SURFACE` (default): each column's own terrain surface, so layers follow the land like a 3D printer's layers in
    reverse. In CubicWorldGen worlds it is computed from the generator (`cc/CwgSurface`); elsewhere it is recorded the
    first time the column's top is known (the ground under trees and water) and saved with the column
    (`SurfaceRecord`). Columns whose surface is not known yet wait.
  - `TOP_Y`: `world.topY` for every column (`auto` = the height the generator reports).
- Per phase, `depthReference` (`INHERIT` default, `SURFACE`, `TOP_Y`) switches. Each reference has its own depth line,
  moved only by its own phases (the other stays where it is), and a block goes when either line reaches it. A `TOP_Y`
  line starts at `world.topY` when its first phase starts and descends at that phase's speed, so mountains are cut
  flat first and nothing is swept all at once. A line reaches nothing before its first phase.
- Destruction `speed`: `PHASE` spreads the descent over the phase's days, `RATE` goes at `layersPerDay`, `INSTANT` at
  the phase start. Conversion: `convertDays` after the destruction (-1 = the rest of the phase's days, 0 = at once).
- Infinite depth descends at `layersPerDay` (each layer stays 1/`layersPerDay` of a day: a day lasts 1200 s at the
  vanilla day length, so a layer every S seconds is `layersPerDay = 1200 / S`; 16 = 75 s; the phase plan in the log
  shows the interval); every later phase is infinite too, with its own rate, conversions and damage, and the last one
  goes on for ever. In infinite phases conversions run all the time, ahead of
  the destruction: with `convertDepth` n the n layers below the eroding surface are converted before they are destroyed.
- Everything is a pure function of progress (`Timeline`), so terrain loaded on day 40 gets what terrain watched since
  day 0 got.
- Phase start: one log line (`Solar apocalypse phase n begins on day d: <message>`), and for the players online, from
  the phase's section: `message` in chat, `sound` (any sound name the client knows, played at the player), `splash` (a
  title on screen with the message small underneath; fonts, colours and timing in the `splash` section). All empty by
  default. `&` formatting codes work in both texts. `/solar announce [n]` replays a phase's announcement to yourself.
- Splash fonts are client textures in the layout of `minecraft:textures/font/ascii.png` (`splash.titleFont`, default
  `solarapocalypse:textures/font/splash.png`, not shipped yet; `splash.messageFont`); Minecraft's font while missing. The
  title flickers between `splash.titleColor` and `splash.flickerColor`.

## 3. Block effects
Per block, using the rules active in the running phase (`phases.convertRuleMode` and `phases.destroyRuleMode`, each
`CARRY` = every phase so far, the latest phase's rule winning per block (for conversions: per block and layer), or
`ISOLATED` = the running phase only):
1. **Destroy** (`destroy` selectors): removed once the depth reaches the block. Blocks reached while the rule's phase
   runs go layer by layer as the depth passes; blocks above the reference (trees, buildings) go top down over the first
   tenth of the phase; other blocks reached before the phase go at random but fixed moments within it.
2. **Convert** (`selector -> target`, target may be `air`): only the **surface layer**, the top `convertDepth` layers
   (default 1) of the column's current surface. The surface is the topmost opaque block, raised over blocking blocks and
   liquids stacked on it (glass, lava); plants, snow layers and the like on it belong to layer 1. Conversions start once
   the phase's destruction is done, at random but fixed moments over `convertDays`. Converting to air makes the block
   below the new surface, so leaves burn through a canopy while grass -> dirt stops at the top. Rules are resolved to
   their end: with grass -> dirt, dirt -> gravel, gravel -> sand in effect, grass becomes sand in one change.
   **Chances**: `dirt -> gravel @ 70%` converts 70 % of dirt and leaves the rest; rules matching a block share it out
   in order (`dirt -> sand @ 30%` after it covers the rest), a rule without `@` takes all that is left. Which blocks
   convert is a fixed hash of position, phase and block state, so a block's outcome never changes between looks and
   nothing has to be stored; a later phase with its own rule for the block (e.g. 100 %) takes over in `CARRY`. A carried rule keeps its roll, so
   blocks a chance passed over stay as they are; to strike again in a later phase (anvils damaged a bit more each
   phase), repeat the rule there: it rolls anew.
   **Layers**: layer 1 is the surface block and what stands on it, layer 2 the block below, and so on. Without a layer
   modifier a rule acts in the top `convertDepth` layers of the running phase. A rule carried from an earlier phase
   (`CARRY`) does too, except in an infinite phase, where it acts only in layer 1, the layer the erosion takes next
   (the user's design, turn 11: the carried rules blast the top layer, the infinite phase's own rules make a gradient
   ahead of the destruction line). `layer=2` limits a rule to layer 2, `layer=2-4` to layers 2 to 4, `depth=3` to the
   top 3 (= `layer=1-3`); these hold in every phase the rule is carried into. In each layer the latest phase with a
   rule acting there decides, and its rules acting there share the block out in order: a rule for other layers never
   takes a block's share, and older phases' rules still act in the layers a newer phase leaves alone. As erosion
   lowers the surface, a block moves up through the layers and each stage's rules reach it. The user's example, an
   infinite phase (`convertDepth` 5) after phases that turn grass to path, path to dirt, dirt to gravel, gravel to sand
   and sand to vitrified sand: `grass -> grass_path layer=5`; `grass -> dirt layer=4` and `grass_path -> dirt layer=4`;
   grass, grass_path and dirt `-> gravel layer=3`; grass, dirt and gravel `-> sand layer=2`, and
   `grass_path -> solarapocalypse:vitrified_sand layer=2`. Each layer below the surface is then one stage further
   along, and whatever reaches layer 1 becomes vitrified sand through the carried rules (dirt there turns to vitrified
   sand at once). Chances work in every layer, infinite phases included: a block rolls once per deciding phase and
   state, so `dirt -> gravel @ 50% layer=3` makes a patchy stage. A carried rule that reaches deeper in a later
   non-infinite phase (a bigger `convertDepth`) converts those new layers over that phase's `convertDays`; layers it
   reached before catch up at once. A block below every rule's layers is looked at again once the depth line has come
   close enough, and the cube below is queued as soon as a column's surface is within the deepest rule's layers of it.
   **Modifiers** follow the target in any order: `@ n%`, `layer=`, `depth=`, `dimensions=`, and `preserveState`, which keeps the source block's properties
   the target block has too (by name: facing, half, shape...) unless the target sets them:
   `minecraft:anvil[damage=0] -> minecraft:anvil[damage=1] @ 25% preserveState` damages a quarter of anvils and keeps
   their facing. The top half of a two-block plant or door (`half=upper`) never converts by itself: it pops off when
   its bottom half changes.
3. **Evaporation**: a liquid goes wherever the sun reaches it from the first phase whose `evaporate` list has it
   (selectors as for blocks, plus `fluid:<name>` and `temperature<K` / `<=` / `>` / `>=` on the Forge fluid temperature,
   water 300 K, lava 1300 K; e.g. `material:water, !temperature<250` early and `temperature<250` later for cold liquids).
   `evaporation.mode`: `INSTANT` clears whole bodies, `LAYERS` lowers a level from `evaporation.topY` (auto: sea level, or the CubicWorldGen
   preset's water level) at `layersPerDay`. With `blocks.blockPhysics`, liquids cannot form new sources once their
   evaporation has started.
4. **Fire**: once the surface block of a column has no conversion left in the phase, `ignitePercent` % of surface
   positions (chosen at random but fixed, over the conversion time) get **solar fire** on top: vanilla fire's looks,
   sound, light and entity burning, but it never ticks, so it never spreads, burns out or burns blocks. It stands on
   any surface that blocks movement (paths, glass, vitrified sand, slabs too) and goes only when that ground goes. Flammable surface
   blocks get vanilla fire instead at `igniteFlammablePercent` %, which then behaves as vanilla fire. Outside infinite
   phases one roll serves every phase, so the percent is the total alight: 25 % then 50 % keeps the first 25 % and
   lights as many again; fire is never put out between phases, only when its ground goes. In infinite phases fire is
   tagged with its layer and redrawn on every new layer. Punching the block under it, water, or a thrown water bottle
   (splash or lingering: the spot it hits and the four beside it, as vanilla does for vanilla fire) puts it out; it
   comes back on the engine's next look at the cube. Its steps are silent: an entity's step plays the sound of the
   block 0.2 below its centre, which is the fire when the centre hangs over it beside a ledge (vanilla fire sounds
   like wool there). Vanilla fire is left to vanilla (it spreads and burns out). `blocks.nightDousesFire` (default false): night puts solar fire out, each spot at its own moment
   between time of day 12000 and 14000, and lights the same spots again between 23000 and 1000; no new fire (solar or
   vanilla) is lit at night, vanilla fire already burning is left to vanilla. It reads the world's time of day, so
   sleeping and `/time set` take effect on the next look at each cube (in `TICKS` clock mode, when the cube's next look
   is due). After sleeping (to time of day 0) about half the spots light again on the next look and the rest within the
   next 1000 ticks (50 s); while players sleep no fire is visible anyway. No solar fire goes next to (diagonals too) a block that can burn: it
   would take the space vanilla fire spreads into, or look odd next to wood that never catches (fire spread off). Those
   positions just stay empty (checked on each look at the cube). Ice and snow next to or near solar fire melt as vanilla
   has them (ice at block light 9+, about 6 blocks from fire; snow at 12+, about 3 blocks), on their random ticks.
   Water flowing or placed into solar fire replaces it, as with vanilla fire. Rain does not put it out yet (vanilla
   fire dies in rain on its own random tick, which solar fire never gets): planned per phase, see TODO.md.

- **Vitrified sand** (`solarapocalypse:vitrified_sand`, `variant` sand or red_sand like vanilla sand): sand the sun
  melted in place, a rough, cloudy glass (translucent, culls against itself, sand-coloured on maps; mobs do not spawn on
  it, as on glass). Breaking it drops one sand of its variant; silk touch keeps the block. Placeholder textures.
- **Dimensions**: any convert, destroy or evaporate entry can end with `dimensions=-1` (or `dimensions=0,-1`;
  `dimensions=*` or nothing = every dimension in `world.dimensions`). Entries are filtered by dimension first, then the
  usual phase, CARRY and rule-order logic runs, so a rule scoped elsewhere never shadows an older rule, and there is no
  hidden priority: with `glowstone -> magma dimensions=-1` before `glowstone -> cobblestone`, Nether glowstone becomes
  magma and elsewhere cobblestone. Intrinsic changes (sponges drying, masonry cracking) fit `*`; environmental ones
  (netherrack to magma) a dimension.
- Selectors: `modid:name`, `modid:name:meta`, `modid:name[property=value,...]`, `modid:*`, `#oreDictName`, `material:<name>`, `*`. Wildcards skip
  unbreakable blocks. Several can be combined with commas, and `!` excludes: `material:rock, !minecraft:cobblestone`
  (a destroy list counts as one combined selector). Exclusions apply to the list or rule they are in, not to rules
  carried from earlier phases. Within a phase the first matching rule wins.
- Loops (A -> B -> A in one layer, also across phases in `CARRY`) are cut when the rules are compiled: the deciding
  rules of the loop member with the oldest phase are turned off from that phase on, with a warning in the log. Rules in
  different layers never loop (`dirt -> sand layer=1` with `sand -> dirt layer=2` is fine).
- **Weather** (`phase_n.weather`): `UNCHANGED` (as usual), `NONE` (no rain), `RAIN` (always raining), `THUNDER` (always
  a thunderstorm), or `INHERIT` (the default: the previous phase's; phase 1 `UNCHANGED`), so the defaults leave the
  weather alone and two settings give dry early phases and storms later. Held every second in the apocalypse's
  dimensions that have weather (`/weather` cannot change it meanwhile). Vanilla's other dimensions share the
  overworld's weather, so holding it for one of them holds the overworld's (and the other way round). The rain counter
  is set to half a day, so rain or a dry spell lasts that long into a following `UNCHANGED` phase; the thunder counter
  is kept at two days or more except in a storm, so a dry spell does not end in a thunderstorm (a storm's rain and
  thunder end together). Vanilla consequences: a thunderstorm darkens
  the sky enough that the world no longer counts it as day, so there is no direct sun damage while
  `entities.sunNeedsDaytime` applies, and players can sleep at any time (each sleep skips to the next morning, which
  the `SUN` clock counts); rain puts out burning mobs and vanilla fire under the open sky (not solar fire yet).
- Fire: no vanilla fire while the gamerule `doFireTick` is false (it would neither spread nor burn out) and none on
  `blocks.vanillaFireBlacklist` (default TNT); those blocks are treated like the rest (solar fire, which lights nothing).
- Changes use `setBlockState` flag 2|16 (clients told, neighbours not: nothing flows or falls); `blocks.blockPhysics`
  switches to flag 3. The blocks on top of and beside a change still get their neighbour update when their own neighbours
  are loaded (full blocks are not told), so what the new block cannot hold (plants and crops on paths, torches on top or
  on the side, ladders, top halves, fire) pops off at once; sand, gravel and liquids are left alone. A plant a rule
  makes where it cannot live (a dead bush on grass or path; vanilla keeps dead bushes on sand, dirt and hardened clay
  only) is removed at once instead of popping off later with drops. No item drops or container spills unless `blocks.dropItems`, also from blocks that pop
  off (Forge's `restoringBlockSnapshots` switch is set during the change).
- Defaults (the user's phase set): 1 safe day, 11 phases (days 1, 1, 2 x 7, 3, then 2). 1: grass, mycelium, farmland to Every phase announces itself with placeholder defaults for testing: message "Phase n", the thunder sound,
  splash "Phase One" (the number as a word).
  paths. 2: 30 % of paths to dirt, 30 % of snow burns, TNT goes; 25 % fire.
  3: the rest of those, wool and carpets, dirt to gravel, plants, leaves, gourds, webs, vines, ice, cacti burn; water evaporates (LAYERS,
  4 a day from sea level); 50 % fire. 4: wood burns, clay hardens, gravel to sand, conversions 2 deep; other liquids
  evaporate (`*, !material:lava`); 75 % fire. 5: sand melts to vitrified sand (red sand to red vitrified sand); 80 %. 6: stone and stone bricks crack to cobblestone; lava evaporates; 85 %.
  7-10: erosion of 1, 2, 3, 5 layers at one a day, conversions 2-5 deep; 90-95 %. 11: infinite erosion at 200 layers a
  day (one every 6 s); 100 %. Sun damage 0, 0.5, 1, 1.5, 2, 3, 4, 5, 6, 8, 10 per second by day, at night too from
  phase 7; heat 0.25 from phase 4 rising to 4; Fire Resistance protects from the sun, not the heat. Fire percentages
  are the same for solar fire and vanilla fire on flammables.

## 4. Engine (Cubic Chunks worlds)
- Cubes are queued on load (new and reloaded: the retroactive part), when a player (or a wand, an enderman...) places
  a block in them (so placing cannot hold the apocalypse off), on phase starts, on `/solar` time changes and
  reloads, and when their earliest pending change falls due (checked every 20 ticks, at most one look per cube per
  1/20 day, or per layer in an infinite phase, so erosion and its fire move layer by layer). When more is due than the
  budget allows, a cube whose look was due is brought up to its due time only and queued again for its next step, so
  every cube moves one step (a layer in an infinite phase) per round: the terrain comes down evenly at the speed the
  engine manages, a layer at a time, instead of in patches several layers deep (the user's trenches, turn 11); a phase
  start keeps those steps. Loads, placed blocks, time skips (sleeping, `/time`, `/solar set` and `add`) and reloads
  bring a cube straight to the present, and so does a look due within the last wake check (20 ticks: the engine is then
  keeping up). A cube still stepping through an earlier phase steps into the next phase at its start, so the new
  phase's rules reach it. A pass the budget cuts short
  resumes first on the next tick (it used to wait behind every queued cube, which cut stripes 16 blocks long into the
  terrain and evaporated water in slivers). `/solar status` shows how far behind the engine is. Queued cubes are processed
  once ready (populated, lit, surface-tracked), top-down per x/z. A block's own callbacks (vanilla fire placed, a block
  popping off) can load a neighbouring cube mid-pass; the engine copes (it is queued like any loaded cube), places
  vanilla fire only where its six neighbours are loaded, and logs the first such load once per game with its cause.
- The mod never slows, skips or alters the server's tick or the world's time (a day-length mod stays in charge); it only
  spends a bounded share of each tick's spare time.
- Time budget per server tick: the engine may spend at most `performance.tickBudgetMs` (4 ms) and at most
  `performance.freeTickShare` (50 %) of the time the rest of the server leaves free in a 50 ms tick (100-tick average),
  at least 0.5 ms. It is time spent by the engine, which runs after the world's own tick, checked before every change
  (a cube with many slow changes, e.g. lighting after trees burn, stops and continues next tick). A busy server slows the
  apocalypse instead of lagging.
- ...and at most `performance.maxBlockChangesPerTick` (512) changes per tick, also inside a cube, which caps what
  clients are sent and must redraw. A big ocean evaporating (2.2 M sources in the self-test's loaded area) then takes
  ~4400 ticks instead of ~160.
- Every config load logs the phase plan (days, depth, layer interval, rule and block-state counts) and warns about
  decreasing depths.
- `/solar status` shows the engine's average and slowest tick, block changes per tick and the server's tick time;
  `/solar fire [radius]` counts solar and vanilla fire in the loaded cubes around you and the fire lit since start.
- Fewer than 256 x/z per column change per tick (CC's client heightmap packet counts them in a byte).
- Nothing loads or generates a cube: a cube is processed only when it and what its blocks depend on are loaded,
  otherwise it waits, and catch-up makes it right later. The one exception is opt-in: `blocks.blockPhysics` notifies
  neighbours, which can generate a cube at a cube edge. Vanilla fire checks that its area is loaded (Forge).

## 5. Sky and the unknown above
CC counts never-generated cubes as air. A position counts as sun-exposed only when nothing opaque is known above it
**and** the cubes up to `world.skyClearance` (32) blocks above are loaded, or it is above the sky ceiling:
`world.skyCeilingY`, or in CubicWorldGen worlds the computed surface + `world.surfaceMargin` (48, for trees and
structures). Shade from CC's heightmap is always trusted. `world.sunFloorY` optionally makes everything below a Y shade.
Unknown positions wait (blocks) or count as shade (mobs).

The CubicWorldGen model scans the generator's density (the same 4x8x4 grid and interpolation CWG uses) from the
preset's height offset; it gives the highest solid terrain block before caves and decoration. Measured on the default
preset (3D noise): 98.6 % exact against generated ground, 0.3 % off by one, 1 % lower (caves, lakes), 0.15 % higher
(at most 5 blocks); 0.6 ms per new column, cached.

## 6. Sun damage
- Every `entities.intervalTicks` (20) each living entity is checked once, staggered by entity id.
- Direct: the zombie rule (sky visible from the eyes; only by day if `entities.sunNeedsDaytime`, default true, until
  phase `entities.sunAtNightFromPhase`, default 7): `sunDamage`, `sunFireSeconds`.
- Background heat: sky light at the eyes at least `entities.backgroundMinSkyLight` (4), day or night: `backgroundDamage`,
  `backgroundFireSeconds`. Overhangs, trees and houses with openings are no refuge; sealed rooms and caves are. Both
  add up in the sun.
- Damage source `solarapocalypse.sun` bypasses armour and is not fire damage; Fire Resistance protects as
  `entities.fireResistance` says (default `DIRECT`: the sun, not the heat). Skipped: creative/spectator players, armour stands, `entities.blacklist`, and
  fire-immune mobs only if `entities.spareFireImmune` (default false).

## 7. Checks
- `./gradlew test`: `TimelineTest` (depth and reach agree, also across infinite phases with different rates; two
  reference lines).
- `bash scripts/probe_server.sh <tag> selftest`: fresh default-preset world, on its own config `scripts/selftest.cfg`
  (the earlier five-phase set: 3 safe days, phases of 3 days, erosion in phase 5, plus a sixth `TOP_Y` phase at 64
  layers a day; phase 5 also has `stone -> cobblestone depth=3-4`, phase 4 `weather=NONE`, phase 6 `THUNDER`), so the
  checks do not move with the defaults. Last runs (2026-10-08):
  CWG model exact for 96.9-98.6 % of positions depending on the seed (the rest lower, from caves and lakes; at most a
  few blocks higher); rules check as expected (chances 30/20 % -> 3014 and 1977 of 10000 blocks, identical on a second
  look; exclusion, loop cut, TNT blacklist; evaporation lists with fluid and temperature selectors; `preserveState`:
  stone brick stairs facing east, top half -> stone stairs the same; `anvil[damage=0] -> anvil[damage=1] @ 25%
  preserveState` 2516 of 10000, facing kept; top half of a tall plant: no rule); grass under tall grass and a sunflower
  turned to path: all three pop off, 0 items dropped; phase 1 catch-up
  of ~10.7k cubes with every world tick made 12 ms slower than the 10 ms budget: 57-88 ticks; elsewhere
  with no change cap: 0.3-2.2 M blocks (mostly water) in 120-185 ticks, 0.8-1.7 s engine time; with the cap at the end:
  at most 512 changes in a tick; pigs, first hit in phase 4: sun 10 -> 5, under a roof 10 -> 9 (heat), 24 deep
  untouched; 8 layers of erosion below
  each column's surface: 0 of 441-511 k positions above the line still hold a block; solar fire at the end of phase 3
  on 4.2-4.9 % of positions (5 % asked; water and other non-solid tops get none, nor spots next to flammables, ice and
  snow), at the end of phase 4 10.0-10.2 % (10 % asked), phase 3's fire all kept but where its ground went (1801 of
  1802), unchanged after 200 ticks; with `nightDousesFire`: 0 at midnight, 4.9 % half way through the sunset (half of
  9.9 %), 9.9 % again the next morning; depth stages: the user's grass to glass example gives one stage per layer,
  `depth=2-3` acts in layers 2 and 3 only; after 8 layers of erosion, `stone -> cobblestone depth=3-4` left layers 3
  and 4 cobblestone (34174 of 34174, 34198 of 34199) and layers 1, 2, 5 stone (under 0.1 % off, where the check's
  top differs from the engine's surface); in the infinite phase 14.0-14.5 % (15 % asked), only the current layer's fire left;
  `TOP_Y` line of phase 6 at Y 26-45: 0 of 13-230 k positions above it still hold a block.
  Turn 11 (2026-10-10, two seeds): a config with `count` 2 gets no `phase_3` or later, a leftover `phase_5` is kept
  with its note; carried rules per layer: the user's gradient in an infinite phase gives 5 grass_path, 4 dirt,
  3 gravel, 2 sand, 1 vitrified_sand (carried rules in layer 1 only), the same for one block stepping up the layers; in
  a non-infinite phase with `convertDepth` 3 and no rules of its own the carried chain reaches 3 layers, the new ones on
  that phase's timing; `layer=1` and `layer=2` rules pointing at each other do not loop; thrown water bottles put out
  3 of 3 solar fires hit; weather: an inherited `NONE` stopped rain set before the phase, `THUNDER` held (rain counter
  11986); evenness: after the skip to 32 layers 98.2-100 % of columns lost 32 or 33 layers (the rest: caves and lakes
  under the reference); with the clock running a layer every 20 ticks and the change cap at 512 the engine fell 19
  layers behind and 100 % of columns still lost 32 or 33, across the phase 6 start (before the phase-start fix, 488
  columns jumped to the line there). Turn 10's run printed 0 for the chance and anvil checks and null for red sand
  (its throwaway phases had `convertDepth` 0, now counted as 1); it was reported as passing by mistake.
- In-game tests (the user): days skipped with `/time add`, then a bed to the next morning (from turn 9), so every phase
  up to the infinite one can be watched.
- `./gradlew runServer -Pno_dev_mods`: runs without Cubic Chunks. The user has checked splash, message, sound and
  burning in a client.
