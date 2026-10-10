# Ideas and suggestions (not planned)

- GitHub issues for bugs once others play-test; TODO.md stays the plan.
- Release tags once the vanilla part lands, so the changelog gets version sections.
- The self-test as a GitHub Actions run, once the CC and CWG jars can be fetched headlessly.
- Realistic weather (the user, turn 11): no rain in the first phases, or virga, with dry lightning striking withered
  plants and dried ground; from phase 5 unprecedented hot super-storms flooding the dry land. Not for the user's world
  ("I do not want to flood my world"); others can build it with `phase_n.weather` and the conversion system.
- `TOP_Y` starting at the loaded terrain (the user, turn 12: "finds the highest Top Y of loaded chunks around players
  and destroys down to that layer, waits for catchup, then continues layer-by-layer"). Claude's view: it can work as
  `world.topY = loaded` (the highest ground loaded when the TOP_Y phase starts, saved then and fixed after, so terrain
  loaded later gets the same line); a line that followed whatever is loaded would cut differently depending on where
  players had been. Since turn 12 a line in the empty sky costs nothing (no fire redraw, the engine clock skips to the
  next change), so this only saves waiting time: (topY - highest ground) / layersPerDay days. Today the same is done by
  setting `world.topY` to the terrain's height by hand.
- Per-phase evaporation rate (the user, turn 12, asked for water to come down by its own level next to the TOP_Y line:
  that is `evaporation.mode = LAYERS`, already the default, but its `layersPerDay` and `topY` are one setting for every
  phase). Per-phase values if a set of phases needs water to go at different speeds.
- Measure client cost (packets, render rebuilds) with `runClient`; the user tests multiplayer themselves first.
