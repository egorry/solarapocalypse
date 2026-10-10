# Changelog

Follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- `layer=2` / `layer=2-4` on a convert rule: it acts only in those layers below the surface, so a whole gradient of
  stages can run ahead of infinite erosion (`depth=3` still means the top 3 layers).
- Thrown water bottles (splash and lingering) put out solar fire, as they do vanilla fire.
- `phase_n.weather`: `UNCHANGED`, `NONE`, `RAIN`, `THUNDER` or `INHERIT` (default; phase 1 `UNCHANGED`), held while the
  phase runs.
- `/solar status` shows how far behind the engine is when it cannot keep up.
- Phase sections above `phases.count` get a note saying they are not used (they are kept, never recreated).
- Configurable phases (`config/solarapocalypse.cfg`): any number, lengths per phase or scaled, a safe phase 0.
- Apocalypse clock that follows the sun (works with day-length mods) or counts ticks; optional pause while the server
  is empty; nothing rewinds it.
- Cubic Chunks worlds: erosion with a per-phase depth (or infinite), conversions of the surface layer, evaporation of
  water, lava and modded liquids; terrain loaded later catches up.
- Depth counted from each column's own surface (computed from CubicWorldGen's generator, or recorded) or from a fixed Y.
- Rule modes: rules of earlier phases carry over (latest wins) or each phase stands alone.
- Sun damage and fire for mobs in direct sunlight, background heat wherever sky light reaches, optional Fire Resistance
  immunity.
- `/solar` (alias of `/solarapocalypse`): status, set, add, phase, pause, resume, reload.
- Block changes use a share of the free tick time, so a busy server slows the apocalypse instead of lagging.
- Phase length is a minimum: destruction runs first, then conversions, and the next phase waits for both.
- Solar fire: the sun sets a share of the surface alight after each phase's conversions (and on every layer of infinite
  erosion); it looks, sounds and burns like fire but never spreads. Flammable blocks get ordinary fire. The mod is now
  needed on clients too.
- Fire-immune mobs burn too, and direct sun burns at night as well, by default.
- Phase announcements, all optional per phase: a chat message, a sound, and a splash title on screen with the message
  underneath (own font and flickering fire colours). `/solar announce` previews them.
- Selectors can be combined with commas and excluded with `!` (`material:rock, !minecraft:cobblestone`); conversion
  rules that loop are cut, with a warning.
- No vanilla fire on TNT (`blocks.vanillaFireBlacklist`) or while the gamerule `doFireTick` is false.
- At most 512 block changes per tick by default (`performance.maxBlockChangesPerTick`), so clients are not flooded.
- `entities.sunAtNightFromPhase`: with `sunNeedsDaytime`, the sun burns at night too from this phase on (default 7).
- `/solar status` shows the apocalypse's tick time and block changes; `/solar fire` counts fire around you.
- Conversion chances: `minecraft:dirt -> minecraft:gravel @ 70%`. Which blocks convert is fixed per block.
- No solar fire next to flammable blocks, so vanilla fire can spread to them.
- Phase 1 no longer sets mobs on fire by default.
- Per-phase `depthReference`: later phases can erode flat from a fixed Y (starting at `world.topY`).
- Per-phase `evaporate` lists replace `evaporation.waterPhase` / `lavaPhase` / `otherLiquidsPhase`; new selectors
  `fluid:<name>` and `temperature<K` (and `<=`, `>`, `>=`).
- `phases.ruleMode` is split into `convertRuleMode` and `destroyRuleMode`.
- Phases are saved in number order in the config (phase_2 before phase_10).
- New defaults: eleven phases over 22 days, from paths and burning grass to infinite erosion; water evaporates from
  phase 3, lava from phase 6; the sun burns by day only until phase 7; Fire Resistance protects from the sun.
- Time skips count: sleeping, `/time add`, and `/time set` (as the skip forward to that time of day).
- Block properties in selectors and targets (`minecraft:anvil[damage=0] -> minecraft:anvil[damage=1]`), and the
  `preserveState` modifier, which keeps the block's facing and the like; modifiers stack
  (`minecraft:stone_brick_stairs -> minecraft:stone_stairs @ 30% preserveState`).
- What a changed block can no longer hold (plants and crops on new paths, torches, the top half of tall plants and
  doors) pops off at once and drops nothing unless `blocks.dropItems`; tall plants and doors convert as a whole.
- No solar fire diagonally next to flammable blocks either (next to ice and snow is fine: they melt as in vanilla).
- Setting the time back by less than 1000 in the time of day (e.g. 23600 to 23500) no longer counts as a day skipped.
- Phase 2 no longer burns 30 % of wool and carpets by default (phase 3 burns all of it).
- `blocks.nightDousesFire` (default false): night puts the sun's fire out, spot by spot over the sunset, and lights the
  same spots again over the sunrise.
