# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md).

## In progress
- (none)

## Will do
- Ignition: a percentage of exposed blocks, chosen deterministically, catch fire (instant or spread); refreshed after each
  destroyed layer; an own animated fire block that does not spread or tick; optional scan that ignites flammables.
- Leaf culling when logs are removed before leaves (optional, for configs that burn wood first).
- Vanilla (non-cubic) worlds: block engine on chunks (sun damage already works there), recorded surface on chunks.
- Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet cost.
- Simple Difficulty integration (optional, soft dependency).
- Measure client cost (packets, render rebuilds) with `runClient`.
- Validate the CubicWorldGen surface model on the user's final preset once chosen.
- Default phase content: the user's base phase effects.
