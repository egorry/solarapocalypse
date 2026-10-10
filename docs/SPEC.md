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
  in full. After sleeping the engine carries on from where it was, a step (a layer in an infinite phase) per round
  at the normal lag-safe budget, so the world stays even and catches up behind the clock; it gets back to the present
  only if it is faster than the phase (the user's decision, turn 13: at 200 layers a day a night's sleep is about 90
  layers). After a `/time set` or `/time add` it jumps to the present and catches up cube by cube, for
  `performance.skipBoostSeconds` (30) with `skipTickBudgetMs` (20) and `skipMaxBlockChangesPerTick` (2048), faster with
  some lag. Players whose ground (the block under them) the erosion of an infinite phase took during a `/time` skip die
  ("scorched by the sun"), checked once per skip; creative and spectator players are spared. Sleepers no longer die:
  they fall when the erosion gets there. It stops while `doDaylightCycle` is
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
   runs go layer by layer as the depth passes; blocks above the reference (trees, buildings, and in SURFACE mode the
   sea and lakes over the terrain) go top down over the first tenth of the phase, `surfaceMargin` (48) above it and up
   first; other blocks reached before the phase go at random but fixed moments within it. That holds in the first
   phase on a depth line too (turn 13: there everything above the reference went at the phase start, so the user's test
   with phase 11 as phase 1 lost each cube's sea and trees in one go, cube by cube for minutes, the "raw chunk
   deletion", while the erosion waited for that round). In a fast first phase the terrain erodes meanwhile, so tree
   trunks can hang over eroded ground for that tenth (the user: the bandaid is enough, OUTOFSCOPE.md). A block under
   standing liquid (a vanilla water or lava source block, or any block of another fluid, anywhere above it in its
   column up to the column's surface: seas, lakes, pools) is not destroyed while the liquid is there: the sea floor and everything below it wait until the liquid has gone, by
   evaporation or a destroy rule, then catch up their missing layers at once, as the pass that takes the liquid carries
   on down the column (and queues the cube below); a held block is looked at again within 1/20 day at the latest. So a basin keeps its shape and drains flat with `LAYERS` evaporation, lakes too (turn 16,
   the user's choice, option (b) of their water question: before, the floor eroded from its own top under standing
   water, and a cavern opened under the sea, held up as block physics is off). Flowing vanilla water does not hold the
   ground: water that runs into a cut after a pass ebbs by itself, which no pass sees, so it would hold the ground below
   until the next look (seen in the self-test).
2. **Convert** (`selector -> target`, target may be `air`): only the **surface layer**, the top `convertDepth` layers
   (default 1; 0 turns every conversion off while the phase runs, carried and `layer=` rules included, as in phase 11
   of the defaults) of the column's current surface. The surface is the topmost opaque block, raised over blocking blocks and
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
   sand at once). With a single phase (or `ISOLATED`) nothing is carried, so layer 1 has only the phase's own rules: the
   user's one-phase copy of phase 11 (turn 12) left the grass on top as it was over vitrified sand at layer 2, until
   the erosion took it; a `layer=1` rule fills that in. Chances work in every layer, infinite phases included: a block rolls once per deciding phase and
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
   sound, light and entity burning (mobs and players catch fire, dropped items burn up; it tells Forge it burns, as
   vanilla's check only knows vanilla fire: before turn 13 nothing caught fire from touching it), but it never ticks, so it never spreads, burns out or burns blocks. It stands on
   any surface that blocks movement (paths, glass, vitrified sand, slabs too) and goes only when that ground goes. Flammable surface
   blocks get vanilla fire instead at `igniteFlammablePercent` %, which then behaves as vanilla fire. Outside infinite
   phases one roll serves every phase, so the percent is the total alight: 25 % then 50 % keeps the first 25 % and
   lights as many again; fire is never put out between phases, only when its ground goes. In infinite phases each
   surface block rolls once, so fire on a surface the line has not reached yet stays as it is, and when the erosion takes
   the block, the one below rolls for the new surface (turn 12: fire used to be redrawn on every layer of the line,
   which cost two changes per column per layer even while a TOP_Y line was still in the sky). The engine takes the old
   fire away before its ground goes, and where the block below is the new surface and stays, the block the erosion
   takes becomes the new fire in the same change (turn 14: ~1.85 changes per column per layer at 85 % fire, from ~2.7). Punching the block under it, water, or a thrown water bottle
   (splash or lingering: the spot it hits and the four beside it, as vanilla does for vanilla fire) puts it out; it
   comes back on the engine's next look at the cube. Its steps are silent: an entity's step plays the sound of the
   block 0.2 below its centre, which is the fire when the centre hangs over it beside a ledge (vanilla fire sounds
   like wool there). Its light is `blocks.solarFireLight` (default 8, vanilla fire 15; turn 16, the user's choice: less light
   is less lighting work, and 8 still keeps mobs from spawning in the fire's own space, as they need block light 7 or
   less, more often the darker; beside it, at 7, 1 try in 8 passes the light check, where 15 kept them off within ~7
   blocks), sent to clients on login and
   on `/solar reload` (clients light the world too and must agree); the flames are drawn full-bright whatever the
   level, and fire already burning keeps the light it was placed with until something relights the spot. Its model
   (client, `blocks.solarFireModel`): `FULL` is vanilla fire's (four crossed flames plus a flame wall on each side,
   double-faced: 12 faces; each part picks one of two or four texture variants), `SIMPLE` the crossed flames only (4
   faces); read when models bake (game start, F3+T). Vanilla fire is left to vanilla (it spreads and burns out). `blocks.nightDousesFire` (default false): night puts solar fire out, each spot at its own moment
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
  melted in place, a rough, cloudy glass, opaque (turn 16, the user: more sand than glass; it was see-through until
  then): no light or sight through it, drawn in the solid pass, the same texture without its transparency. Glass
  material: glass sounds, no tool needed, mobs do not spawn on it (as on glass); sand-coloured on maps. Breaking it drops one sand of its variant; silk touch keeps the block. Placeholder textures.
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
  are loaded (full blocks are not told; nothing around a change of air or fire, which hold nothing; solar fire beside
  a change, which only minds its ground), so what the new block cannot hold (plants and crops on paths, torches on top or
  on the side, ladders, top halves, fire) pops off at once; sand, gravel and liquids are left alone. A plant a rule
  makes where it cannot live (a dead bush on grass or path; vanilla keeps dead bushes on sand, dirt and hardened clay
  only) is removed at once instead of popping off later with drops. No item drops or container spills unless `blocks.dropItems`, also from blocks that pop
  off (Forge's `restoringBlockSnapshots` switch is set during the change).
- Defaults (the user's phase set): 1 safe day, 11 phases (days 1, 1, 2 x 7, 3, then 2). Every phase announces itself
  with placeholder defaults for testing: message "Phase n", the thunder sound, splash "Phase One" (the number as a
  word). 1: grass, mycelium, farmland to paths; no fire. 2: 30 % of paths to dirt, 30 % of snow burns, TNT goes; 5 % fire.
  3: the rest of those, wool and carpets, dirt to gravel, plants, leaves, gourds, webs, vines, ice, cacti burn; water evaporates (LAYERS,
  4 a day from sea level); 10 % fire. 4: wood burns, clay hardens, gravel to sand, conversions 2 deep; other liquids
  evaporate (`*, !material:lava`); 25 % fire. 5: sand melts to vitrified sand (red sand to red vitrified sand); 30 %. 6: stone and stone bricks crack to cobblestone; lava evaporates; 40 %.
  7-10: erosion of 1, 2, 3, 5 layers at one a day, conversions 2-5 deep; 50, 60, 70, 75 %. 11: infinite erosion at 200
  layers a day (one every 6 s) with no conversions (`convertDepth` 0, turn 16, the user: the fancy conversions happen in
  phases 1-10, and phase 11 should run at decent speed on most PCs; from turn 13 to 15 the user's gradient ran ahead of
  the erosion, layer 6 grass to path down to vitrified sand in layer 2, the carried rules in the top layer); no fire (turn 15, the user: too hot for fire to burn; fire was
  the costliest part of a layer, and the phase 10 fire goes with the first layer). Sun damage 0, 0.5, 1, 1.5, 2, 3, 4, 5, 6, 8, 10 per second by day, at night too from
  phase 7; heat 0.25 from phase 4 rising to 4; Fire Resistance protects from the sun, not the heat. Fire percentages
  (the user's table, turn 13) are the same for solar fire and vanilla fire on flammables; phase 11 has none.

## 4. Engine (Cubic Chunks worlds)
- One clock per world (the engine clock): every cube is brought up to it, and it moves on only after a round, once
  every cube due by it has been processed. A round takes the cubes whose earliest pending change falls due by the clock
  (at most one look per cube per 1/20 day, or per layer in an infinite phase), the cubes below that a pass opened, and
  the cubes that loaded (new and reloaded: the retroactive part) or had a block placed in them (by a player, a wand, an
  enderman..., so placing cannot hold the apocalypse off) since the round before. While the engine keeps up, the clock
  is the present (moved every 20 ticks). While it cannot, the clock moves one step per round (a layer in an infinite
  phase; straight to the next change due when nothing is due before it), so the whole loaded world comes down evenly at
  the speed the engine manages, an onion layer at a time, and a cube that loads or wakes meanwhile joins at its
  neighbours' layer. Turn 11's per-cube steps could not do that: a cube that loaded, or woke for its own first layer,
  went straight to the present, which cut whole cubes out (the user's pits, and caverns under standing ground, turn
  12). The clock stops at each phase's start, where every loaded cube is looked at, and is saved per dimension
  (`data/solarapocalypse.dat`), so a restart carries on where the engine was. `/time` skips and `/solar set`, `add`
  and `phase` bring it straight to the present (one round then catches every cube up, cube by cube); sleeping and a
  config reload keep it. Each round goes nearest the players first (by column distance to the nearest player, each
  column top down), so a round the budget spreads over many ticks reaches their surroundings first and spreads out
  from them (turn 13: rounds came in no particular order, which the user saw as random chunks changing). A pass the budget cuts short resumes first on the next tick (it used to wait behind
  every queued cube, which cut stripes 16 blocks long into the terrain). `/solar status` shows how far behind the
  engine's clock is. Queued cubes are processed
  once ready (populated, lit, surface-tracked), top-down per x/z. A pass stops going down a column at the first block
  nothing can change yet: under the column's top (out of the sun, so no liquid there evaporates), below every depth
  line, and deeper below the surface than the running phase's conversions reach; the cube's next look is then when a
  line reaches that block (or brings the surface within the conversions' layers of it). Turn 16 (the user's throughput
  item): until then every block of the cubes the line was in was looked at on every layer, most of the engine's time
  once fire and conversions were gone. A block's own callbacks (vanilla fire placed, a block
  popping off) can load a neighbouring cube mid-pass; the engine copes (it is queued like any loaded cube), places
  vanilla fire only where its six neighbours are loaded, and logs the first such load once per game with its cause.
- The mod never slows, skips or alters the server's tick or the world's time (a day-length mod stays in charge); it only
  spends a bounded share of each tick's spare time.
- Time budget per server tick: the engine may spend at most `performance.tickBudgetMs` (4 ms) and at most
  `performance.freeTickShare` (50 %) of the time the rest of the server leaves free in a 50 ms tick (100-tick average),
  at least 0.5 ms. It is time spent by the engine, which runs after the world's own tick, checked before every change
  (a cube with many slow changes, e.g. lighting after trees burn, stops and continues next tick). A busy server slows the
  apocalypse instead of lagging.
- ...and at most `performance.maxBlockChangesPerTick` (2048 since turn 15, the user's setting; 512 before) changes per tick, also inside a cube, which caps what
  clients are sent and must redraw. 64 or more changes in a cube in one tick make Cubic Chunks resend the whole cube
  (Forge's `clumpingThreshold`), and each changed cube is re-meshed by clients. Infinite SURFACE erosion through stone
  makes 1 change per x/z column per layer without fire and ~1.85 with 85 % fire (old fire away, the block taken
  becoming the new fire; ~2.7 before turn 14), plus the gradient's conversions while it passes through soil, so a
  render distance of 12 (625 columns, 160 k x/z) needs ~300 k changes a layer with 85 % fire, ~160 k without: at 2048
  a tick ~146 and ~80 ticks, about 160 and 300 layers a 20-minute day where the time budget allows (512: ~40 with
  fire). The user's turn 15 test at 2048 and 4 ms: ~100 a day with fire, 200 (kept up) without. Vertical view distance hardly
  matters (only cubes at the line work). Faster: raise `maxBlockChangesPerTick` and `tickBudgetMs`, or lower
  `layersPerDay` to what the engine manages (`/solar status` shows how far behind it is). A big ocean evaporating (2.2 M sources in the self-test's loaded area) then takes
  ~4400 ticks instead of ~160.
- Where 512 comes from and what the limits cost (turn 13 research, the user's question): 512 was a starting value from
  a suggestion the user relayed in turn 3 ("around 512", against client and network bursts), not a measured limit.
  The user's turn 13 TOP_Y test: the cap bound throughout (512.0 changes a tick) while the engine used 0.7-1.3 ms of
  its 4 ms (about 1.5-2.5 µs a change) and the whole server 6-10 ms of its 50 at 20 TPS. Through solid terrain at
  their view distance (~1,100 columns, ~3 changes a column a layer, ~0.8 M changes a layer) that is ~14 layers a day;
  keeping 200 a day there needs ~7,000 changes a tick. Costs per setting:
  `maxBlockChangesPerTick`: speed while it binds; server time grows with it (the engine's own time, plus lighting,
  which Cubic Chunks defers until a cube is sent or its light read, so it shows in the server's tick time, not the
  engine's), and about one whole-cube resend per ~700 changes (each player watching gets it: a few KB compressed,
  nothing in singleplayer, where the client and server share memory; the client re-meshes each cube on its builder
  threads). `tickBudgetMs`: the engine's own time per tick; binds before the cap on slower machines. `freeTickShare`:
  never more than this share of the time the rest of the server leaves free, so a busy server slows the apocalypse
  instead of lagging. `skip*`: the same after `/time`. `layersPerDay` and horizontal view distance set the demand
  (columns grow with the square of the distance); vertical view distance hardly matters. Forge's `clumpingThreshold`
  and Cubic Chunks' `cubesToSendPerTick` (new cubes only) are best left alone. Nothing crashes at higher values: the
  budget is checked before every change, so no tick can run long enough for the server watchdog (60 s, dedicated
  servers); Forge splits packets over 1 MB; the one hard network limit, CC's heightmap packet (under 256 x/z per column
  per tick), is guarded separately. What can go wrong: a lower TPS if the budget is set high on a busy server, client
  stutter (many cubes re-meshed), and in multiplayer upload bandwidth (~2 KB per cube resent per player watching; slow
  connections can time out).
- What a layer costs (turn 14, the user's questions; measured with `scripts/probe_server.sh <tag> bench` on the dev
  server: the user's turn 14 test config, one seed, no cap, a 10 ms budget, ~550 columns loaded, the clock kept ahead so
  the engine works flat out). 85 % fire: 33 layers a minute, ~261 k changes a layer, ~1.4 µs of engine time a change,
  ~990 ms of server time a layer (turn 13's engine: 23, 379 k, ~1,190 ms). The same fire without its light: the same
  speed, 428 ms a layer. No fire: 57 layers a minute, 143 k changes and 261 ms a layer. The budget (time and cap)
  counts every change the engine makes: destruction, conversions, evaporation, fire placed and removed; its time also
  covers looking at blocks that do not change. Lighting is outside it: Cubic Chunks relights after the engine's changes
  (in its own tick and when cubes are sent), and with 85 % fire that is about half the server's time per layer, as the
  fire's light field shifts down with every layer (~15 light values a column). Conversions cost only while the
  gradient passes through soil (dirt, sand, gravel: the first layers, deserts, beaches); in stone nothing converts.
  Evaporation costs once per liquid block (`INSTANT`: all at the phase start; `LAYERS`: spread out). The user's turn 14
  test (2048 and 4 ms, render distance ~12-14, turn 13's engine): ~1,750 changes and ~3.5 ms a tick (both limits
  binding), 77 layers a day of 200, the server 11-14 ms a tick, 70-120 fps (140-200 before the apocalypse). With turn
  14's engine the same settings should give ~110 a day; 200 a day needs ~3,200 changes and ~6 ms of engine time a tick,
  ~20-25 ms of server time a tick (20 TPS holds). The client: every surface cube in view is resent whole and re-meshed
  once per layer whatever the cap (the cap only paces it; a resent cube also marks the render chunks beside it), so its
  work grows with layers per day and view distance; each floor fire is vanilla's model, 12 cut-out faces (turn 14 said
  ~40, wrongly: each part of the model picks one of its variants), so 85 % fire adds ~10 faces a column to the
  terrain's 1-2 on top, and fire near the player smokes and crackles (vanilla's display ticks). Vanilla fire is the user's biggest FPS cost (240 -> 80 fps): it changes
  blocks, light and meshes wherever it spreads. Multiplayer: a vanilla server sends a keep-alive every 15 s and drops a
  player ("Timed out") whose previous one is still unanswered when the next is due, so a player whose connection carries
  less than the server sends them falls behind and is dropped once the backlog passes ~15-30 s. The data rate is the
  cubes resent in their view per second: `layersPerDay` and view distance in the long run, `maxBlockChangesPerTick`,
  `tickBudgetMs` and the `skip*` boost for bursts. Singleplayer has no network.
- Fire, light and conversions (turn 15, the user's questions: what stone stages in phase 11 would cost, and the savings
  without fire or conversions; the same bench, seed and settings; layers a minute / changes a layer / server time a
  layer): 85 % fire with the gradient 33 / 258 k / 992 ms; the same at light 4 32 / 259 k / 599 ms (light 0: 428, so 4
  saves ~70 % of the light's cost); no fire, the gradient 54 / 145 k / 273 ms; no fire, no conversions 67 / 134 k /
  220 ms; no fire, the user's stone stages added (stone -> cobblestone in layer 5, then gravel 4, sand 3, vitrified
  sand 2) 20 / 632 k / 869 ms; no fire, grass, dirt, gravel, sand and stone straight to vitrified sand in layer 1
  (`convertDepth` 1) 39 / 262 k / 362 ms. Each conversion stage costs one change per column per layer in stone (four
  stages: ~4.7 changes a column a layer instead of 1). A cube's stages change in the same pass, so clients get about as
  many resends (one a layer, plus the cube below where the stages reach into it). Vitrified sand was see-through: a
  surface of it let sky light into the block below (lighting work on every layer) and clients drew it in the
  translucent pass (opaque from turn 16). The no-conversion run started deeper (27 layers in, the others 6-12), below
  most soil.
- Turn 16 (the same bench and seed; layers a minute / changes a layer / engine time a change / server time a layer),
  with the engine stopping down each column at the first block nothing can change yet: no fire, no conversions 96 /
  134 k / 0.83 us / 162 ms (turn 15's code on the same day: 72 / 132 k / 1.22 us / 202 ms; over 300 ticks, as in 1200
  this variant ran through the loaded terrain); no fire, the turn 13-15 gradient 67 / 143 k / 1.17 us / 215 ms (turn
  15: 54 / 145 k / 1.51 us / 273 ms); no fire, everything to vitrified sand in layer 1, opaque 64 / 266 k / 0.66 us /
  220 ms, see-through (turn 15's block, the same code) 52 / 267 k / 0.83 us / 268 ms: opaque vitrified sand gives ~23 %
  more layers, as the sky light no longer goes through it; 85 % fire with the gradient at light 15, 8 and 4: 42 / 42 /
  40 layers a minute, 839 / 602 / 483 ms of server time a layer (the engine's own time about the same, 1.07-1.13 us a
  change): less light is less lighting work, outside the engine's budget, so at 8 the server spends ~30 % less time a
  layer than at 15. The first attempt at the water hold (only the block right under a liquid waited) left a crust over
  eroded ground and cost a quarter of the layers; it was replaced before release.
- Clients (Cubic Chunks 0.0.1271): a cube resent whole replaces the client's blocks but keeps its tile entities, also
  where the block is gone (vanilla's chunk packets drop them; Cubic Chunks' `PacketCubes` does not), so a destroyed
  spawner kept making flames for the user (turn 15). The client removes tile entities whose block no longer has one,
  once a second (`client/StaleTileEntities`). The server's own tile entities go with their blocks as usual.
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
- Drops: with `blocks.dropItems` false (default), a mob killed by the sun or its heat (damage source
  `solarapocalypse.sun`), or by fire the apocalypse set (the sun's or heat's `...FireSeconds` plus a second, or solar
  fire's 8 seconds after it touched it; marked in the entity's Forge data) drops nothing (turn 15: the user found 757
  items from night spawns dying at once). Players always drop their things; a mob a player kills drops as usual; mobs
  killed without a player hitting them drop no experience in vanilla anyway.

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
  Turn 12 (2026-10-10): TOP_Y line through solid terrain (from just below the lowest ground), the clock a layer every
  20 ticks for 800 ticks and the engine at 4096 changes per tick (still slower), a 2 x 2-column area reloading at tick
  400: 0 terrain blocks gone under standing ground and 0 below the engine's line; the turn 11 engine on the same check
  (`t12old5`): 84,510 blocks gone under standing ground in 42,849 columns, the user's caverns (cubes below woke for
  their own first layer while the ground above lagged). Two blocks counted before the check kept to terrain materials
  were mineshaft planks the carried `wood -> air` rule burnt once they became the surface (correct). Evenness under a
  capped engine 98.3-99.8 % (several seeds). On one hilly seed the SURFACE line check found 72-85 blocks above the line:
  flowing water (levels 4-5) that ran into the cut after the pass (the next layer's look removes it), not blocks the
  erosion missed; the check now names what it finds.
  Turn 13 (2026-10-10, three seeds): one infinite SURFACE phase as the first phase: at its start water 1 and 20 above
  the ground stays, 53 above goes, the ground stays; a tenth in, all of it gone, top first. Cobblestone dropped on solar
  fire burns up, the same item under a roof stays. Terrain gone below the SURFACE line, counted against a snapshot
  taken before the skip: 0, after the catch-up and with the engine 18 layers behind; evenness 94.9-99.7 % by seed (the
  rest are caves and lakes under the line, now opened; the snapshot shows none of it was cut by the engine). TOP_Y line
  behind the clock: 0 caverns, 0 below, 0 left above; a low seed took the line into the unpopulated cubes at the bottom
  of the loaded range, which the engine never touches, so the terrain checks now count only cubes it works on.
  Turn 14 (2026-10-10): the self-test passes unchanged with the fire made cheaper (fire shares, epochs, night dousing,
  bottles, erosion fire 15.1 % of 15 % with only the current layer's, line checks 0, evenness 99.2 %, TOP_Y lag 0 / 0 /
  0). `bench` (above, section 4): fire coverage after a minute of layer-by-layer erosion 84.5-84.8 % for 85 %, as with
  turn 13's engine (85.0 %); ~1 % of fires carry an older layer, as before (ground the line has not reached).
  Turn 16 (2026-10-11): the self-test's own config pins `blocks.solarFireLight` 15. On seed 14014 the results are the
  same with and without the engine's stop down each column (line checks 0, evenness 99.7 %, TOP_Y lag 0 / 0 / 0) and
  the same as turn 15's code; the engine stops going down a column 100 below the ground and looks again when the line
  gets there. A stone pillar under a water source in the eroded zone (water out of every destroy and evaporate list)
  keeps 2 of 2 stones; once the water is taken away, 0 of 2. The evenness check names the top block of columns short
  of the rest; with flowing water holding the ground too, one column on that seed was a layer short right after the
  catch-up (its water had ebbed by itself), so only standing liquid holds now. Turn 16's first run found the
  conversion checks at 0 because throwaway phases had `convertDepth` 0, which now means no conversions; a phase's
  `convertDepth` defaults to 1 in code too.
- In-game tests (the user): days skipped with `/time add`, then a bed to the next morning (from turn 9), so every phase
  up to the infinite one can be watched. From turn 13, phase 11's settings as the only phase, starting after a quarter
  day, so it runs as in play without skips (no backlog from a jump). Turn 14 ("Phase 11 SURFACE Test 5", 2048 changes
  and 4 ms, evaporation `INSTANT`, `material` rules turning plants, leaves, wood and the like to air): 77 layers a day,
  91 behind at depth 145 (Y -93), 70-120 fps; `/solar reload` applies config changes in play (confirmed).
  Turn 15 (the same world continued, turn 14's engine, 2048 and 4 ms): with 85 % fire the clock moved 10 layers while
  the engine moved 5 (~100 a day of 200), 1,815 changes and 3.1 ms a tick, the server 13.1 ms a tick, 127-230 fps,
  22,548 cubes queued; after `/solar reload` with no fire the engine kept up and gained slowly (37 layers in the
  clock's 37, then 15 in 14), 1,240-1,840 changes and 3.1-3.7 ms a tick, the server 9.6-9.7 ms, 150-225 fps, 993-2,607
  queued; the CPU fans rose with each layer, a bit less without fire. One 2.4 s stall ("Can't keep up") came 13 s after
  a save of ~2,400 cubes, while the engine's slowest tick was 26 ms. 757 dropped items lay around from night spawns
  dying at once (now none: section 6); spawner flames stayed where spawners were destroyed (fixed: section 4).
- `./gradlew runServer -Pno_dev_mods`: runs without Cubic Chunks. The user has checked splash, message, sound and
  burning in a client.