- Infinite phases look at each cube every layer instead of every 1/20 day, so erosion and its fire move layer by layer.
- Vitrified sand: sand melted in place by the sun (phase 5 by default, instead of glass); breaks back into sand.
- The config file is tidied on every load: sections in number order, keys from older versions dropped.
- Blocks hanging on the side of a changed block (wall torches, ladders) pop off quietly too.
- A plant a rule makes where vanilla cannot keep it (a dead bush on grass) is removed at once, instead of popping off
  later and dropping sticks.
- `dimensions=` on convert, destroy and evaporate entries limits them to some dimensions.
- Placeholder phase announcements by default ("Phase 1", thunder, "Phase One"), for testing.
- A skip across several phase starts announces each phase in turn; players who join later get the running phase's
  announcement.
- `/time add` counts in full (no `maxSunJump` any more); after `/time set` or `/time add` the engine catches up faster
  for 30 s (`performance.skip*`); sleeping keeps the normal budget.
- Skipping time with `/time` where an infinite phase's erosion passes kills the player.
- Blocks players place change in the engine's next round, instead of on their cube's next scheduled look.
- Red vitrified sand from red sand (drops red sand).
- Depth-staged conversions: `depth=3` or `depth=2-3` on a convert rule limits it to those layers below the surface, so
  blocks step through stages as erosion lowers the surface (grass, path, dirt, gravel, sand, glass layer by layer).
### Changed
- Erosion the engine cannot keep up with comes down evenly over the whole loaded world, an onion layer at a time:
  one clock per world, which moves a layer once every cube has done the one before. The clock is saved, so a restart
  carries on where the engine was; `/solar reload` keeps it (it used to bring everything to the present).
- Fire in infinite phases is no longer redrawn on every layer: each surface block rolls once, so fire on ground the
  line has not reached yet stays as it is, and a TOP_Y line still in the sky costs nothing.
- Phase 11 of the defaults has the gradient running ahead of the erosion (`convertDepth` 6): layer 6 grass to path,
  5 dirt, 4 gravel, 3 sand, 2 vitrified sand.
- Default fire per phase (solar fire, and vanilla fire on flammables): 0, 5, 10, 25, 30, 40, 50, 60, 70 and 75 %, then
  85 % solar and 100 % vanilla in phase 11.
- Sleeping no longer brings the erosion straight to the present: the engine carries on a layer at a time from where it
  was, so the world stays even and catches up behind the clock. `/time` and `/solar set/add/phase` still jump. Nobody
  dies in their sleep from erosion any more; they fall when it gets there.
- The engine works outwards from the players: each round reaches their surroundings first.
- Carried convert rules act in the running phase's `convertDepth` layers (they kept their own phase's depth), and only
  in the top layer during infinite phases; per block and layer the latest phase with a rule there wins.
- Infinite erosion comes down evenly, a layer at a time, when the engine cannot keep up with `layersPerDay`: it then
  runs slower than set instead of leaving trenches several layers deep.
- Solar fire's steps are silent (vanilla fire sounds like wool when you walk along a ledge over it).
### Fixed
- In the first phase on a depth line (such as an infinite SURFACE phase as phase 1), trees, buildings and the sea above
  the terrain all went at the phase start, a whole cube at a time, holding up the erosion for minutes; they now go top
  down over the first tenth of the phase, as in later phases.
- Solar fire burns what touches it, as vanilla fire does: mobs and players catch fire, dropped items burn up.
- Pits a cube wide cut through the terrain, and caverns under standing ground, while the engine was behind: a cube that
  loaded, or woke for its first layer, went straight to the present instead of its neighbours' layer.
- Trenches and stripes in infinite erosion, and water evaporating in slivers: a cube the time budget cut short waited
  behind every other queued cube.
- When erosion emptied a cube's columns over several ticks, the cube below could miss its look, leaving its
  conversions undone until a later look.
- Crash (`ConcurrentModificationException` in `CubeEngine.run`) when a block change loaded a neighbouring cube.
- A single cube with many slow changes (lighting after trees burn) could overrun the tick budget (one 188 ms tick seen);
  the budget is now checked before every change.
- Solar fire went out when its ground turned into glass or a path; it now stands on anything that blocks movement.
- Block changes never ran on servers whose own tick took longer than the time budget (singleplayer with the default 4 ms
  budget): the budget now counts the apocalypse's own time.
- With several infinite phases at different rates, blocks were removed at the first one's rate.
- Fire was put out and re-rolled at every phase start. `ignitePercent` is now the total alight (25 % then 50 % keeps
  the first 25 %), and fire stays until its ground goes; only infinite phases redraw it on each layer.
