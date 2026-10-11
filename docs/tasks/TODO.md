# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- Cubic Chunks version mismatch for `performance.updateBeforeSending` (turn 24): done as a warning in the log and in chat
  to the host or operators, the game running on without the feature. Forge's error screen (as for incompatible mods)
  would instead stop the game from loading, for an optional feature; Claude's view: keep the chat warning [low, user].
- Simple Difficulty default temperatures (turn 23, Claude's values): sun 1, 2, 3, 3, 4, 4, 5, 6, 7, 8, 9 and heat 0, 0,
  1, 1, 2, 2, 3, 4, 5, 6, 7 over phases 1 to 11, added up in the sun (its normal is 11-14, hot 15-19, burning 20-25).
  Change them as you like after testing [low, user].

## Waiting for the user's test (client side: Claude cannot see a client)
- Updated before sending (turn 24, with the turn 23 items): explore new terrain in a later phase (`/solar phase 5`) with
  `performance.updateBeforeSending` on: terrain loading in never changes in view; off: it does, a moment later.
  `/solar status` counts the cubes done before sending. Also: the game starts normally with the mod as a coremod (the
  log names `SolarApocalypseCore`; Forge warns about a coremod jar holding a mod, harmless, CubicWorldGen gets the same).
- The optional items and break effects (turn 23; the user tests them together):
  - Break effects: near a change, vanilla break particles and sounds (a fizz and smoke for liquids), none beyond 16
    blocks, and no client lag when a whole layer goes near you (at most 8 a tick).
  - Simple Difficulty: hotter in the sun and under cover open to the sky as the phases go on; sealed rooms and caves
    normal. Note: in a self-test with Simple Difficulty loaded, the sun pig died faster than without it (its cause not
    looked into; the pig checks assume a pack without it).
  - Burning liquids: a BuildCraft oil pool in the sun in phase 2 or 3 catches fire and burns away.
- Settled sand and gravel (turn 21; the user will test it later, turn 22) in a client: they should look exactly like vanilla sand, red sand and gravel (same
  models, random rotation), with the same break particles and sounds, and pick block giving the vanilla item. Seen
  with F3 as `solarapocalypse:settled_block[type=...]` where conversions made them.

## Will do (Cubic Chunks)
1. Dark spots: why ground counts as under cover in open sky at all on a vanilla-like Cubic Chunks preset (the user,
   turn 19: their single spot, cube -19 5 -13, block 8 0 7, near -296 80 -201, is situational; investigate the cause
   in general instead; turn 20: the world type is Cubic Chunks' default "Custom Cubic" preset, vanilla-like generation
   made Cubic Chunks aware); see also the later investigation of cubes that look covered after `/time add` [low, user].
   Turn 20 investigation (code, CC source and a read-only scan of copies of 5 dev saves): no single cause confirmed.
   Fixed: ground uncovered two or more cubes down (a crown or roof gone) waited for its cube's next look, the next phase
   or day, so it stayed without fire (the missing fire light reads as dark at night). Ruled out: a stale height map
   after the engine's own removals (the scan found no height map top on a clear block). Real cover: the mod and Cubic
   Chunks' sky light take the column's top from the same height map, which also counts cubes not loaded or not sent
   to clients yet, so ground under them is both covered and dark until the engine reaches them. In the saves, the
   user's spot is open and correct in one and under a real oak canopy in the other. Both reports predate the turn 12
   engine clock: recheck on the current code; if a spot shows again, Claude would add a self-test count of open-sky
   ground the engine treats as covered.
   Lead (turn 24, not checked yet): Cubic Chunks marks every x/z of a column's heights whenever it tracks a new cube in
   it (empty air cubes too), so that tick's heightmap packet wraps to 0 and clients drop the column's height changes;
   block changes the engine sent that tick keep the wrong heights their own packet gives (RESEARCH.md). Clients would
   then light those spots as covered. Same lead for the cubes that look covered after `/time add` (below).
2. Done (turn 24): the hook before players get a cube (COMPLETED.md); kept here so item 3 keeps its number.
3. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user; turn 24: the user will bring it up in a couple of turns].
4. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
5. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user]. Turn 19's offset on seed -5479930802988752619 (1,701 positions above the ground, up to 6) was CWG's snow
    populator turning surface water into ice after the model, so the self-test counted it as ground; turn 20 rerun with
    ice not counted: 0.

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Seen again in the turn 12 TOP_Y test: about 25 % of the area dark, without fire. Recheck after the turn 12
  engine clock (cubes no longer run at different times), then investigate after everything else [lowest, user].

## Before a release
- `use_mixins = true` (only for dev runs: Cubic Chunks needs MixinBooter there) makes the template's CurseForge and
  Modrinth uploads list MixinBooter as required (`gradle/scripts/publishing.gradle`). The mod does not need it: drop
  that requirement if those uploads are used (both are off now).

## Later (relies on bottom-up mechanic from above)
- "Lava Apocalypse" preset for an infinite phase coming from below, converting air into lava.
- "Ice Apocalypse" preset mostly the same as normal config, but ice-ified, including SimpleDifficulty support and cold variants of some existing settings (cold damage, background cold damage e.t.c)
