# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **Water and the SURFACE line** (the user, turn 14, an enquiry: water evaporation and SURFACE erosion do not match up,
  as SURFACE counts from the ground under the water while evaporation comes down flat from `topY` or goes at once; they
  set evaporation to `INSTANT` for their SURFACE tests; can SURFACE treat water as its surface, or other ideas?). Turn
  15, the user: option (c) does not work: with `LAYERS` the floor is cut from its own top while the flat level is still
  above it, so a cavern opens under the water (held up, as block physics is off); (a) and (b) sound promising; are they
  changes to the evaporation or to the surface destruction? Decision next turn. Answer: both change the destruction,
  not the evaporation, which stays as it is (water goes by whichever comes first). (a) changes where the line is: over
  standing water the SURFACE reference becomes the water's top (CubicWorldGen's sea level where the terrain lies below
  it; lakes are not in the generator's model and keep their floor), so the `*` destroy rule takes the sea flat from sea
  level down with the land, and the floor once the line reaches it; small, in the surface model. (b) changes when a
  block may go: the engine does not destroy a block under standing liquid (any liquid, lakes too) until the liquid
  has gone, by evaporation or the destroy rule; the floor then catches up its missing layers at once; moderate, in the
  engine's pass. How it is now: in one phase with both, the destroy rule (`*` takes liquids) takes water above the
  floor top down over the first tenth of a first phase (at the start of a later one), deepest first;
  evaporation `LAYERS` flat from `topY` at its own rate (water above `topY` at the start), `INSTANT` at the start (it
  works, by taking all the water at once). Claude's view: (b): basins keep their shape and drain flat, lakes too; (a)
  loses the floor's shape, and in the default phases, where the sea evaporates in phase 3, the dry floors would lie
  below the line of phases 7-10 (1-5 layers) and wait for phase 11.
- **Solar fire light default** (turn 15: the user asked for a configurable light level, "defaulting to what, 4?";
  `blocks.solarFireLight` is in, default 15 until the user decides). Measured (bench, 85 % fire in an infinite phase):
  server time a layer 992 ms at light 15, 599 at 4, 428 at 0, so 4 saves ~70 % of the light's cost. Light below 8 also
  lets mobs spawn beside fire at night (more night spawns dying in the sun); the flames stay full-bright either way.
  Claude's view: keep 15 as the default (vanilla's glow, and with no fire in phase 11 the default phases no longer pay
  for moving fire: fire placed once per phase costs little); 4 in configs with fire in an infinite phase.
- **Stone stages in phase 11** (the user, turn 15: they plan to add stone -> cobblestone -> gravel -> sand -> vitrified
  sand to the default gradient later, or instead dirt and stone straight to vitrified sand in layer 1; how much more
  lag?). Measured (bench, no fire; SPEC section 4): each stage in stone is one more change per column per layer. The
  four stone stages: 4.4 times the changes of today's gradient, ~3 times the server time a layer, 20 layers a minute
  instead of 54 (at the user's 2048 and 4 ms ~50-80 a day instead of ~200; keeping 200 needs ~7,500 changes and ~9 ms
  a tick). One vitrified stage in layer 1 (`convertDepth` 1): 1.8 times the changes, 39 a minute (~115-150 a day).
  No conversions: 67 a minute (~225-260 a day). Clients re-mesh about as often either way (a cube's stages change in
  the same pass, one resend a layer, plus the cube below where the stages reach into it). Vitrified sand is see-through:
  a surface of it lets sky light into the block below (lighting work every layer) and clients draw it in the
  translucent pass. Claude's view: one stage, to an opaque block or in layer 2 (the top is then already converted when
  it shows); several stages only with a lower `layersPerDay`.

## Will do (Cubic Chunks)
1. Throughput headroom (the user, turn 13: after SURFACE; their TOP_Y test "works wonderfully" but falls behind: the
   line at Y -165 while the terrain was cut to Y 36, 203 layers (a day) behind, 707 by the time they were below Y 0,
   with their PC not struggling; turn 14: 77 a day of 200 at 2048 changes and 4 ms) [high, user]: research done (SPEC
   section 4); turn 14: the cheaper fire (~42 % more layers); turn 15: no fire in the default phase 11 (the user), and
   in the user's test their engine then kept up with 200 a day at 2048 and 4 ms (~100 with 85 % fire); default cap
   2048. Left: skipping blocks that cannot change yet (Claude's suggestion: in an infinite phase every block of a cube
   the line is in is looked at on every layer, about a quarter of the engine's time; below the line and the
   conversions' layers nothing changes until the line gets there; needs care with the next-look time of excluded
   blocks); counting the cap in cubes resent per tick instead of changes (Claude's suggestion: the network and client
   cost is per cube, as 64 or more changes in a cube in one tick resend it whole).
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
4. Vanilla fire seed cap (`maxVanillaFireSeedsPerChunk`) [medium, the user's turn 14 observation: vanilla fire is their
   biggest FPS cost, 240 -> 80 fps; it was waiting on such a report]: fewer places where the sun lights vanilla fire,
   so fewer fronts burn at once (each burning block changes its neighbours, light and the client's mesh, and smokes).
   Claude's view: also try a lower `igniteFlammablePercent` meanwhile. Turn 15: no vanilla fire in the default phase 11
   any more (the user), so this is for phases 2-10.
5. Investigate the user's spot that counts as under cover in open sky (cube -19 5 -13, block 8 0 7, near
   -296 80 -201, vanilla-like CC preset); likely a CC sky light or heightmap fault [low, user].
6. Leaf culling when logs are removed before leaves [low, Claude: only matters for configs that burn wood first].
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
12. Block-breaking effects near the player (the user, turn 14, "later to-do item") [low, user: later]: breaking
   particles and sounds for the apocalypse's block changes, only within a limited radius of each player (as vanilla
   shows fire smoke only near the player when lots of fire is on the ground), configurable (on/off and radius). Much
   later the user will add their own breaking sounds, not one per block but mono or positionally panned, so this
   vanilla-style setting will then default to off.
13. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user].
14. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
15. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user].

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Seen again in the turn 12 TOP_Y test: about 25 % of the area dark, without fire. Recheck after the turn 12
  engine clock (cubes no longer run at different times), then investigate after everything else [lowest, user].
