# Research: a Solar Apocalypse for Cubic Chunks (and vanilla) on Forge 1.12.2

Turn 1 research (2026-10-06/07). Question: can a configurable solar apocalypse (evaporation, block conversion, layer-by-layer
erosion, sun damage, all applied retroactively to chunks loaded later) work in Cubic Chunks worlds, where the cubes above a
position may never have been generated? How does CC know what faces the sky?

Targets: CubicChunks 1.12.2-0.0.1271, CubicWorldGen 0.0.152, Forge 14.23.5, the user's `Cubic Chunks` CurseForge instance
(world `CC - Migrated`, LongerDays x3). Sources: CC 0.0.1271 and CWG decompiled (`../SRPCCC/research/decomp/`), MC/Forge
sources (`../SRPCCC/build/rfg/minecraft-src/java`), the pack's jars (javap), prior-art repositories, and a headless probe
on a dev server (section 9). Five research agents read the code; four adversarial verifiers re-checked the code claims
(section 10). Code citations below are shortened: `CC/` = `io/github/opencubicchunks/cubicchunks/` in the CC decomp,
`MC/` = `net/minecraft/` in the MC sources.

## 1. Verdict

**Feasible, with one real limitation that needs a design decision.**

- CC keeps a persistent per-column record of every opaque block it has ever lit, so "can this block see the sky" is an
  O(1) in-memory read that never loads or generates anything. Edits through `World.setBlockState` keep that record,
  lighting and clients consistent, and they are cheap (measured: about 1-7 µs per removed surface block including light).
- CC has **no notion of "unknown above"**: cubes that were never generated count as air. A position whose cubes above
  were never generated can be reported as sky-exposed although real terrain would cover it. In the user's world (surface
  spread over about Y 230-2070, see 3.3) this is the normal case for a player deep underground, not an edge case.
  It is solvable: CWG's terrain is a pure function of (x, z) in the user's preset, so the true surface can be computed
  without generating cubes (3.3), and there are cheaper conservative fallbacks (3.4).
- Retroactive application has good hooks: per-cube readiness flags, per-cube capabilities saved with the cube, cube load
  and watch events, and an optional hook that edits new cubes before they are lit or sent (zero light cost, no pop-in).
- No world-changing solar apocalypse exists for 1.12.2, and no version of any of them handles tall worlds or applies
  changes retroactively (section 8).

## 2. How Cubic Chunks knows what faces the sky

### 2.1 The heightmap ("opacity index")
- Every server column holds a `ServerHeightMap` (`CC/core/world/ServerHeightMap.java`), created by the `Chunk` constructor
  mixin (`CC/core/asm/mixin/core/common/MixinChunk_Cubes.java:177-194`). `NewServerHeightMap` is an unused stub. Clients
  get a `ClientHeightMap` filled from server packets.
- It is a run-length encoding, per x/z, of "light opacity != 0" for **every block of every cube that was ever
  surface-tracked**, not just the top block. It is saved in the column NBT (`Level.OpacityIndex`) and is never pruned when
  cubes unload. Removing the top block finds the next opaque block below from this encoding, without loading the cube.
- A cube is surface-tracked (`ICube.isSurfaceTracked()`) when it reaches `Requirement.LIGHT`: generate, populate, first
  light, then `Cube.trackSurface()` (`CC/core/server/CubeProviderServer.java:300-352`). Every cube a player watches is
  eventually brought to LIGHT. Cubes generated only as population neighbours stay untracked; while loaded they sit in a volatile
  `StagingHeightMap` that forgets them when they unload.
- Two "tops" with different meanings:
  - index only (tracked cubes): `getOpacityIndex().getTopBlockY(lx, lz)`, `Chunk.heightMap[]`, `Chunk.canSeeSky` /
    `World.canSeeSky` (vanilla `y >= heightMap[]`, not mixed in by CC);
  - index + staging (also loaded untracked cubes): `Chunk.getHeightValue()` / `IColumn.getHeightValue` (= top + 1),
    `World.getHeight`, `getPrecipitationHeight`, and CC's light engine.
  The second is the better "shaded" test: the top generated layer above a lit band is usually staged, so the index-only
  test calls positions exposed about one cube lower than the light engine does. They disagree while untracked cubes are
  loaded (reproduced, 9.2). Neither counts unloaded cubes that were never tracked.
