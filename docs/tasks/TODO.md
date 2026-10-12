# To do

Done items move to [COMPLETED.md](COMPLETED.md); limitations and won't-fix items live in [OUTOFSCOPE.md](OUTOFSCOPE.md);
unplanned suggestions in [IDEAS.md](IDEAS.md). Cubic Chunks items come first, in priority order. Priorities in
brackets are the user's where they gave one, otherwise Claude's, with the reason.

## In progress
- (none)

## Waiting for the user's decision
Nothing here is dropped: each needs a yes or no (or another choice) from the user, with Claude's view given.
- Simple Difficulty default temperatures (turn 23, Claude's values): sun 1, 2, 3, 3, 4, 4, 5, 6, 7, 8, 9 and heat 0, 0,
  1, 1, 2, 2, 3, 4, 5, 6, 7 over phases 1 to 11, added up in the sun (its normal is 11-14, hot 15-19, burning 20-25).
  The user tunes them while testing (turn 25; checklist section E) [low, user].

## Waiting for the user's test (client side: Claude cannot see a client)
The user's test pass (turn 25 checklist; the user does it in turn 26). Tick the boxes here, and write what you saw
(coordinates from F3 for anything odd) into prompt.txt, item by item.

Setup
- [ ] Pull, then start the dev client with `-Pcompat_mods` on the Gradle command line (`./gradlew runClient
  -Pcompat_mods`, or in the IntelliJ run configuration's arguments). It adds Simple Difficulty and BuildCraft to
  `run/mods` for sections E and F (the first start downloads them); the next run without it removes them again.
- [ ] Config: to see every new default (the background conversions too), move `run/config/solarapocalypse.cfg` aside so
  a fresh one is written. Keeping yours also works: new options come in with their defaults, but its empty
  `backgroundConvert` lists stay empty.
- [ ] A new Custom Cubic world (the default preset), creative, cheats on. Commands used below: `/solar phase <n>`
  (jumps to a phase's start), `/solar status`, `/solar reload` (after editing the config), `/time add`.

A. The game starts with the mod as a coremod (turn 24)
- [ ] The client starts and a world loads normally. `run/logs/latest.log` names `SolarApocalypseCore`; a Forge warning
  about a coremod jar that also holds a mod is harmless (CubicWorldGen gets the same one).
- [ ] `/solar status` shows "... updated before sending" (not "Cubic Chunks patch not loaded"). No chat warning about
  the Cubic Chunks version on joining.

B. Terrain updated before you see it (turn 24, `performance.updateBeforeSending`)
- [ ] `/solar phase 4`, then fly into terrain never loaded before: it arrives already changed (no grass or leaves,
  dirt turned to gravel, no water) and nothing changes in view as it loads. The count in `/solar status` grows.
- [ ] Set `updateBeforeSending=false` in the config, `/solar reload`, fly into new terrain the other way: it loads
  as generated and changes in view a moment later. Set it back to `true` and `/solar reload`.

C. Dark spots (to-do item 1 and the "covered after /time add" item; turn 25 guard, see below)
- [ ] While doing the rest, watch the ground in the open: by day no darker patches, at night every fire spot lit.
- [ ] `/solar phase 8`, then `/time add 24000` twice: no areas that look covered (dark as if blocks sat above them).
- [ ] If you see a dark spot: note its coordinates and F3's light line there, run `/solar status` standing on it, then
  rejoin the world (or fly 300+ blocks away and back). Tell me whether rejoining cleared it: if it did, it was the
  clients' height maps (the turn 25 guard was meant to stop that); if not, it is the server's.

D. Break effects (turn 23, `blocks.breakEffects`)
- [ ] `/solar phase 3` beside trees and grass: leaves, plants and dirt changing near you show vanilla break particles and
  sounds; water evaporating near you fizzes and smokes. Nothing for changes more than 16 blocks away.
- [ ] `/solar phase 11` (a whole layer at a time, 200 a day): no client lag or stutter while the layers go near you.
- [ ] Expected: terrain updated before you see it (section B) makes no effects.

E. Simple Difficulty (turn 23; needs `-Pcompat_mods`)
- [ ] Temperature in the sun rises as the phases go on (phases 1, 4, 8, 10), also under trees and overhangs open to the
  sky (background heat); sealed rooms and caves stay normal.
- [ ] Tune `phase_n.sunTemperature` and `phase_n.backgroundTemperature` to taste (turn 25: you do this while testing);
  tell me the values you settle on and I make them the defaults.

F. Burning liquids (turn 23; needs `-Pcompat_mods`)
- [ ] A BuildCraft oil pool in the sun, `/solar phase 3`, then wait (fire comes to 10 % of burnable surfaces, spread over
  the phase): the oil catches vanilla fire, which spreads over the pool and burns it away. Water beside it gets none.
  (In phase 4 all liquids but lava evaporate, oil too.)

G. Settled sand and gravel (turn 21)
- [ ] After `/solar phase 4` (dirt became gravel in phase 3, gravel sand in phase 4): F3 on the converted ground shows
  `solarapocalypse:settled_block[type=...]`. It looks exactly like vanilla sand and gravel (same textures, random
  rotation), breaks with the same particles and sound, drops the vanilla item, and pick block gives the vanilla item.
- [ ] Dig under it: it does not fall.

H. Optional
- [ ] Water evaporating in slivers rather than chunks (to-do item 3, which you will bring up): anything you notice
  while watching water in phase 3.
- [ ] The production coremod path: `./gradlew build`, then `build/libs/solarapocalypse-0.0.1.jar` in a copy of your
  pack (not tried yet outside the dev client): it loads and section A holds there too.

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
   Lead (turn 24): Cubic Chunks marks every x/z of a column's heights whenever it tracks a new cube in it (empty air
   cubes too), so that tick's heightmap packet wraps to 0 and clients drop the column's height changes; block changes
   sent that tick keep the wrong heights their own packet gives, (x, x) for (x, z) (RESEARCH.md). Clients would then
   light those spots as covered. Same lead for the cubes that look covered after `/time add` (below). Turn 25: guarded
   (the engine makes no change in such a column that tick; with the Cubic Chunks patch only); the user's test pass
   (checklist section C) tells whether spots still show.
2. Done (turn 24): the hook before players get a cube (COMPLETED.md); kept here so item 3 keeps its number.
3. Water evaporating in slivers rather than chunks (the user, turn 11: "something we can definitely try to address
    later"): recheck after the turn 11 engine fix, which removed one cause (cut-short passes waiting behind every
    queued cube) [low, user; turn 24: the user will bring it up in a couple of turns].
4. Ship the user's phase sound and splash font when they are made (`assets/solarapocalypse/sounds.json`,
    `textures/font/splash.png`) [lowest until the user has made them, user].
5. Validate the CubicWorldGen surface model on the user's final world-gen preset [lowest until the user has chosen it,
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

## Later (relies on bottom-up mechanic from above)
- "Lava Apocalypse" preset for an infinite phase coming from below, converting air into lava.
- "Ice Apocalypse" preset mostly the same as normal config, but ice-ified, including SimpleDifficulty support and cold variants of some existing settings (cold damage, background cold damage e.t.c)
