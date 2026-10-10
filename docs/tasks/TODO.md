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
- FPS with `blocks.solarFireModel` `SIMPLE` against `FULL` (turn 15, optional as the user asked), and with
  `blocks.solarFireLight` 8 (the new default) against 15 (the user, turn 16, offered to measure both; both are in from
  this turn). The model is read when models bake (game start, F3+T); the light applies to fire placed after a change.
- The spawner flames fix (turn 15: a destroyed spawner's flames should stop within a second).
- Opaque vitrified sand (turn 16): how it looks.

## Will do (Cubic Chunks)
1. Throughput headroom (the user, turn 13: after SURFACE; their TOP_Y test "works wonderfully" but falls behind: the
   line at Y -165 while the terrain was cut to Y 36, 203 layers (a day) behind, 707 by the time they were below Y 0,
   with their PC not struggling; turn 14: 77 a day of 200 at 2048 changes and 4 ms) [high, user]: research done (SPEC
   section 4); turn 14: the cheaper fire (~42 % more layers); turn 15: no fire in the default phase 11 (the user), and
   in the user's test their engine then kept up with 200 a day at 2048 and 4 ms (~100 with 85 % fire); default cap
   2048; turn 16: no conversions in the default phase 11 either (the user), and the engine stops going down a column at
   the first block nothing can change yet (Claude's suggestion; bench: 96 layers a minute without fire and conversions,
   from 72; SPEC section 4). Left: counting the cap in cubes resent per tick instead of changes (Claude's suggestion:
   the network and client cost is per cube, as 64 or more changes in a cube in one tick resend it whole).
2. Rain puts out solar fire, per phase (the user, turn 11: players expect it, and our fire should differ from vanilla
   fire only in spreading; agreed by Claude) [high, Claude: players expect it, and it is next to the fire code just
   touched]: `phase_n.rainDousesFire` (default true), solar fire under
   rain (raining, a biome that rains and is warm enough there) goes out like vanilla fire and comes back when the rain
   stops; the engine looks at every loaded cube when rain starts and stops.
3. Background conversions [medium, user]: per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated); confirmed by the user: sealed rooms and caves without sky light are spared. Rides the pass
   every loaded cube already gets at a phase start.
4. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [medium, the user's turn 14 observation: vanilla fire is their
   biggest FPS cost, 240 -> 80 fps; it was waiting on such a report]: fewer places where the sun lights vanilla fire,
   so fewer fronts burn at once (each burning block changes its neighbours, light and the client's mesh, and smokes).
   Claude's view: also try a lower `igniteFlammablePercent` meanwhile. Turn 15: no vanilla fire in the default phase 11
   any more (the user), so this is for phases 2-10.
5. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
6. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
7. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost [low, Claude: a performance nicety; needs a mixin into CC].
8. Simple Difficulty integration (optional, soft dependency) [low, Claude: optional].
9. Burning liquids (oil catching fire), if wanted [low, user].
10. Non-falling gravel and sand (user's idea, turn 9) [low, user]: blocks that look like gravel and sand and drop real
   gravel and sand but never fall, for the default rules. Converted gravel and sand fall later when something else
   updates them (a block popping off next to them, a player, flowing water); vanilla sand and gravel also check for a
   fall once when placed. Claude's view: a good compromise, cheap (vanilla textures, no new art). Only when block physics
   is false in config, also applies to converted blocks (minecraft:dirt -> minecraft:gravel changes in code to solarapocalpse:faux_gravel
   or whatever the block IDs end up being. Block physics = true means use vanilla gravel & sand as normal.
11. Verbose debug logging mode [low, user].
12. Block-breaking effects near the player (the user, turn 14, "later to-do item") [low, user: later]: breaking
   particles and sounds for the apocalypse's block changes, only within a limited radius of each player (as vanilla
   shows fire smoke only near the player when lots of fire is on the ground), configurable (on/off and radius). Much
   later the user will add their own breaking sounds, not one per block but mono or positionally panned, so this
   vanilla-style setting will then default to off.
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
