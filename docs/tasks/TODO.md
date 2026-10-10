# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **Sleeping: jump or step?** (turn 12) A time skip (sleeping, `/time`, `/solar set/add/phase`) brings the engine's
  clock straight to the present: one round then takes every cube down the whole skip, cube by cube, so until it is done
  finished cubes stand next to unfinished ones (at 200 layers a day a night's sleep is 100 layers). The other way:
  sleeping leaves the clock alone and the engine carries on a layer per round (even, but the world then lags the
  apocalypse's day; it catches up only if the engine is faster than `layersPerDay`), and players no longer die in their
  sleep from erosion (they fall when it gets there). Claude's view: sleeping steps (the user wants even erosion above
  all), `/time` and `/solar` keep jumping (they are test tools, and the user uses them to get to a phase quickly).
  Until the user decides, everything jumps as before.

## Will do (Cubic Chunks)
1. Cheaper fire in infinite phases (the user, turn 12: upgraded from IDEAS; must not undo the turn 12 fixes)
   [high, user]: done in part (turn 12: no redraw on every layer, which was all the work of a TOP_Y line in the sky).
   Left: the block the erosion takes becomes the new surface's fire in one change (now: block removed, old fire removed,
   new fire placed = 3 changes per column per layer, so this saves a third of SURFACE erosion's work).
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
4. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
5. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
6. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag].
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
