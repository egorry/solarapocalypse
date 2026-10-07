# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order.

## In progress
- (none)

## Will do (Cubic Chunks)
1. Ship the user's phase sound and splash font once chosen (`assets/solarapocalypse/sounds.json`,
   `textures/font/splash.png`).
2. Validate the CubicWorldGen surface model on the user's final preset once chosen.
3. Leaf culling when logs are removed before leaves (optional, for configs that burn wood first).
4. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`), only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag.
5. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost.
6. Simple Difficulty integration (optional, soft dependency).
7. Burning liquids (oil catching fire), if wanted (low priority).
8. Verbose debug logging mode (low priority).

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.
