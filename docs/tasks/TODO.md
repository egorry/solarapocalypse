# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- Background conversions in the default phases (turn 20: `phase_n.backgroundConvert` is empty by default, so nothing
  changes until rules are set). Claude's suggestion: the phase's own surface rules where shade should not protect,
  e.g. phase 1 grass, mycelium and farmland -> grass_path, phase 3 leaves, plants and snow -> air, phase 4 wood -> air
  (canopies and trunks go whole instead of from the top), so trees, mushrooms and the ground under them and under
  overhangs go with the open ground. Cost: a phase with background rules looks at every block of a cube on a pass,
  not only down to its line [medium, user].

## Waiting for the user's test (client side: Claude cannot see a client)
- (none)

## Will do (Cubic Chunks)
1. Dark spots: why ground counts as under cover in open sky at all on a vanilla-like Cubic Chunks preset (the user,
   turn 19: their single spot, cube -19 5 -13, block 8 0 7, near -296 80 -201, is situational; investigate the cause
   in general instead; turn 20: the world type is Cubic Chunks' default "Custom Cubic" preset, vanilla-like generation
   made Cubic Chunks aware); see also the later investigation of cubes that look covered after `/time add` [low, user].
   Turn 20 investigation (code, CC source and a read-only scan of copies of 5 dev saves): no single cause confirmed.
   Fixed: ground uncovered two or more cubes down (a crown or roof gone) waited for its cube's next look, the next phase
   or day, so it stayed without fire (the missing fire light reads as dark at night). Ruled out: a stale height map
   after the engine's own removals (the scan found no height map top on a clear block). Real cover: the mod and Cubic
   Chunks' sky light take the column's top from the same height map, which also counts cubes not loaded or not sent
   to clients yet, so ground under them is both covered and dark until the engine reaches them. In the saves, the
   user's spot is open and correct in one and under a real oak canopy in the other. Both reports predate the turn 12
   engine clock: recheck on the current code; if a spot shows again, Claude would add a self-test count of open-sky
   ground the engine treats as covered.
2. Pre-first-light hook (optional CC mixin): process new cubes before their first light, no pop-in, no light or packet
   cost [low, Claude: a performance nicety; needs a mixin into CC].
3. Simple Difficulty integration (optional, soft dependency) [low, Claude: optional].
4. Burning liquids (oil catching fire), if wanted [low, user].
5. Non-falling gravel and sand (user's idea, turn 9) [low, user]: blocks that look like gravel and sand and drop real
   gravel and sand but never fall, for the default rules. Converted gravel and sand fall later when something else
   updates them (a block popping off next to them, a player, flowing water); vanilla sand and gravel also check for a
   fall once when placed. Claude's view: a good compromise, cheap (vanilla textures, no new art). Only when block physics
   is false in config, also applies to converted blocks (minecraft:dirt -> minecraft:gravel changes in code to solarapocalpse:faux_gravel
   or whatever the block IDs end up being. Block physics = true means use vanilla gravel & sand as normal.
   Turn 20: this also fixes turn 17's "terrain cut ahead of the TOP_Y line" (closed): the 4 blocks were converted sand
   or gravel that fell into a cave by vanilla's check on placement; where the 32 blocks around are not all loaded it
   falls at once, so it looks cut. Claude would raise this item to medium for that reason; the user's call.
6. Verbose debug logging mode [low, user].
7. Block-breaking effects near the player (the user, turn 14, "later to-do item") [low, user: later]: breaking
   particles and sounds for the apocalypse's block changes, only within a limited radius of each player (as vanilla
   shows fire smoke only near the player when lots of fire is on the ground), configurable (on/off and radius). Much
   later the user will add their own breaking sounds, not one per block but mono or positionally panned, so this
   vanilla-style setting will then default to off.
8. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user].
9. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
10. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
    user]. Turn 19's offset on seed -5479930802988752619 (1,701 positions above the ground, up to 6) was CWG's snow
    populator turning surface water into ice after the model, so the self-test counted it as ground; turn 20 rerun with
    ice not counted: 0.

## Will do (vanilla worlds)
- Block engine on chunks (sun damage already works there), recorded surface on chunks, fire.

## Later (much later) investigation
- A direction for finite worlds, and potentially infinite (CC) worlds with a starting Y if desired. This would affect which direction the apocalypse comes from, rather than being top-down it could be bottom-up (intended for the Nether).
- Cubes that look covered after `/time add` in later phases (the user, turn 11): darkened as if blocks sat above them,
  and no fire relit there until the next phase or day; likely Cubic Chunks lighting or heightmap lag after mass
  removal. Seen again in the turn 12 TOP_Y test: about 25 % of the area dark, without fire. Recheck after the turn 12
  engine clock (cubes no longer run at different times), then investigate after everything else [lowest, user].
