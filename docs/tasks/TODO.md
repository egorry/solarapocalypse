# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order.

## In progress
- (none)

## Will do (Cubic Chunks)
1. Per-phase `depthReference` (`INHERIT` / `SURFACE` / `TOP_Y`), for flat erosion in later phases. Proposed, waiting
   for the user's OK: each reference keeps its own depth line and a block goes when either line reaches it; a `TOP_Y`
   phase's depth counts down from `world.topY`, and what is above its line goes top down over the first tenth of the
   phase (as trees and buildings do now), so switching sweeps everything above the plane quickly.
2. Liquid classes. Proposed, waiting for the user's OK: per-phase `evaporate` selector lists instead of
   `evaporation.waterPhase` / `lavaPhase` / `otherLiquidsPhase`, with `fluid:<name>` and Forge temperature selectors
   (`temperature<300`), so cryotheum can go in phase 6 and oil in phase 2; optionally liquids that ignite.
3. Separate rule modes for conversions and destruction (`convertRuleMode`, `destroyRuleMode`).
4. Ship the user's phase sound and splash font once chosen (`assets/solarapocalypse/sounds.json`,
   `textures/font/splash.png`).
5. Validate the CubicWorldGen surface model on the user's final preset once chosen.
6. Leaf culling when logs are removed before leaves (optional, for configs that burn wood first).
7. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`), only if the user's tests (`/solar fire`, `/solar status`)
   show vanilla fire lag.
8. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost.
9. Simple Difficulty integration (optional, soft dependency).
10. Verbose debug logging mode (low priority).

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.