- Pure reads, never load or generate a cube: `IHeightMap.*`, `Chunk.getHeightValue/getHeight/canSeeSky/
  getPrecipitationHeight`, `World.canSeeSky/getPrecipitationHeight/getHeight` (the `World.*` ones may load the column,
  which only holds biomes and the heightmap). Verified: 1000 calls of each, 0 cube loads (9.1).
- **Generate cubes on a CC server** (never call on positions that might be unloaded): `World.getBlockState`,
  `World.getLight`, `World.getLightFromNeighbors`, `World.canBlockSeeSky` (below sea level it scans `getBlockState` down
  from Y 62; probe: 1-2 cube loads per call), `World.getTopSolidOrLiquidBlock`, `Chunk.getBlockState`,
  `Chunk.getLightFor`, `IColumn.getCube`, `ICubeProvider.getCube`, and `setBlockState` with flag 1 or without flag 16
  (neighbour and observer notifications read neighbours). Non-loading: `isBlockLoaded`, `getLoadedCube`,
  `getLoadedColumn`, `ICube.getBlockState`.

### 2.2 What blocks the sun (opacity != 0)
| Counts as cover | Does not count (sun passes) |
|---|---|
| Full blocks, slabs, stairs, farmland, grass path (255) | Glass, stained glass, panes |
| Leaves, cobweb (1) | Lava (opacity 0: a lava surface reads as exposed) |
| Water, ice, frosted ice (3) | Snow layer, carpet, fences, torches, plants, rails, anvil |

Vanilla uses the same rule (`Chunk.heightMap`, `MC/world/chunk/Chunk.java:237-260,823-829`). Consequences: glass houses do
not protect; leaves, water and ice protect fully (the heightmap is binary); plants, torches and snow layers sit *above*
the top and count as exposed. Different rules (glass protects, leaves do not) need our own walk over occluders:
`getTopBlockYBelow` enumerates opaque Ys without loading cubes (server only), and block types can be read where the cube is
loaded.

### 2.3 Sky light is not a usable signal
- With up-to-date light, sky light 15 is exactly "above the heightmap": it adds nothing to `canSeeSky` for the binary
  test.
- `World.getLightFor(SKY, pos)` returns 15 for **unloaded** cubes without loading them (verified by the probe).
- CC's light also leaks in the unknown-above case: a cube's sky-light source is "y above max(index, staging)", so
  never-generated cubes above count as open sky (`CC/core/lighting/phosphor/PhosphorLightEngine.java:289-292`).
- Light is useful only as an optional graded-shade factor for entities (14-13 near openings and under leaves), read after
  `isBlockLoaded`.
- The pack's `cubicchunks.cfg` keys `fastSimplifiedSkyLight`, `replaceLightRecheck` and `updateKnownBrokenLightingOnLoad`
  are not read by CC 0.0.1271 (dead keys). `relightChecksPerTickPerColumn` is live (1 relight check per column per tick).

## 3. The unknown-above problem

