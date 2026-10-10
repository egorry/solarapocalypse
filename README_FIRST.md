# README_FIRST: start here

Solar Apocalypse is a Minecraft 1.12.2 Forge mod (IntelliJ workspace, Windows): over configurable phases the sun
evaporates liquids, converts and erodes blocks from the surface down and burns mobs, also on terrain loaded later.
Cubic Chunks worlds first (CC is a soft dependency), vanilla-height worlds too. [CLAUDE.md](CLAUDE.md) (loaded
automatically) has the working rules; this file points at everything else. Current state: the task lists.

## Documents
| File | Holds |
|---|---|
| [docs/tasks/TODO.md](docs/tasks/TODO.md) | In progress and will do |
| [docs/tasks/COMPLETED.md](docs/tasks/COMPLETED.md) | Done action items, per turn |
| [docs/tasks/OUTOFSCOPE.md](docs/tasks/OUTOFSCOPE.md) | Limitations, won't fix, out of scope |
| [docs/tasks/IDEAS.md](docs/tasks/IDEAS.md) | Suggestions, not planned |
| [docs/DESIGN.md](docs/DESIGN.md) | The user's feature list (theirs: read, don't rewrite) |
| [docs/SPEC.md](docs/SPEC.md) | How the mod works as built |
| [docs/RESEARCH.md](docs/RESEARCH.md) | Feasibility research (turn 1): how CC knows what faces the sky, costs, hooks, clock, prior art |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | Player-facing changes |
| [README.md](README.md) | The mod and its Forge dev environment |

## Code (`src/main/java/com/solsticeentertainment/solarapocalypse/`)
- Package root: mod wiring (`SolarApocalypse`), config (`SolarConfig`), time (`ApocalypseClock`, `Timeline`), block
  rules and their evaluation (`BlockRules`, `BlockChanges`, `SurfaceRecord`), sun, heat and fire (`Sky`, `SunDamage`, `SolarFire`), the vitrified sand block (`VitrifiedSand`),
  phase announcements and the network channel (`Announcer`), `/solar` (`SolarCommand`). No Cubic Chunks classes here.
- `client/`: client-only rendering (the phase splash, the vitrified sand item model); never loaded on a dedicated server.
- `cc/`: everything that touches Cubic Chunks or CubicWorldGen classes; only loaded when they are installed.
- `debug/`: dev-only checks run by `scripts/probe_server.sh` (probe, self-test, bench).
- `src/test/`: unit tests.

## Commands (Git Bash, repo root)
```
./gradlew build                               # also runs the unit tests
bash scripts/probe_server.sh <tag> selftest   # fresh dev world, phases end to end on scripts/selftest.cfg, prints SOLAR TEST lines
bash scripts/probe_server.sh <tag> bench [fire0,stone,...]   # throughput of an infinite phase (scripts/bench.cfg; variants in debug/Bench), SOLAR BENCH lines
bash scripts/probe_server.sh <tag>            # fresh dev world + research probe, prints SOLAR PROBE lines
bash scripts/smoke_server.sh <tag>            # start/stop the dev server, copy its log to research/
./gradlew runServer -Pno_dev_mods             # dev server without Cubic Chunks; the next normal run restores it
javap -p -c -cp ../SRPCCC/research/deobf/opencubicchunks-292243-5135427-deobf.jar <class>   # exact CC signatures
```
