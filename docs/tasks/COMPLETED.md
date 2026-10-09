# Completed

Action items, newest first. Player-facing details are in [../CHANGELOG.md](../CHANGELOG.md).

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