### 3.1 Mechanism
A player who digs or teleports to Y -800 in a fresh column loads cubes -62..-38 (vertical distance 12). The population
requirement generates one cube above the range (CWG boxes: `C+(-1..1)^3` generated, `C+(-1..0)^3` populated); populators
that write further up (trees from a surface near the cube top, structures, other mods' populators) can generate a few
more, untracked and staged. Nothing above that exists. The column's index knows only the tracked range, so any air with no tracked opaque block above
it (a cave or tunnel reaching the top 1-2 cube layers of the range, a cave taller than the range, a player high in the
sky) reports `canSeeSky == true`. CC's own first light gives it sky light too. When the cubes above are generated and
tracked later, the index learns the real cover and the verdict becomes correct; CC then relights older cubes on their
next load from the heightmap saved with them (`LastHeightMap`, `CC/core/lighting/LightingManager.java:159-194`).

Reproduced on a real server (9.2): a shaft carved at the top of a deep tracked range reported `canSeeSky == true` while
the loaded (generated, untracked) cube right above it held stone. After the surface cubes were generated, the same
position reported shaded, and that knowledge persists.

A "shaded" verdict (y <= known top) is always trustworthy. An "exposed" verdict is trustworthy only if every cube between
the position and the highest possible terrain is known. Acting on a false "exposed" is irreversible (erosion, conversion)
or unfair (damage deep underground), so the mod must not trust CC's verdict alone.

### 3.2 Also: never-generated gaps are recorded as transparent
If a column has two tracked ranges (the surface and a deep mine), the gap between them is encoded as air. Erosion that
strips the surface range would make the top fall straight through the gap to the deep range. **Erosion depth must be
measured from a stable reference** (the original surface or the computed terrain surface), never by chasing the falling
heightmap top. This also defines what an "infinite" penetration depth can mean (6.4).

### 3.3 The user's world: the surface can be computed exactly
CWG has no surface-height API, but its base terrain is analytic (`CustomTerrainGenerator.java:119-174`): a density
`D = (lerp(sel, low, high) + depth2d) * vol + h - y` evaluated on a 4x8x4 grid and interpolated; a block is terrain iff
D > 0. Replacers never add blocks where D <= 0 except the ocean fill below the water level; caves and ravines only remove.

The `CC - Migrated` preset (`saves/CC - Migrated/data/cubicgen/custom_generator_settings.json`) has every noise
Y-frequency at 0 and a constant volatility of 512, so **D = A(x, z) - y: the terrain is an exact heightfield.** The top
terrain block is `ceil(bilinear A) - 1`, with A read from CWG's private `terrainBuilder` at the four surrounding 4-block
grid corners (reflection, server thread only). Other numbers from that preset: height offset 1536, oceans of **lava** up
to Y 850 (`water_level 851`, `ocean_block minecraft:lava`), no cubeAreas. Surface span: Y -2199..4458 if the noise
reached +-1 (the code does not bound it tighter), about Y 230..2070 with the noise range measured from CWG's own noise
classes (|n| <= 0.42; 20M samples). `actualHeight 2049.8` is only a GUI heuristic, not a bound. Ravines in this preset
replace rock with flowing lava below a floor near Y 1300-1500, so generated lava can sit open at the surface.

This gives a per-column "true surface" without generating anything. Above it there can only be population features
(trees, huge mushrooms, lakes, mod structures, a margin of about +32), and below it only carved caves. Combined with the
index it removes the unknown-above problem for terrain cover. Limits: it is CWG-version-specific reflection; the default
CWG preset (and others with Y frequencies) has 3D noise with overhangs, where only an upper bound exists; other generators
need a fallback. To be validated against real generation (proposed test in 11).

### 3.4 Fallbacks (any world type)
A three-state predicate, server-side only, evaluated for positions in loaded, surface-tracked cubes:
1. Column not loaded (`getLoadedChunk`) -> UNKNOWN (do not load it).
2. `y <= chunk.getHeightValue(lx, lz) - 1` -> SHADED (vanilla API, same meaning in vanilla and CC).
3. Vanilla world, or CC with the cubes from `cubeY(y)+1` up to a sky ceiling all known -> EXPOSED.
   "Known" = loaded or `ICubeProviderServer.isCubeGenerated` (a non-loading region-header lookup; monotonic, so cache the
   result per column). The ceiling is the computed surface (3.3) where available, else a configured Y.
4. Otherwise UNKNOWN: entities treat it as shade (only the "regardless of sunlight" damage applies); blocks are deferred
   (the cube keeps its old stamp and is caught up when the column above becomes known).

Optional `sunFloor` (positions below Y n are always shaded) bounds every scan and matches "hide deep underground". The
pack's ccpregen (configured with `requirement=LIGHT`) tracks everything it pregenerates into the index, so pregenerating
a surface band is an existing way to make the cover above deep areas known. Do not force-generate the cubes above deep
players by default: about 441 columns x 50 cubes each, every cube generated,
populated and lit (estimate: tens of seconds of CPU plus disk). If offered, only as a throttled background LIGHT queue.

## 4. Changing blocks cheaply

### 4.1 Measured (headless dev server, 9.3)
| Operation (one 16x16 column, flag 18) | Edits | Light flush |
|---|---|---|
| grass -> dirt on 253-254 top blocks (same opacity) | 0.11-0.12 ms | 0.004-0.013 ms |
| remove the top block of all 256 x/z (one layer) | 0.19-1.6 ms | 0.04-0.18 ms |
| the same, when the layer opens a 16x16x8 cave below | 0.21-0.91 ms | 0.15-0.76 ms |
| remove one layer of water (256 blocks) | 0.12-1.6 ms | 0.19-0.25 ms |

No cube was loaded or generated by any of these edits. That is roughly 1-7 µs per removed block, so a 2 ms per-tick
budget handles on the order of 300-2000 surface changes per tick on the server. Not measured: client packets and render
rebuilds (a headless server has no watchers).

### 4.2 Rules for edits
- Use `world.setBlockState(pos, state, 18)` (2 = sync to clients, 16 = no observer updates; no flag 1 = no neighbour
  updates). This keeps CC's index, sky light and client heightmap consistent and skips neighbour and observer
  notifications, so it cannot start water flow. No flag skips `breakBlock`/`onBlockAdded` of the blocks themselves, and
  some of those read neighbours unguarded: placing a liquid (`checkForMixing`) or a falling block (its scheduled tick
  reads the block below) at a cube boundary can still generate the neighbour cube. Flag 18 is generation-safe for inert
  targets (air, dirt, stone, glass...). `Chunk.setBlockState` on an unloaded cube generates it: check `getLoadedCube`.
- Same-opacity conversions (grass -> dirt) queue no light work at all.
- CC queues light work globally and processes it in full (no time budget) at the end of each world tick, on any server
  light read, before cube sends, unloads and saves. Never read light inside the edit loop (each read flushes).
- 64 or more changes in one cube in one tick make CC resend the whole cube (about 6-9 KB per watching player) and
  re-fire `CubeWatchEvent`. Either stay under 64 per cube per tick or do a cube's step at once on purpose.
- Client heightmap bugs in CC 0.0.1271 (also at CC HEAD): `PacketHeightMapUpdate` writes its entry count as a byte, so
  when all 256 x/z of a column change in one tick the count wraps to 0 and the client drops the update; and
  `PacketCubeBlockChange` computes the heights at (x, x) instead of (x, z), relying on that update to correct it. A
  whole 16x16 layer in one tick therefore leaves clients with a wrong heightmap (rain, client relight) unless the cube is
  fully resent. Rule: fewer than 256 distinct x/z per column per tick, or whole-cube resends of non-empty cubes.
- Removing leaves runs `BlockLeaves.breakBlock` (marks 26 neighbours for decay, which later drop items); logs mark 9x9x9.
  The pack's FastLeafDecay only reacts to flag-1 updates. Removing leaves without the decay cascade needs a direct storage
  write; or the decay is accepted as part of the theme.
- Converting into sand or gravel spawns falling-block entities if unsupported. Placing fire blocks spreads fire (lag).
- Removing logs with any flag marks nearby leaves for decay; the first decaying leaf uses flag 3, and FastLeafDecay then
  collapses the canopy with drops. To keep trees from dropping items, the mod removes the leaves itself.
- In vanilla worlds a top drop of N blocks runs about 6N+1 sky light checks (`relightBlock`; cost not measured). Bulk or
  instant modes there can write `ExtendedBlockStorage` directly, then `generateSkylightMap()` (it does not fix sideways
  light into caves), `markDirty()`, tile entity cleanup and a partial `SPacketChunkData` (never a 65535 mask: the client
  replaces its whole chunk). Not needed in CC.

### 4.3 Water (and lava)
- Still water only starts flowing on a neighbour notification (`BlockStaticLiquid.neighborChanged`). Removing water with
  flag 18 causes no flow cascade, so evaporation can go layer by layer or all at once.
- Fluid physics creates new sources through `BlockEvent.CreateFluidSourceEvent` (vanilla and Forge
  `BlockFluidClassic`). The pack's ProperFiniteWater answers every such event at NORMAL priority (water: ALLOW for big
  bodies, else DENY; every other fluid: DENY): to stop regrowth, deny at `EventPriority.LOWEST` during evaporating phases.
