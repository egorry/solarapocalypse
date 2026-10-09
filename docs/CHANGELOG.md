# Changelog

Follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
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
- Sleeping (or skipping time) where an infinite phase's erosion passes kills the player.
- Blocks players place change on the engine's next pass, instead of on their cube's next scheduled look.
- Red vitrified sand from red sand (drops red sand).
- Depth-staged conversions: `depth=3` or `depth=2-3` on a convert rule limits it to those layers below the surface, so
  blocks step through stages as erosion lowers the surface (grass, path, dirt, gravel, sand, glass layer by layer).
### Fixed
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
