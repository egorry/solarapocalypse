# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **Carried rules keep their own phase's `convertDepth`** (found turn 10): in `CARRY`, a rule from phase 3 (depth 1)
  still acts only on the surface block in phase 11, whose `convertDepth` of 5 only applies to rules written in phase 11
  itself (none by default). So the default infinite phase converts 1 or 2 layers deep, not 5. Claude's view: carried
  rules should use the running phase's `convertDepth` unless they have their own `depth=`, which matches what the
  setting says ("conversions reach the top this many layers"). Change it?
- **Rain and splash water potions** do not put out solar fire (flowing or placed water does). Should rain douse it
  (slowly, like vanilla fire) or splash potions put it out? Claude's view: potions yes (a cheap event hook, players
  expect it), rain no (the sun's fire, and the phases already decide how much burns).

## Will do (Cubic Chunks)
1. Background conversions [medium, user]: per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated); confirmed by the user: sealed rooms and caves without sky light are spared. Rides the pass
   every loaded cube already gets at a phase start.
2. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
3. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
4. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag].
5. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost [low, Claude: a performance nicety; needs a mixin into CC].
6. Simple Difficulty integration (optional, soft dependency) [low, Claude: optional].
7. Burning liquids (oil catching fire), if wanted [low, user].
8. Non-falling gravel and sand (user's idea, turn 9) [low, user]: blocks that look like gravel and sand and drop real
   gravel and sand but never fall, for the default rules. Converted gravel and sand fall later when something else
   updates them (a block popping off next to them, a player, flowing water); vanilla sand and gravel also check for a
   fall once when placed. Claude's view: a good compromise, cheap (vanilla textures, no new art). Only when block physics
   is false in config, also applies to converted blocks (minecraft:dirt -> minecraft:gravel changes in code to solarapocalpse:faux_gravel
   or whatever the block IDs end up being. Block physics = true means use vanilla gravel & sand as normal.
9. Verbose debug logging mode [low, user].
10. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
11. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).