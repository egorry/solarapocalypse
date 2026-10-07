# Out of scope, limitations, won't fix

- **Modpack integrations** (LongerDays, ProperFiniteWater, ...): not catered for. The SUN clock follows any day-length
  mod by itself; only Cubic Chunks (and CubicWorldGen for the surface model) are soft dependencies.
- **"One conversion per block per phase"** would need a memory per block. Rules are evaluated on the current block, so
  a chain (grass -> dirt, dirt -> sand) always runs to its end; `phases.ruleMode` picks which phases' rules apply.
- **Never-generated terrain in Cubic Chunks** counts as air. Without the CubicWorldGen model, a column's surface is only
  known once its top has been loaded; deep areas under never-seen surfaces wait (no erosion, no sun) until then.
- **CubicWorldGen presets with cube areas, or terrain floating more than 64 blocks above the rest**: no surface model;
  the recorded surface is used.
- **Floating transparent roofs** (a glass dome with air under it) are not the "surface" for conversions; the surface
  is the topmost opaque block plus whatever blocking blocks are stacked directly on it.
- **CC client heightmap packet bugs** (upstream, CC 0.0.1271): worked around by changing fewer than 256 x/z per column
  per tick; not fixed here.
- **Entities without nearby players** stop ticking (vanilla), so burning does no damage there; sun damage still applies
  but hurt cooldowns do not run down.
