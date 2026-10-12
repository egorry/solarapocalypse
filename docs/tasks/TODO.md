# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- (none)

## Waiting for the user's test (client side: Claude cannot see a client)
Turn 26 retest: the fixes from the user's turn 26 test pass (their results: COMPLETED.md, turn 26). Tick the boxes and
write what you saw into prompt.txt.
- [ ] Your run configuration "1. Run Client (Pcompat_mods)" says `-Pcompact_mods`: make it `-Pcompat_mods` and take the
  Simple Difficulty and BuildCraft jars you copied out of `run/mods` first (with both, Forge finds each mod twice). Or
  keep your copies and the plain run configuration: that works too.
- [ ] Terrain updated before you see it (fix: the hook did run, but a new surface cube's sky was not known yet, so it
  changed nothing): `/solar phase 4`, fly into new terrain: it arrives changed, nothing changes in view as it loads.
  `/solar status` counts the cubes.
- [ ] Break effects (fix: every effect landed on the last block the engine changed in a cube): particles and sound at
  each changing block near you (at most 8 a tick), and the fizz and smoke where water evaporates.
- [ ] Dark spots (fix: fire came late on ground in a cube's top layer, as at your spot 61 47 -397 and the turn 19 one):
  at night every fire spot lit, drained sea floors too.
- [ ] Solar fire steps: walking and jumping through solar fire sounds like stone.
- [ ] Evaporation as whole bodies again (`evaporation.mode` default `INSTANT`): your config keeps `LAYERS`, so set
  `mode=INSTANT` under `evaporation` (or delete the line), then `/solar reload`. In phase 3 seas and lakes go cube by
  cube.
- [ ] The leftover moving water you saw: if some is left with `INSTANT`, the coordinates or a screenshot of one spot
  (F3 on the water) so Claude can tell what it is: a spring in the shade feeding a flow, water under cover, or water
  that flowed in after its cube's pass.
- [ ] Optional, still not tried: `build/libs/solarapocalypse-0.0.1.jar` in a copy of your pack (the coremod outside the
  dev client).

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
   Lead (turn 24): Cubic Chunks marks every x/z of a column's heights whenever it tracks a new cube in it (empty air
   cubes too), so that tick's heightmap packet wraps to 0 and clients drop the column's height changes; block changes
   sent that tick keep the wrong heights their own packet gives, (x, x) for (x, z) (RESEARCH.md). Clients would then
   light those spots as covered. Same lead for the cubes that look covered after `/time add` (below). Turn 25: guarded
   (the engine makes no change in such a column that tick; with the Cubic Chunks patch only).
   Turn 26: the user's spot (61 47 -397, a drained sea floor) had full sky light on the client, so not that: its
   ground was a cube's top layer, whose fire stands in the cube above, and that cube was not looked at again after the
   ground's conversion (fire waits for it) until the next phase. The turn 19 spot was the same (block 8 0 7, the bottom
   layer of its cube). Fixed; the retest above tells whether any spots are left.
2. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
3. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user]. Turn 19's offset on seed -5479930802988752619 (1,701 positions above the ground, up to 6) was CWG's snow
    populator turning surface water into ice after the model, so the self-test counted it as ground; turn 20 rerun with
    ice not counted: 0.
4. Pollution of the Realms integration investigation (the user, turn 26) [low, Claude: after the retest fixes]. If we can have solar fire create carbon/sulfur, and dust on block 
    destruction without causing too much to be created, some way to limit that. Even 1 of each type per XZ coordinate
    would still be lots of pollution blocks created. 

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Seen again in the turn 12 TOP_Y test: about 25 % of the area dark, without fire. Recheck after the turn 12
  engine clock (cubes no longer run at different times), then investigate after everything else [lowest, user].

## Later (relies on bottom-up mechanic from above)
- "Lava Apocalypse" preset for an infinite phase coming from below, converting air into lava.
- "Ice Apocalypse" preset mostly the same as normal config, but ice-ified, including SimpleDifficulty support and cold variants of some existing settings (cold damage, background cold damage e.t.c)
