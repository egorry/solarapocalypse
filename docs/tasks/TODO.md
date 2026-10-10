# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **Higher default block change cap?** (turn 13: the user asked where 512 comes from and what raising it does; the
  research is in SPEC section 4 and the turn 13 reply.) 512 was a starting value from a suggestion the user relayed in
  turn 3 ("around 512", against client and network bursts), never measured. In the user's turn 13 TOP_Y test the cap
  bound the whole time (512.0 changes a tick) while the engine used 0.7-1.3 ms of its 4 ms and the server 6-10 ms of
  its 50; through solid terrain at their view distance (~1,100 columns) that is ~14 layers a day of the 200 set.
  Nothing crashes at higher values: the cost is server tick time (lighting, which Cubic Chunks defers until a cube is
  sent), client re-meshing (a cube with 64 or more changes in a tick is resent whole) and, in multiplayer, upload
  bandwidth. Claude's view: default `maxBlockChangesPerTick` 2048 (about 4 times the speed), `tickBudgetMs` stays 4
  (on a slower server the time budget then binds first), and the user tries 4096-8192 with `tickBudgetMs` 10-20 on
  their PC (`/solar reload` applies them; watch `/solar status` and FPS). Until the user decides, 512 stays.
- **What stands above the terrain in a fast first phase** (turn 13, found while fixing the user's SURFACE test): in
  the user's shortcut test (phase 11 as phase 1), trees and the sea now go top down over the first tenth of the phase
  (0.2 days), while the 200-a-day erosion of the terrain starts at once, so for those minutes the lowest water and tree
  trunks hang over ground already eroded under them (up to ~40 layers). In the full config they are gone in phase 7, at
  one layer a day, long before phase 11, so this shows only in shortcut tests and in configs whose first destroying
  phase is fast. A way out: in the first phase on a line, the terrain waits until what stands on it has gone (the line
  starts `surfaceMargin` above the terrain at the layer rate, at most a tenth of the phase), which delays the first
  terrain layer by up to that. Claude's view: not needed for the real config; worth doing if the user wants shortcut
  tests to look right.

## Will do (Cubic Chunks)
1. Cheaper fire in infinite phases (the user, turn 12: upgraded from IDEAS; must not undo the turn 12 fixes)
   [high, user]: done in part (turn 12: no redraw on every layer, which was all the work of a TOP_Y line in the sky).
   Left: the block the erosion takes becomes the new surface's fire in one change (now: block removed, old fire removed,
   new fire placed = 3 changes per column per layer, so this saves a third of SURFACE erosion's work).
2. Throughput headroom (the user, turn 13: after SURFACE; their TOP_Y test "works wonderfully" but falls behind: the
   line at Y -165 while the terrain was cut to Y 36, 203 layers (a day) behind, 707 by the time they were below Y 0,
   with their PC not struggling) [high, user]: research done (SPEC section 4, the turn 13
   reply, the default cap waiting above). Left: the user's experiments with higher limits; the cheaper fire above
   (a third fewer changes); counting the cap in cubes resent per tick instead of changes (Claude's suggestion: the
   network and client cost is per cube, as 64 or more changes in a cube in one tick resend it whole).
3. Rain puts out solar fire, per phase (the user, turn 11: players expect it, and our fire should differ from vanilla
   fire only in spreading; agreed by Claude) [high, Claude: players expect it, and it is next to the fire code just
   touched]: `phase_n.rainDousesFire` (default true), solar fire under
   rain (raining, a biome that rains and is warm enough there) goes out like vanilla fire and comes back when the rain
   stops; the engine looks at every loaded cube when rain starts and stops.
4. Background conversions [medium, user]: per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated); confirmed by the user: sealed rooms and caves without sky light are spared. Rides the pass
   every loaded cube already gets at a phase start.
5. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
6. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
7. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag].
8. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost [low, Claude: a performance nicety; needs a mixin into CC].
9. Simple Difficulty integration (optional, soft dependency) [low, Claude: optional].
10. Burning liquids (oil catching fire), if wanted [low, user].
11. Non-falling gravel and sand (user's idea, turn 9) [low, user]: blocks that look like gravel and sand and drop real
   gravel and sand but never fall, for the default rules. Converted gravel and sand fall later when something else
   updates them (a block popping off next to them, a player, flowing water); vanilla sand and gravel also check for a
   fall once when placed. Claude's view: a good compromise, cheap (vanilla textures, no new art). Only when block physics
   is false in config, also applies to converted blocks (minecraft:dirt -> minecraft:gravel changes in code to solarapocalpse:faux_gravel
   or whatever the block IDs end up being. Block physics = true means use vanilla gravel & sand as normal.
12. Verbose debug logging mode [low, user].
13. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user].
14. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
15. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Seen again in the turn 12 TOP_Y test: about 25 % of the area dark, without fire. Recheck after the turn 12
  engine clock (cubes no longer run at different times), then investigate after everything else [lowest, user].
