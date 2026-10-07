# Solar Apocalypse: how it works

The behaviour as built. The user's feature list is [DESIGN.md](DESIGN.md); feasibility research is
[RESEARCH.md](RESEARCH.md); what is left is in [tasks/TODO.md](tasks/TODO.md). Every setting named here is documented
in the generated `config/solarapocalypse.cfg`.

## 1. Time
- Progress is a `long` saved in the overworld's `data/solarapocalypse.dat`; 1 day = 24000 units. `/time set` cannot
  rewind it.
- `clock.mode = SUN` (default) adds the forward movement of the overworld's `worldTime`: a day lasts as long as the sun
  takes, so any day-length mod is followed; sleeping counts; jumps over `clock.maxSunJump` are ignored; it stops while
  `doDaylightCycle` is false. `TICKS` counts server ticks (`clock.ticksPerDay` per day).
- `clock.progressWhileEmpty = false` pauses while nobody is online; `true` keeps counting and terrain catches up on load.

## 2. Phases and depth
- Phase 0 (`phases.safeDays`) is quiet; phases 1..`phases.count` follow, each `days` long (0 = `phases.baseDays` x
  `phases.scaling`).
- `depth`: layers destroyed by the end of the phase, counted down from the column's **reference** (layer 1 = the
  reference block), or `infinite`. Never decreases; once infinite, every later phase is infinite at the last positive
  `layersPerDay`.
- `world.depthReference`:
  - `SURFACE` (default): each column's own terrain surface, so layers follow the land like a 3D printer's layers in
    reverse. In CubicWorldGen worlds it is computed from the generator (`cc/CwgSurface`); elsewhere it is recorded the
    first time the column's top is known (the ground under trees and water) and saved with the column
    (`SurfaceRecord`). Columns whose surface is not known yet wait.
  - `TOP_Y`: `world.topY` for every column (`auto` = the height the generator reports).
- `speed`: `PHASE` spreads the descent over the phase, `RATE` goes at `layersPerDay` (the phase lasts until done),
  `INSTANT` at the phase start.
- Everything is a pure function of progress (`Timeline`), so terrain loaded on day 40 gets what terrain watched since
  day 0 got.

## 3. Block effects
Per block, using the rules active in the running phase (`phases.ruleMode`: `CARRY` = every phase so far, the latest
phase's rule winning per block; `ISOLATED` = the running phase only):
1. **Destroy** (`destroy` selectors): removed once the depth reaches the block. Blocks reached while the rule's phase
   runs go layer by layer as the depth passes; blocks above the reference (trees, buildings) go top down over the first
   tenth of the phase; other blocks reached before the phase go at random but fixed moments within it.
2. **Convert** (`selector -> target`, target may be `air`): only the **surface layer**, the top `convertDepth` layers
   (default 1) of the column's current surface. The surface is the topmost opaque block, raised over blocking blocks and
   liquids stacked on it (glass, lava); plants, snow layers and the like on it belong to layer 1. Conversions start once
   the phase's destruction is done and are spread at random but fixed moments over the rest of the phase (`INSTANT`: at
   once; infinite phases: from the start). Converting to air makes the block below the new surface, so leaves burn
   through a canopy while grass -> dirt stops at the top. A chain of rules runs to its end.
3. **Evaporation**: sun-exposed liquids go from `evaporation.waterPhase` / `lavaPhase` / `otherLiquidsPhase` on;
   `INSTANT` clears whole bodies, `LAYERS` lowers a level from `evaporation.topY` (auto: sea level, or the CubicWorldGen
   preset's water level) at `layersPerDay`. With `blocks.blockPhysics`, liquids cannot form new sources once their
   evaporation has started.

- Selectors: `modid:name`, `modid:name:meta`, `modid:*`, `#oreDictName`, `material:<name>`, `*`. Wildcards skip
  unbreakable blocks. Within a phase the first matching rule wins.
- Changes use `setBlockState` flag 2|16 (clients told, neighbours not: nothing flows or falls); `blocks.blockPhysics`
  switches to flag 3. No item drops or container spills unless `blocks.dropItems`.
- Defaults: 5 phases of 3 days after 3 safe days. 1: grass, mycelium, farmland, paths to dirt; plants, vines, cacti,
  snow, ice burn; water and lava evaporate; mobs in the sun catch fire. 2: leaves, gourds, cobwebs burn; 1 damage.
  3: wood, wool, carpets, TNT burn; dirt to sand; 2 damage, 0.5 heat. 4: clay hardens, gravel to sand; 4 damage, 1 heat.
  5: everything erodes, infinite, 16 layers a day; 8 damage, 2 heat.

## 4. Engine (Cubic Chunks worlds)
- Cubes are queued on load (new and reloaded: the retroactive part), on phase starts, on `/solar` time changes and
  reloads, and when their earliest pending change falls due (checked every 20 ticks, at most one look per cube per
  1/20 day). Queued cubes are processed once ready (populated, lit, surface-tracked), top-down per x/z.
- Time budget per server tick: at most `performance.tickBudgetMs` (10 ms) and at most `performance.freeTickShare` (75 %)
  of the time the rest of the server leaves free in a 50 ms tick (100-tick average), at least 0.5 ms. A busy server slows
  the apocalypse instead of lagging.
- Every config load logs the phase plan (days, depth, rule and block-state counts) and warns about decreasing depths.
- Fewer than 256 x/z per column change per tick (CC's client heightmap packet counts them in a byte).
- Nothing loads or generates a cube.

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
- Direct: the zombie rule (sky visible from the eyes, by day if `entities.sunNeedsDaytime`): `sunDamage`, `sunFireSeconds`.
- Background heat: sky light at the eyes at least `entities.backgroundMinSkyLight` (1), day or night: `backgroundDamage`,
  `backgroundFireSeconds`. Overhangs, trees and houses with openings are no refuge; sealed rooms and caves are. Both
  add up in the sun.
- Damage source `solarapocalypse.sun` bypasses armour and is not fire damage; Fire Resistance protects as
  `entities.fireResistance` says. Skipped: creative/spectator players, armour stands, fire-immune mobs
  (`entities.spareFireImmune`), `entities.blacklist`.

## 7. Checks
- `./gradlew test`: `TimelineTest`.
- `bash scripts/probe_server.sh <tag> selftest`: fresh default-preset world, default config. Last run (2026-10-07):
  CWG model exact for 96.9-98.6 % of positions depending on the seed (the rest lower, from caves and lakes; at most a
  few blocks higher); phase 1 catch-up of ~10.7k cubes: 0.3-1.7 M blocks (mostly water) in 120-145 ticks, 0.8-1.1 s
  engine time; pigs in phase 4: sun 10 -> 5, under a roof 10 -> 9 (heat), 24 deep untouched; 8 layers of erosion below
  each column's surface: 0 of 483252 positions above the line still hold a block.
- `./gradlew runServer -Pno_dev_mods`: runs without Cubic Chunks.
