# Completed

Action items, newest first. Player-facing details are in [../CHANGELOG.md](../CHANGELOG.md).

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
