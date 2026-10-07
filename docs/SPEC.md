# Solar Apocalypse: design spec (turn 2)

How the mod works as built, the decisions behind it, and what is still open. The user's feature list is
[DESIGN.md](../DESIGN.md); the feasibility research is [RESEARCH.md](RESEARCH.md). Only Cubic Chunks is a (soft)
dependency; nothing is tailored to any modpack.

## 1. Status

Built and checked on a headless dev server (`bash scripts/probe_server.sh <tag> selftest`, section 9):

| Feature | State |
|---|---|
| Config `config/solarapocalypse.cfg`, any number of phases (`phases.count`), `/solar reload` | done |
| Clock: own saved progress, SUN or TICKS mode, pause while empty | done |
| Phase lengths: per phase or base + scaling (constant, linear, quadratic, exponential) | done |
| Block conversions and erosion (destroy lists with a depth line), Cubic Chunks worlds | done |
| Evaporation of water, lava, other liquids (INSTANT or LAYERS) | done |
| Retroactive catch-up of cubes loaded later (new or reloaded) | done |
| Sun damage (direct) and background heat (under thin cover), fire, Fire Resistance option | done, vanilla and CC |
| `/solar status|set|add|phase|pause|resume|reload` | done |
| Vanilla (non-cubic) worlds: block changes | not yet (entity damage already works there) |
| Ignition (percentage of exposed blocks, own non-spreading fire block) | not yet |
| Leaf culling when logs go first | not yet |
| Pre-first-light hook for new cubes (section 7) | not yet |
| CubicWorldGen terrain model (exact sky ceilings, section 6) | not yet |
| Simple Difficulty integration | not yet |

## 2. Time

- Progress is a `long` in the overworld's `data/solarapocalypse.dat`; 1 day = 24000 units. `/time set` cannot rewind it.
- `clock.mode = SUN` (default) adds the forward movement of the overworld's `worldTime` each tick. A day lasts as long
  as the sun takes, so any day-length mod is followed without integration; sleeping counts; jumps larger than
  `clock.maxSunJump` (default one day: `/time set`) are ignored; it stops while `doDaylightCycle` is false.
- `clock.mode = TICKS` adds one tick each server tick; `clock.ticksPerDay` sets the day length (for permanent-day
  servers, or to count a 72000-tick long day as one day).
- `clock.progressWhileEmpty = false` (default) pauses while nobody is online; `true` keeps counting, and terrain
  catches up when it loads.

## 3. Phases and the sun's depth

- Phase 0 (`phases.safeDays`) is quiet. Phases 1..n follow, each `days` long (0 = `phases.baseDays` x scaling).
- Each phase has a **depth**: how many layers below `world.topY` the sun reaches by the end of the phase (layer 1 is
  `topY` itself), or `infinite`. Depth never decreases; once infinite, every later phase is infinite and keeps the
  last positive `layersPerDay`.