- Sources also appear without that event: breaking ice over a solid or liquid block, ice melting, buckets, machines.
  Ice must be converted directly (to air or water-less blocks), never broken or melted. Flowing water that already has a
  scheduled tick (springs, rivers) keeps flowing into cleared space until it is removed too.
- Instant evaporation of a huge body (an ocean, or the user's lava seas reaching Y 850) is a lot of blocks: it must go
  through the same budgeted queue; "instant" can mean "on the cube's first processing" rather than "in one tick".
- Lava has opacity 0 and does not count as cover; evaporating lava (DESIGN.md) is technically the same as water.
- Modded fluids: `IFluidBlock`/`BlockFluidBase`; the pack has Thermal, BuildCraft (oil lakes), IC2C and SimpleDifficulty
  (purified water) fluids.

## 5. Retroactive application (new and reloaded terrain)

### 5.1 Hooks and timing (CC)
- `CubeEvent.Load` fires when the cube is in the column, but for a **new** cube before population, first light and
  surface tracking (probe: 63 of 63 fresh loads had populated/lit/tracked = false), and it can be nested inside CC
  generation. Handlers must only enqueue.
- A cube is ready when `isFullyPopulated() && isInitialLightingDone() && isSurfaceTracked()`, the same test CC uses
  before sending it (`CC/core/server/CubeWatcher.java:166-168`). `CubeWatchEvent` fires only for ready cubes, per player,
  again on re-entry into range and on full resends.
- Per-cube state: `AttachCapabilitiesEvent<ICube>` capabilities are saved in the cube NBT (`ForgeCaps`); `CubeDataEvent`
  gives the raw cube NBT. Attach/deserialize can run on CC's cube I/O thread, so keep them trivial. A capability-only
  change needs `world.markChunkDirty(pos, null)` (CC redirects it to the cube) or it is lost.
- Cubes unload only through CC's ChunkGc; there is no `CubeEvent.Unload` at server stop.
- `CubeProviderServer.calculateDiffuseSkylight(Cube)` (HEAD) runs after full population and before first light. Edits via
  `ICube.setBlockState` there queue no light work and no packets, and the client never sees the unprocessed cube. It needs
  a CC-internal mixin (applied only when CC is present, like SolsticeCCPatches' connector). Optional optimisation; the
  normal path (queue -> process when ready) works without it, with brief pop-in at the view edge.
- CC random-ticks only cubes within 128 blocks (3D) of a player plus forced cubes; Forge 1.12.2 has no random-tick event.
  The apocalypse needs its own scheduler.

### 5.2 Architecture this points to
- Global clock (section 7) -> target state (phase, layer step).
- Each cube (CC) or chunk (vanilla) carries a tiny stamp capability: the progress it has been brought to.
- Load, watch and phase-change events only enqueue positions whose stamp is behind. A `WorldTickEvent` END handler
  processes the queue under a nanosecond budget, nearest to players first, top-down, ready cubes only, and never touches an
  unloaded position.
- Processing is "bring this unit from its stamp to the target", shared by live progression and catch-up. Layer removal is
  not idempotent, so the stamp must be advanced (and the cube marked dirty) in the same step as the edits.
- Population of a neighbour or a tall tree can add blocks into an already processed cube: a slow re-check sweep over
  player-near cubes catches these.
- World pregeneration (the pack has ccpregen) floods load events: enqueue cheaply, dedup by stamp.

### 5.3 Vanilla worlds and which worlds are cubic
- CC decides per world, and exclusions are not retroactive (`forceDimensionExcludes=false`): in the save `CC` the Nether
  and End are vanilla, but the test saves `Nether Test` and `Nether Copy of CC` have a cubic Nether, and non-excluded
  modded dimensions are cubic (Compact Machines' DIM144 in `CC` and `CCIC2TF`). So: decide per `World` with
  `((ICubicWorld) world).isCubicWorld()` (with CC installed every `World` implements `ICubicWorld`), never by dimension
  id, and give the apocalypse a dimension whitelist. The vanilla path is needed even in the CC pack.
- Vanilla: chunk capabilities (`AttachCapabilitiesEvent<Chunk>`, saved as `ForgeCaps`); `ChunkEvent.Load` fires before
  population; `canSeeSky` is exact because a chunk always has its full height. `chunk.isPopulated()` alone is not a
  readiness gate: it needs the chunk to have ticked (player-watched or forced), and the -x/-z neighbours' population can
  still drop trees, lakes or ice into an already processed chunk, so their stamps must be re-checked after
  `PopulateChunkEvent.Post`.

## 6. Notes on the user's DESIGN.md
- **Dynamic number of phases**: possible. Forge `@Config` supports arrays; a list or a per-phase section count defines the
  phases, or a JSON file in `config/solarapocalypse/`. No hardcoded maximum is needed.
- **Block rules** `mod:block:meta` and groups: blocks can be matched by registry name and metadata (`getStateFromMeta`),
  by `Material`, by type (`Block.isLeaves`, `IPlantable`, `IGrowable`, `BlockBush`, `IFluidBlock`), and by OreDictionary
  names through the block's item. A fallback rule for unmatched (modded) blocks can key on material or "any opaque block".
- **Destroy "all blocks" with "infinite" depth**: in CC that means erosion with no lower bound. It must be measured from a
  stable reference (3.2), can only act on loaded cubes, and each newly exposed layer has to wait for the cubes above it to
  be known. In the user's world the terrain is up to thousands of blocks thick, so "infinite" erodes until the sun reaches
  the lava sea or the void, at the configured speed.
- **Percentage of exposed blocks ignited, deterministic**: hash (x, y, z, phase) against the percentage. Fire spreads
  and lags in bulk; placing fire only on the processed cube's top layer and capping it per tick is advisable. With
  `doFireTick=false` placed fire never burns out, and fire whose tick finds unloaded blocks within 2 stops ticking.
- **Duration extended when destruction is slower than the phase**: the scheduler knows its backlog (queued layers x
  rate), so it can hold the clock or extend the phase.
- **Damage "regardless of sunlight but above ground"**: "above ground" needs a definition, e.g. within N blocks of the
  computed surface (3.3), or above a configured Y, or sky light > 0.
- **Simple Difficulty integration**: SimpleDifficulty 0.3.9 has a public `TemperatureRegistry.registerModifier(
  ITemperatureModifier)` (player and world influence); same soft-dependency isolation as CC.
- Damage sources: a fire-typed source is fully blocked by Fire Resistance (by design or not); creative/spectator players
  are protected unless `setDamageAllowedInCreativeMode`; the pack's nodami removes mob invulnerability frames, so the
  damage interval sets the damage rate directly. The sun test for entities: `canSeeSky` at the eye position, three-state
  as in 3.4, every 20 ticks staggered by entity id.

## 7. The apocalypse clock (day-length mods)
- LongerDays 1.0.4 (pack: Longer Days=true, multiplier 3): at `WorldTickEvent` START in dimension 0 it sets
  `worldTime - 1` whenever `totalWorldTime % 3 != 0`. A day stays 24000 `worldTime` units but lasts 72000 server ticks.
  With `doDaylightCycle=false` it makes time run **backwards** (2 units per 3 ticks, possibly negative).
- `worldTime` is shared by all dimensions (`DerivedWorldInfo`), jumps on sleep, and `/time set day` sets it to 1000
  (day 0). `totalWorldTime` cannot be changed by commands and counts real ticks.
- Every 1.12.2 day-length mod checked (LongerDays, Extended Days, Time Control, Morpheus) keeps 24000 `worldTime` units per
  sun cycle; Time Control even turns `doDaylightCycle` off and drives `worldTime` itself.
- Recommended: the mod's own `long progress` in overworld `WorldSavedData` (`getMapStorage()`), advanced in
  `ServerTickEvent` END. Default mode SUN: add the positive change of overworld `worldTime` per tick, ignore negative
  changes and single-tick jumps above a cap (default 24000). This follows any day-length mod with no extra setting, counts
  sleep as passing time, and cannot be rewound by `/time set`. Alternative mode TICKS (+1 per tick, configurable ticks per
  day) for permanent-day servers. Phase thresholds in days, converted once to `long`. Commands to query/set/add/pause.
  Send progress to clients in a small packet (client `worldTime` is wrong between syncs under LongerDays). Never use CC's
  `inhabitedTime`.
- Prior art almost always uses `floor(worldTime / 24000)`, so `/time set` resets or changes their phase.

## 8. Prior art
- 1.12.2: nothing that changes the world; only entity sunburn mods (Sun-Burn, Burn in the Sun, AngrySun). A 2018-2019
  forum request for a 1.12.2 port went unanswered.
- History: Splated's Bukkit plugin (to 2015), the Forge 1.6.4 "Solar Apocalypse" (Cephrus, 2014; grass -> dirt, plants
  and leaves gone, water evaporates, burning), Elite Armageddon (1.7.10/1.8; random top blocks within 16 blocks of each
  player).
- Modern: Refabricated (Fabric 1.20-26.x; random-tick piggyback in ticking chunks; "heat layers" = a Y threshold above
  which entities burn, dropping with days; issue #14 complains of an untouched zone around the player), Reforged
  (Forge 1.16/1.18; per-block random-tick mixins), Reborn (Forge 1.20.1; budgeted random columns near players; author
  warns fires and evaporation are heavy), Plus, Apocalypse: Countdown ("effects only occur in loaded chunks"), Burning
  Horizon (9 phases), and a few others.
- All of them: vanilla heightmap or `canSeeSky`, only chunks near players, no retroactive processing, no tall-world or
  Cubic Chunks support. Solar Apocalypse's chunk/cube stamps and queue would be new.

## 9. Empirical checks (headless dev server, CC 0.0.1271 + CWG 0.0.152, default CustomCubic preset)
`bash scripts/probe_server.sh <tag>` runs `debug/CubicProbe` (`-Dsolarapocalypse.probe`, fresh world each run, the server
stops itself; `SOLAR PROBE` lines in `research/probe_latest_<tag>.log`).

1. Deep-only column (cubes -52..-48 to LIGHT at block 640000): 63 cube loads, every fresh cube had populated/lit/tracked
   false at `CubeEvent.Load`. The highest generated cube was -47 (one above, the population neighbour); the surface cube
   was not generated. 1000 x `canSeeSky`/`getPrecipitationHeight`/`getHeight`/`getLightFor`: 0 cube loads;
   `getLightFor(SKY)` at an unloaded Y 4200 = 15. `canBlockSeeSky(-800)`: 1-2 cube loads.
2. Shaft carved at the top of the tracked range (Y -760..-753): `canSeeSky` **true**, index top -761, while the loaded,
   untracked cube above held `minecraft:stone` at -752 (only the staging map knew; `getHeightValue - 1` reported 63, from a
   cube `canBlockSeeSky` had just generated). After generating cubes -47..8 to LIGHT (503 cube loads, 0.5 s), the shaft
   reported shaded: the occlusion is learnt and persists.
3. Edit costs: table in 4.1 (two columns, 12 layers each, a carved cave opened by the 11th layer). 0 cube loads.

## 10. Verification
Four verifiers re-read the code behind every load-bearing claim of the CC heightmap, CC lighting, CC lifecycle/CWG and
vanilla/soft-dependency research (the clock and prior-art topic was not verified separately; LongerDays was decompiled
directly). 72 claims: 58 confirmed, 14 partially confirmed (corrected), none refuted. The corrections are folded into
the sections above: population spill above +1 cube (3.1), the two heightmap meanings (2.1), the client heightmap packet
bugs and flag-18 limits (4.2), ice and ProperFiniteWater (4.3), per-world cubic decisions and vanilla readiness (5.3),
the CWG noise bound (3.3), fire (6). Further implementation pitfalls they found:
- The CC "LastHeightMap" saved with each cube is index-only and coarse; it cannot serve as our "what changed above"
  record: use our own cube capability.
- `CubeDataEvent.Load` can hand over the very NBT object of a pending save (treat it as read-only); capability attach
  and deserialize can run on several cube I/O threads; a cube with no capabilities returns null from `getCapabilities()`.
- `getLoadedColumn` returns the column being loaded, for any coordinates, while a `ChunkDataEvent.Load` handler runs.
- `IColumn.getLoadedCubes()` is a live internal list (loading/unloading while iterating throws
  ConcurrentModificationException); `getLoadedCubes(Integer.MAX_VALUE, ...)` overflows and returns an empty list.
- `getCube(..., GENERATE)` can return the shared `BlankCube` (no events); `GET_CACHED`/`LOAD` can return null.
- Async cube loads complete during the `WorldTickEvent` END of whichever world ticks first, so a world-END processor
  cannot guarantee it sees a cube before it is sent. Only a populator or the pre-first-light mixin avoids pop-in.
- Entity ticking (and `LivingUpdateEvent`) stops 300 ticks after a dimension has no players and no forced chunks.
- Populators registered with `CubeGeneratorsRegistry.register` never run under CC's vanilla-compatibility generator
  (`registerForCompatibilityGenerator` is separate).
- No other light engine (Phosphor, Alfheim...) is in the pack; CC's engine is the only one.

## 11. Open questions
All answered in turns 2-4; the decisions are in [SPEC.md](SPEC.md) and [DESIGN.md](DESIGN.md).
