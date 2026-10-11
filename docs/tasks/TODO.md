# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- Lower `igniteFlammablePercent` defaults (the user, turn 19: "I already want (and I think I already gave you?) lower
  flammability values"): the defaults are the user's turn 13 table, vanilla fire on flammables at the same percent as
  solar fire (0, 5, 10, 25, 30, 40, 50, 60, 70, 75, then none in phase 11); no lower row is on file. Needs the values
  per phase [medium, user].

## Waiting for the user's test (client side: Claude cannot see a client)
- Floating water over caverns (the user's turn 18 report; turn 19: they will re-test mixed destruction and evaporation
  in one phase, and what they saw was not under unloaded cubes). Turn 19 scan of copies of their saves
  (`scripts/cubescan.py`, liquid resting on air): "Phase 11 SURFACE Test 4" (10-10, before turn 16's hold) 41,293
  water sources on air, sheets of sea one block thick over open air (e.g. at -478 35 -403, air down to Y 6): the floor
  eroded under the water, as the user describes; "Test 5" (10-11 09:39, with the hold) 90 in four spots, all in
  columns whose cubes above Y 47 were never saved, the player having been far below (lava pockets down to Y -347): the
  water's cube unloaded while the floor's was not, fixed in turn 18 (some of that water has since run into the hole);
  "Test 6" none. So the hold works; if the re-test shows floating water, the save's name is enough for a scan [high,
  user: a defect they saw].

## Will do (Cubic Chunks)
1. Background conversions [medium, user]: per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated); confirmed by the user: sealed rooms and caves without sky light are spared. Rides the pass
   every loaded cube already gets at a phase start.
2. Terrain cut ahead of the TOP_Y line (Claude's observation, turn 17 self-test, seed 699324865493784867): with the
   engine 25 layers behind (its line at Y -29), 4 terrain blocks at Y -38 were gone, below both lines (the SURFACE line
   there at Y 16-18), air above them (a cave); three within a few blocks of each other. Turn 16's code does the same on
   that seed, so it predates this turn; seed 14014 shows none [medium, Claude: a block cut ahead of the line is the kind
   of defect the user saw as caverns in turn 12, though only 4 of ~43 k columns].
3. Dark spots: why ground counts as under cover in open sky at all on a vanilla-like Cubic Chunks preset (the user,
   turn 19: their single spot, cube -19 5 -13, block 8 0 7, near -296 80 -201, is situational; investigate the cause
   in general instead); likely a CC sky light or heightmap fault; see also the later investigation of cubes that look
   covered after `/time add` [low, user].
4. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost [low, Claude: a performance nicety; needs a mixin into CC].
5. Simple Difficulty integration (optional, soft dependency) [low, Claude: optional].
6. Burning liquids (oil catching fire), if wanted [low, user].
7. Non-falling gravel and sand (user's idea, turn 9) [low, user]: blocks that look like gravel and sand and drop real
   gravel and sand but never fall, for the default rules. Converted gravel and sand fall later when something else
   updates them (a block popping off next to them, a player, flowing water); vanilla sand and gravel also check for a
   fall once when placed. Claude's view: a good compromise, cheap (vanilla textures, no new art). Only when block physics
   is false in config, also applies to converted blocks (minecraft:dirt -> minecraft:gravel changes in code to solarapocalpse:faux_gravel
   or whatever the block IDs end up being. Block physics = true means use vanilla gravel & sand as normal.
8. Verbose debug logging mode [low, user].
9. Block-breaking effects near the player (the user, turn 14, "later to-do item") [low, user: later]: breaking
   particles and sounds for the apocalypse's block changes, only within a limited radius of each player (as vanilla
   shows fire smoke only near the player when lots of fire is on the ground), configurable (on/off and radius). Much
   later the user will add their own breaking sounds, not one per block but mono or positionally panned, so this
   vanilla-style setting will then default to off.
10. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user].
11. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
12. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user]. Turn 19 self-test, seed -5479930802988752619 (snowy spawn, the default preset): the model lay above the
    generated ground at 1,701 of 43,264 positions, by up to 6 (turn 18's seed: none); not looked into yet [Claude].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Seen again in the turn 12 TOP_Y test: about 25 % of the area dark, without fire. Recheck after the turn 12
  engine clock (cubes no longer run at different times), then investigate after everything else [lowest, user].
