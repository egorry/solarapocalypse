# Ideas and suggestions (not planned)

- GitHub issues for bugs once others play-test; TODO.md stays the plan.
- Release tags once the vanilla part lands, so the changelog gets version sections.
- The self-test as a GitHub Actions run, once the CC and CWG jars can be fetched headlessly.
- Measure client cost (packets, render rebuilds) with `runClient`; the user tests multiplayer themselves first.
- Blocks players place (by hand or a builder's wand) wait for their cube's next look (its next due change, the next
  phase, a reload) and never delay anything else. If they should change at once: queue the cube on placement (asked
  in TODO.md, "Waiting for the user's decision").
- Optional: hold the clock at a phase boundary while loaded terrain still has queued work, with a time limit (exploring
  keeps adding work, so it can never be "all done").
