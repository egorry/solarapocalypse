# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order.

## In progress
- (none)

## Will do (Cubic Chunks)
1. Night puts fire out (option, off by default): solar fire goes out at dusk and the same spots relight at dawn, from the
   overworld's time of day (the clock now follows time skips). Off = today's behaviour: fire is never put out between
   non-infinite phases.
2. Depth-staged conversions (medium): a per-rule `depth k` modifier, the rule acting only in the top k layers of the
   current surface (default: the phase's `convertDepth`). In an infinite phase the layer about to go has every rule,
   deeper layers fewer (sand -> glass at 1, gravel -> sand at 2, dirt -> gravel at 3, grass and path -> dirt at 4,
   grass -> path at 5), so each block steps through the stages as the line comes down instead of jumping to the end.
   Cost: up to k changes per column per layer instead of one (the change cap still holds).
3. Background conversions (medium): per-phase rules that convert matching blocks at surface level even out of the sun
   (grass under overhangs, trees and mushrooms, odd terrain), but nothing underground, like background heat for mobs:
   where sky light reaches the block's top (`entities.backgroundMinSkyLight`, read from the cube's own light data, so
   nothing is generated). Rides the pass every loaded cube already gets at a phase start.
4. Heat-fused sand block (proposed name: vitrified sand, `solarapocalypse:vitrified_sand`): a rough, cloudy glass that
   is sand melted in place by the sun, unlike crafted glass; the default sand conversion (phase 5) would use it. Needs a
   texture (a placeholder until the user's own) and a choice of drop (itself, sand, or nothing).
5. Validate the CubicWorldGen surface model on the user's final world-gen preset once chosen.
6. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault (low).
7. Leaf culling when logs are removed before leaves (optional, for configs that burn wood first).
8. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`), only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag.
9. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost.
10. Simple Difficulty integration (optional, soft dependency).
11. Burning liquids (oil catching fire), if wanted (low).
12. Verbose debug logging mode (low).
13. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`); lowest until then.

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).