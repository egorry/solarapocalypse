# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **Ice and snow melting farther from solar fire.** Vanilla melts ice at block light 9 or more (about 6 blocks from
  fire) and snow at 12 or more (about 3 blocks); solar fire is only kept off spots right next to ice and snow
  (diagonals too). Claude's view: leave the farther melting to vanilla (fire melting nearby ice is what heat does, and
  a 13x13x13 scan per fire spot would leave wide fire-free rings in snowy biomes). Alternative: keep fire 3-6 blocks
  from ice and snow.
- **Speed of catching up after a deliberate time skip** (sleeping, `/time set`). It now runs at the same lag-safe
  budget as any backlog. Alternative: a bigger budget for a while after a skip (faster, but lag right after waking).
- **`clock.maxSunJump`** caps one skip at one day (24000), so `/time add 72000` counts one day. Alternative: no cap.
- **Phases crossed in one skip**: a skip over two phase starts announces only the last phase. Alternative: announce
  each phase crossed, one after another.
- **Night dousing and vanilla fire**: with `blocks.nightDousesFire`, night puts out solar fire and lights no new fire;
  vanilla fire already burning (on wood, leaves) is left to burn out. Alternative: put vanilla fire out at night too.
- **Vitrified sand as the default** (Claude's call this turn): the default phase 5 rule is now
  `minecraft:sand -> solarapocalypse:vitrified_sand` instead of vanilla glass (the user's default set and DESIGN.md
  have glass). Alternative: keep glass as the default and leave vitrified sand for configs.
- **Red sand** melts to the same vitrified sand, which drops plain sand. Alternative: a red variant, or dropping red
  sand.
- **Background conversions: what counts as surface level** (TODO 2): Claude's proposal is "sky light reaches the
  block's top" (`entities.backgroundMinSkyLight`, as for background heat on mobs), so overhangs, tree shade and open
  caves near the surface count, sealed rooms and deep caves do not.
- **Depth-staged conversions: config form** (TODO 1): Claude's proposal is a `depth k` modifier per rule (the rule acts
  in the top k layers), which expresses the user's example ("all previous + ..." per depth). It cannot express a rule
  for one middle depth only; one rule list per depth could, at the cost of a bigger config.
- **Player-placed blocks changing at once** (IDEAS): they now change on their cube's next look. Alternative: queue the
  cube when a block is placed, so placed grass turns to path within a second.

## Will do (Cubic Chunks)
1. Depth-staged conversions [medium, user]: a per-rule `depth k` modifier, the rule acting only in the top k layers of the
   current surface (default: the phase's `convertDepth`). In an infinite phase the layer about to go has every rule,
   deeper layers fewer (sand -> glass at 1, gravel -> sand at 2, dirt -> gravel at 3, grass and path -> dirt at 4,
   grass -> path at 5), so each block steps through the stages as the line comes down instead of jumping to the end.
   Cost: up to k changes per column per layer instead of one (the change cap still holds). Infinite phases now look at
   each cube every layer, which this needs.
2. Background conversions [medium, user]: per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated). Rides the pass every loaded cube already gets at a phase start.
3. Validate the CubicWorldGen surface model on the user's final world-gen preset once chosen [waits for the preset].
4. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
5. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
6. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag].
7. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost [low, Claude: a performance nicety; needs a mixin into CC].
8. Simple Difficulty integration (optional, soft dependency) [low, Claude: optional].
9. Burning liquids (oil catching fire), if wanted [low, user].
10. Verbose debug logging mode [low, user].
11. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).