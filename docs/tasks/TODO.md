# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **The gradient as the default phase 11** (turn 11): the defaults' phase 11 has no convert rules, so the carried rules
  blast the top layer and nothing happens below it. Put the user's chain in phase 11 by default (`grass -> grass_path
  layer=5`, `grass -> dirt layer=4`, `grass_path -> dirt layer=4`, grass / grass_path / dirt `-> gravel layer=3`,
  grass / dirt / gravel `-> minecraft:sand layer=2`, `grass_path -> solarapocalypse:vitrified_sand layer=2`)? Claude's
  view: yes, it is what the user described and costs nothing until phase 11.

## Will do (Cubic Chunks)
1. Rain puts out solar fire, per phase (the user, turn 11: players expect it, and our fire should differ from vanilla
   fire only in spreading; agreed by Claude) [high, Claude: players expect it, and it is next to the fire code just
   touched]: `phase_n.rainDousesFire` (default true), solar fire under
   rain (raining, a biome that rains and is warm enough there) goes out like vanilla fire and comes back when the rain
   stops; the engine looks at every loaded cube when rain starts and stops.
2. Background conversions [medium, user]: per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated); confirmed by the user: sealed rooms and caves without sky light are spared. Rides the pass
   every loaded cube already gets at a phase start.
3. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
4. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
5. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag].
6. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost [low, Claude: a performance nicety; needs a mixin into CC].
7. Simple Difficulty integration (optional, soft dependency) [low, Claude: optional].
8. Burning liquids (oil catching fire), if wanted [low, user].
9. Non-falling gravel and sand (user's idea, turn 9) [low, user]: blocks that look like gravel and sand and drop real
   gravel and sand but never fall, for the default rules. Converted gravel and sand fall later when something else
   updates them (a block popping off next to them, a player, flowing water); vanilla sand and gravel also check for a
   fall once when placed. Claude's view: a good compromise, cheap (vanilla textures, no new art). Only when block physics
   is false in config, also applies to converted blocks (minecraft:dirt -> minecraft:gravel changes in code to solarapocalpse:faux_gravel
   or whatever the block IDs end up being. Block physics = true means use vanilla gravel & sand as normal.
10. Verbose debug logging mode [low, user].
11. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user].
12. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
13. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Investigate after everything else [lowest, user].
