# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- **Higher default block change cap?** (turn 13: the user asked where 512 comes from and what raising it does; the
  research is in SPEC section 4.) 512 was a starting value from a suggestion the user relayed in turn 3 ("around
  512", against client and network bursts), never measured. Turn 13 TOP_Y test: the cap bound the whole time while the
  engine used ~1 ms of its 4. Turn 14: the user ran 2048 with `tickBudgetMs` 4 (not 4096 or more, which they expect to
  cost their 60 fps floor): 77 layers a day of the 200 set (91 behind at depth 145), 70-120 fps, the server 11-14 ms a
  tick; the cap and the 4 ms bound about equally. Since turn 14 a layer with fire takes ~30 % fewer changes and engine
  time, so the same settings should give ~110 a day; 200 a day at their view distance needs ~3,200 changes and ~6 ms of
  engine time a tick (the server ~20-25 ms a tick, still 20 TPS); the client is the open question (SPEC section 4).
  Claude's view: default 2048 (the user's own setting), `tickBudgetMs` stays 4; on their PC one short try of 4096 with
  `tickBudgetMs` 8 shows whether 200 a day keeps 60 fps (`/solar reload` back if not). Until the user decides, 512
  stays.
- **What stands above the terrain in a fast first phase** (turn 13, found while fixing the user's SURFACE test): in
  the user's shortcut test (phase 11 as phase 1), trees and the sea go top down over the first tenth of the phase
  (0.2 days), while the 200-a-day erosion of the terrain starts at once, so for those minutes the lowest water and tree
  trunks hang over ground already eroded under them (up to ~40 layers). In the full config they are gone in phase 7, at
  one layer a day, long before phase 11 (and the default phases 3 and 4 already turn leaves and wood to air). Turn 14,
  the user asked (an enquiry, not to be done yet): don't trees count as part of the surface; why wait for them, and why
  is the terrain under them affected but not them; can they be added to that definition, at what impact and
  complexity; would converting all leaves and logs to air in the fast phase be a bandaid? Answered in the turn 14
  reply: the SURFACE reference is the terrain CubicWorldGen shapes before caves, trees, structures and water (the sea
  floor under the sea), the same whenever a cube loads, so terrain loaded later is cut at its neighbours' layer; trees
  stand above it. Conversions count from another surface, each column's current top block, trees included. Ways to
  include trees: (a) the column's real top as the reference: the erosion under trees and builds would start late by
  their height (terrain pillars where trees stood), and the top is unknown until the cubes above load; (b) a lead-in:
  in the first phase on a line, the line starts `surfaceMargin` (48) above the terrain at the layer rate (small, in
  Timeline; delays the first terrain layer by up to a tenth of the phase); (c) everything above the terrain at the
  phase start (turn 12's behaviour: the sea in one burst). The bandaid works and is in the user's test config already:
  `material:leaves -> air` and `material:wood -> air` (no `layer=`) in an infinite phase act at its start in the top
  `convertDepth` layers of the current surface, top down within a pass, so trees go in the engine's first look at each
  cube (`material:wood` also takes planks and wooden builds). Claude's view: the bandaid is enough for shortcut tests;
  (b) only if the user wants it without the rules.
- **Water and the SURFACE line** (the user, turn 14, an enquiry: water evaporation and SURFACE erosion do not match up,
  as SURFACE counts from the ground under the water while evaporation comes down flat from `topY` or goes at once; they
  set evaporation to `INSTANT` for their SURFACE tests; can SURFACE treat water as its surface, or other ideas?). Now,
  in one phase with both, water goes by whichever comes first: the destroy rule (`*` takes liquids), above the sea
  floor top down over the first tenth of the phase by height above the local floor (deep water first, shallows last);
  evaporation `LAYERS` flat from `topY` at its own rate (water above `topY`, lakes and falls on hills, at the start);
  `INSTANT` at the start. The floor erodes from its own top from the start, under water still standing (without
  physics the water hangs over the cut). Options: (a) water as surface: over standing water the reference is the
  water's top (CubicWorldGen's sea level), so the sea comes down flat with the land and its floor is cut once the water
  above is gone; the floor's shape is lost where the line has passed it; small change; but in the default phases the
  sea evaporates in phase 3, and the dry floors would then lie below the line of phases 7-10 (1-5 layers) and keep
  their sand and gravel meanwhile; (b) the floor waits for its water: a block under standing liquid is not destroyed
  until the liquid has gone, then catches up at once; the basin keeps its shape and drains flat, the floor drops
  several layers in one go as the water leaves it; moderate; (c) config only (what the user did): `INSTANT`, or
  `LAYERS` with the phase's `layersPerDay` and `topY` at sea level. Claude's view: (c) for tests; the default phases
  evaporate the sea long before erosion, so nothing shows there; (b) if seas should drain alongside erosion in one
  phase.
- **Lighter solar fire?** (turn 14, from the throughput measurements and the user's question on what makes their PC
  struggle; they report vanilla fire as their biggest FPS cost, 240 -> 80 fps.) Measured on the dev server (SPEC section
  4): at 85 % fire, fire is ~45 % of a layer's block changes, and its light about half the server's time per layer
  (lighting runs outside the engine's budget; the light field shifts down with every layer, ~15 values a column): the
  same world with the fire's light off took 428 ms of server time a layer instead of ~990. On the client each floor
  fire is vanilla's model, ~40 see-through quads (flames on four sides and two layers), so 85 % of the visible surface
  on fire draws many times the terrain's own geometry: likely the biggest FPS cost (unmeasured; Claude cannot see the
  client). Options: (a) `blocks.solarFireLight` (0-15, default 15): less lighting work, a dimmer glow at night; in
  multiplayer clients must get the server's value (sent on login); (b) a lighter model: the floor layers only (8 quads
  instead of 40), a lower, thinner flame; (c) a lower `ignitePercent` (possible now). Claude's view: the user first
  compares FPS with `ignitePercent` 0 against 85 (`/solar reload`; the fire goes as the layers move on); if fire is
  the FPS cost, (b); (a) if the server side matters (multiplayer).

## Will do (Cubic Chunks)
1. Throughput headroom (the user, turn 13: after SURFACE; their TOP_Y test "works wonderfully" but falls behind: the
   line at Y -165 while the terrain was cut to Y 36, 203 layers (a day) behind, 707 by the time they were below Y 0,
   with their PC not struggling; turn 14: 77 a day of 200 at 2048 changes and 4 ms) [high, user]: research done (SPEC
   section 4); turn 14: measured with `scripts/probe_server.sh <tag> bench`, and the fire made cheaper (the cheaper fire
   item, done: COMPLETED turn 14), ~42 % more layers for the same engine time. Left: the user's A/B tries (fire off, and
   4096 with 8 ms once; see the cap decision above); skipping blocks that cannot change yet (Claude's suggestion: in an
   infinite phase every block of a cube the line is in is looked at on every layer, about a quarter of the engine's
   time in the profile; below the line and the conversions' layers, nothing changes until the line gets there);
   counting the cap in cubes resent per tick instead of changes (Claude's suggestion: the network and client cost is
   per cube, as 64 or more changes in a cube in one tick resend it whole).
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
   Claude's view: also try a lower `igniteFlammablePercent` meanwhile.
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
