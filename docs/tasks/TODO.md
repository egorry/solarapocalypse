# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **"Our fire should be able to melt" (decision 1, turn 9)**: Claude's reading: solar fire already melts ice and snow
  the vanilla way, at no extra cost (it gives light 15; vanilla melts ice at block light 9+, about 6 blocks away, and snow
  at 12+, about 3 blocks), so no distance check was added; fire is still kept off spots right next to ice and snow.
  Question: should fire placed right next to ice or snow melt it at once (ice to water, snow to air) instead of the spot
  staying empty?

## Will do (Cubic Chunks)
1. Depth-staged conversions [medium, user]: a per-rule `depth=` modifier: `depth=3` acts in the top 3 layers of the
   current surface, `depth=2-3` only in layers 2 and 3 (any single depth, so nothing is lost against one list per
   depth); default: the phase's `convertDepth` (form chosen by Claude; the user's decision 9 left it open). In an
   infinite phase the layer about to go has every rule, deeper layers fewer (sand -> glass at 1, gravel -> sand at 2,
   dirt -> gravel at 3, grass and path -> dirt at 4, grass -> path at 5), so each block steps through the stages as the
   line comes down instead of jumping to the end.
   Cost: up to k changes per column per layer instead of one (the change cap still holds). Infinite phases now look at
   each cube every layer, which this needs.
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
   fall once when placed. Claude's view: a good compromise, cheap (vanilla textures, no new art).
10. Verbose debug logging mode [low, user].
11. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
12. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).