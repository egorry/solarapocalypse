# Ideas and suggestions (not planned)

- GitHub issues for bugs once others play-test; TODO.md stays the plan.
- Release tags once the vanilla part lands, so the changelog gets version sections.
- The self-test as a GitHub Actions run, once the CC and CWG jars can be fetched headlessly.
- Realistic weather (the user, turn 11): no rain in the first phases, or virga, with dry lightning striking withered
  plants and dried ground; from phase 5 unprecedented hot super-storms flooding the dry land. Not for the user's world
  ("I do not want to flood my world"); others can build it with `phase_n.weather` and the conversion system.
- Cheaper fire in infinite phases: a destroyed block could become the next layer's fire in one change (now: block
  removed, old fire removed, new fire placed = 3 changes per column per layer, two thirds of phase 11's work).
- Measure client cost (packets, render rebuilds) with `runClient`; the user tests multiplayer themselves first.
