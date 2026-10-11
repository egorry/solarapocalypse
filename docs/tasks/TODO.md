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
- Floating water over caverns (the user's turn 18 report, with turn 16 code; the bench, whose terrain is loaded before
  the phase, saw none): turn 18 fixed ground under a cube that is not loaded being cut. With the lead-in phase the water
  is gone before the erosion; if water still floats anywhere, the coordinates, and whether the player had been far
  below or away from the spot, would show which case it is [high, user: a defect they saw].

## Will do (Cubic Chunks)
1. Background conversions [medium, user]: per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated); confirmed by the user: sealed rooms and caves without sky light are spared. Rides the pass
   every loaded cube already gets at a phase start.
2. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [medium, the user's turn 14 observation: vanilla fire is their
   biggest FPS cost, 240 -> 80 fps; it was waiting on such a report]: fewer places where the sun lights vanilla fire,
   so fewer fronts burn at once (each burning block changes its neighbours, light and the client's mesh, and smokes).
   Claude's view: also try a lower `igniteFlammablePercent` meanwhile. Turn 15: no vanilla fire in the default phase 11
   any more (the user), so this is for phases 2-10.
3. Terrain cut ahead of the TOP_Y line (Claude's observation, turn 17 self-test, seed 699324865493784867): with the
   engine 25 layers behind (its line at Y -29), 4 terrain blocks at Y -38 were gone, below both lines (the SURFACE line
   there at Y 16-18), air above them (a cave); three within a few blocks of each other. Turn 16's code does the same on
   that seed, so it predates this turn; seed 14014 shows none [medium, Claude: a block cut ahead of the line is the kind
   of defect the user saw as caverns in turn 12, though only 4 of ~43 k columns].
4. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
5. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
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
11. Block-breaking effects near the player (the user, turn 14, "later to-do item") [low, user: later]: breaking
   particles and sounds for the apocalypse's block changes, only within a limited radius of each player (as vanilla
   shows fire smoke only near the player when lots of fire is on the ground), configurable (on/off and radius). Much
   later the user will add their own breaking sounds, not one per block but mono or positionally panned, so this
   vanilla-style setting will then default to off.
12. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user].
13. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
14. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Seen again in the turn 12 TOP_Y test: about 25 % of the area dark, without fire. Recheck after the turn 12
  engine clock (cubes no longer run at different times), then investigate after everything else [lowest, user].