- `speed`: `PHASE` reaches the depth evenly over the phase; `RATE` at `layersPerDay`, and the phase lasts until its
  layers are done (DESIGN.md's "added to the phase time"); `INSTANT` at the phase start.
- Everything is a pure function of progress (`Timeline`), so a cube loaded on day 40 gets exactly what a cube watched
  since day 0 got.
- **`world.topY`** (the user's answer in turn 1): a fixed Y, or `auto` = the height the world's generator reports
  (`ICubicWorld.getMaxGenerationHeight()`: CubicWorldGen's preset estimate, e.g. 256 for the default preset and about
  2049 for `CC - Migrated`; the world height in vanilla). See question Q1: with `auto`, depth is mostly spent on air.

## 4. Block effects (Cubic Chunks worlds)

Per block, in phase order (rules of every started phase apply, each to the previous result):
1. **destroy** (erosion): blocks matching the phase's destroy selectors are removed once the depth line has reached them.
   A block reached during the phase goes when the line passes it (layer by layer); one reached before the phase goes at
   a random but fixed moment within the phase.
2. **convert**: blocks matching a `selector -> target` rule change when they are **sun-exposed** or **reached by the
   line**, at a random but fixed moment within the phase (`INSTANT`: at its start). `target` may be `air`, so surface
   burning (leaves, plants) is a conversion: it only touches what the sun sees, and each removal exposes the next block.
3. **evaporation**: sun-exposed liquids go from `evaporation.waterPhase` / `lavaPhase` / `otherLiquidsPhase` on.
   `INSTANT` (default) clears whole bodies top-down; `LAYERS` lowers a level from `evaporation.topY` (auto: the world's
   sea level, or a CubicWorldGen preset's water level) at `layersPerDay`.

- Selectors: `modid:name`, `modid:name:meta`, `modid:*`, `#oreDictName`, `material:<name>`, `*`. Wildcards never match
  unbreakable blocks (bedrock, barriers, portal frames, command blocks). Within a phase the first matching rule wins.
- Sun-exposed = the vanilla rule (light opacity): glass, lava, plants and snow layers let the sun through; leaves, water,
  ice and solid blocks do not. A block is exposed when nothing opaque is above it.
- Changes use `setBlockState` flag 2|16: clients are told, neighbours are not, so nothing flows or falls
  (`blocks.blockPhysics = true` switches to flag 3). No items drop and containers do not spill
  (`blocks.dropItems = true` drops them).
- Defaults (5 phases, 3 days each, 3 safe days): 1 grass/mycelium/farmland/path to dirt, plants, vines, cacti, snow and
  ice burn, water and lava evaporate, mobs in the sun catch fire; 2 leaves, gourds, cobwebs burn, 1 damage; 3 wood,
  wool, carpets, TNT burn, dirt turns to sand, 2 damage plus background heat under 1 block; 4 clay hardens, gravel to sand,
  4 damage, background under 3 blocks; 5 erosion of everything, infinite, 16 layers a day, 8 damage, background under 8.

## 5. Engine (Cubic Chunks)

- Cubes are queued on `CubeEvent.Load` (new and reloaded: the retroactive part), on every phase start, on `/solar`
  time changes and config reloads, and when the earliest pending change in them falls due (checked every 20 ticks, at
  most one look per cube per 1/20 day).
- A queued cube is processed once ready (`isFullyPopulated && isInitialLightingDone && isSurfaceTracked`); not-ready
  cubes are looked at again every 20 ticks. Processing walks each x/z from the top down, so one pass clears a canopy or
  a lake inside the cube; a cube whose bottom opens up queues the cube below.
- Budget: `performance.tickBudgetMs` (default 10 ms) per server tick, 1 ms while the average tick exceeds
  `performance.lagThresholdMs` (45 ms). Fewer than 256 x/z per column change per tick (CC's client heightmap packet
  counts them in a byte).
- Nothing loads or generates a cube: the engine reads loaded cubes and CC's heightmaps only.

## 6. Sky and the unknown above (user's Q1, turn 1: pros and cons, and how combining them changes things)

CC counts never-generated cubes as air, so "nothing opaque above" can be wrong deep underground. The approaches:

| Approach | Pros | Cons |
|---|---|---|
| A. CC heightmap alone (what vanilla zombies use under CC) | free, exact once the cubes above were ever lit | false sky deep underground where the cubes above never existed; acting on it is irreversible for blocks |
| B. Known-above: the cubes up to `world.skyClearance` (32) blocks above must be loaded | any generator, no cost, loads nothing | false sky only in an open void taller than the clearance reaching the top of the loaded area (rare); positions whose cubes above are not loaded wait (mountain tops above your view range, the surface while you are deep in a mine) and catch up later |
| C. Sky ceiling (`world.skyCeilingY`) | above it, answers are immediate | the user must know the terrain's maximum; decides nothing below it |
| D. CubicWorldGen terrain model (per column, computed from the generator) | exact for terrain in CWG worlds, also with 3D noise; no waiting | reflection into CWG internals (version-specific), a few ms per new column (cached), CWG only, no `cubeAreas` presets |
| E. `world.sunFloorY` | free, blunt "deep is always shade" | a hard line; with infinite erosion the exposure-based effects stop there |

**Built: A for shade (always trusted) + B for sky, with C and E optional.** Combining matters in two ways: A alone acts
on false sky, and B alone would wait forever on columns whose terrain top is above every loaded cube; C (and later D)
removes that wait wherever it applies, and E bounds how deep anything can count as exposed. D is the upgrade for CWG
worlds (it would let a surface be processed while you are underground). The erosion line (section 3) does not depend
on the sky at all: it is a pure function of Y.

Entities use the same three states: exposed (direct sun), covered by n sun-blocking blocks (background heat when
n <= `coverDepth`), unknown (no damage).

## 7. Pre-first-light hook (user's Q5, turn 1: context)

When CC creates a cube it generates the terrain, decorates it (trees, ores, lakes), lights it, and only then sends it to
players. The engine above processes a cube after that, so a fresh cube at the view edge can be seen unchanged for a
moment before grass turns to dirt or a lake vanishes, and every change costs a light update and a packet. A small
Cubic Chunks-only mixin can run the same processing in the gap between decoration and first light: the cube is lit and
sent already scorched (no pop-in) and the changes cost no light work and no packets. It applies only to newly generated
cubes (reloaded cubes still take the normal path) and only when CC is installed. It is an optimisation; nothing depends
on it. Planned after the core is settled.

## 8. Sun damage

- Every `entities.intervalTicks` (20) each living entity is checked once, staggered by entity id.
- Direct: sky visible from the eyes (section 6) and daytime (`entities.sunNeedsDaytime`): `sunDamage` and
  `sunFireSeconds` of the current phase.
- Background: at most `coverDepth` sun-blocking blocks above the eyes, day or night: `backgroundDamage`,
  `backgroundFireSeconds`. Applies in the sun too (both add up).
- Damage source `solarapocalypse.sun`: bypasses armour, is not fire damage, so Fire Resistance protects only as
  `entities.fireResistance` says (NONE, DIRECT, BACKGROUND, BOTH). The fire it sets is ordinary burning, which Fire
  Resistance always stops (vanilla).
- Skipped: creative and spectator players, armour stands, fire-immune mobs (`entities.spareFireImmune`), ids in
  `entities.blacklist`.

## 9. Checks

- `./gradlew test`: `TimelineTest` (depth, phase stretching, infinite inheritance, reach times).
- `bash scripts/probe_server.sh <tag> selftest`: fresh default-preset CC world, default config. Results (2026-10-07):
  - phase 1 catch-up of 10756 loaded cubes: exposed grass 27522 to 0, exposed plants 10410 to 0, water 39174 to 1142
    (only unexposed water left), 459529 blocks changed in 124 ticks, 784 ms engine time (1.7 µs per block; mostly water);
  - phases 2-4: every leaf gone, exposed dirt to sand;
  - erosion, phase 5 + 10 days (160 layers below topY 256 = line at Y 97): nothing eroded, the terrain tops out at Y 77
    (Q1);
  - pigs in phase 4 (4 sun + 1 background): in the sun dead after two hits, under a one-block roof 1 damage, 24 blocks
    deep no damage;
  - 79221 conversions in 1.2 s engine time (15 µs per block including the scan of untouched cubes).

## 10. Questions for the user

- **Q1. Depth reference.** You chose a fixed top Y (or the height CC reports). That makes the depth an absolute line:
  phase depth d affects terrain above `topY - d + 1`, the same Y everywhere. Consequences: mountains erode first and
  valleys only when the line gets down to them; with `topY = auto` the first layers are air (default CWG preset: 256 vs
  terrain at 60-80, so 160 layers erode nothing; `CC - Migrated`: 2049 vs terrain from about 230). The alternative is
  depth counted from each column's own terrain surface: every column loses the same thickness at any altitude, which
  reads more like "how deep the sun penetrates". It needs that surface per column: exact in CubicWorldGen worlds
  (section 6 D), recorded when a column first generates elsewhere (columns that existed before the mod use their top
  when first seen, so a tower standing then raises that column's reference). Both run on the same engine; only the
  reference changes. Keep the fixed top Y, switch to the per-column surface, or offer both?
- **Q2. Rule scope.** Conversions act on sun-exposed blocks and on everything above the line; destroy lists act only
  above the line (destroying "everything the sun sees" would never stop, since each removal exposes the next block).
  Surface burning of leaves and plants is a conversion to `air`. Agreed?
- **Q3. Cumulative rules.** Phase 4 still applies phase 1's conversions (grass that appears later, terrain loaded later).
  Needed for retroactivity; agreed?
- **Q4. Transparent results.** A conversion into a block the sun passes through (sand to glass) exposes the block below,
  so it cascades down through a whole sand deposit. Keep (consistent with the vanilla sky rule), or count such blocks as
  cover for block effects?
- **Q5. Background heat** = at most `coverDepth` sun-blocking blocks above the mob, day and night, as a per-phase
  setting. Is that what "above ground / undercover" should mean?
