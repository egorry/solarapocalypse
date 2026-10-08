# Out of scope, limitations, won't fix

- **Modpack integrations** (LongerDays, ProperFiniteWater, ...): not catered for. The SUN clock follows any day-length
  mod by itself; only Cubic Chunks (and CubicWorldGen for the surface model) are soft dependencies.
- **CPU backlog does not delay phases.** A phase waits for its configured destruction and conversion time, not for the
  queue of loaded terrain: exploring keeps adding work. The outcome is the same, it only shows up later. So exploring
  or placing blocks cannot hold the apocalypse back (the user confirmed this is wanted, turn 9; the idea of holding the
  clock until loaded terrain is done is dropped).
- **Never-generated terrain in Cubic Chunks** counts as air. Without the CubicWorldGen model, a column's surface is only
  known once its top has been loaded; deep areas under never-seen surfaces wait (no erosion, no sun) until then.
- **CubicWorldGen presets with cube areas, or terrain floating more than 64 blocks above the rest**: no surface model;
  the recorded surface is used.
- **Floating transparent roofs** (a glass dome with air under it) are not the "surface" for conversions; the surface
  is the topmost opaque block plus whatever blocking blocks are stacked directly on it.
- **CC client heightmap packet bugs** (upstream, CC 0.0.1271): worked around by changing fewer than 256 x/z per column
  per tick; not fixed here.
- **Fire seconds are whole seconds**: Minecraft's `setFire` takes seconds; fractions would need an access transformer
  for little gain (damage amounts are decimals).
- **Per-dimension phase lists** (the user's decision): there is one set of phase lists for every dimension in
  `world.dimensions`; single entries can be scoped with `dimensions=...` (turn 9).
- **Webs dropping string**: melting ice turns to water, the water breaks a web and it drops string (vanilla; found by
  the user). Not patched.
- **Announcements** reach the players online when a phase starts; players joining later get no replay.
- **Entities without nearby players** stop ticking (vanilla), so burning does no damage there; sun damage still applies
  but hurt cooldowns do not run down.
