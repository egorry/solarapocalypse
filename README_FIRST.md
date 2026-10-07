# README_FIRST: start here

Solar Apocalypse is a Minecraft 1.12.2 Forge mod (IntelliJ workspace, Windows): over configurable phases the sun
evaporates water, converts and erodes blocks from the surface down and burns exposed mobs, also on terrain loaded later.
Cubic Chunks worlds first (CC is a soft dependency), vanilla-height worlds too.

Status: turn 2: core built for Cubic Chunks worlds (clock, phases, block effects, retroactive catch-up, sun damage);
open design questions in docs/SPEC.md section 10. [CLAUDE.md](CLAUDE.md) (loaded automatically) has the working rules.

## Documents
| File | Holds |
|---|---|
| [DESIGN.md](DESIGN.md) | The user's feature list (theirs: read, don't rewrite) |
| [docs/SPEC.md](docs/SPEC.md) | How the mod works as built: time, phases and depth, block effects, engine, sky rules, sun damage, checks, open questions |
| [docs/RESEARCH.md](docs/RESEARCH.md) | Feasibility research: how CC knows what faces the sky, the unknown-above problem, edit costs, retroactive hooks, the clock, prior art, probe results, open questions |
| [README.md](README.md) | What the mod is, development setup |
| [CHANGELOG.md](CHANGELOG.md) | Player-facing changes |

## Code (`src/main/java/com/solsticeentertainment/solarapocalypse/`)
- `SolarApocalypse`: the `@Mod`; wiring, per-tick budget, phase-change requeue, per-world `BlockChanges`.
- `SolarConfig`: every config value (Forge `Configuration`, `config/solarapocalypse.cfg`), defaults for 5 phases.
- `Timeline`: phase starts/ends and the sun's depth as pure functions of progress. `ApocalypseClock`: saved progress.
- `BlockRules`: selectors compiled into per-phase state maps. `BlockChanges`: what happens to one block at a progress.
- `Sky`: three-state sky test (vanilla; CC via `cc/CubicSky`). `SunDamage`: entity damage. `SolarCommand`: `/solar`.
- `cc/CubeEngine`: the CC block engine (queue, readiness, budget, top-down processing). `cc/CubicSky`: CC sky queries.
  Everything in `cc/` and `debug/` references CC classes and is only loaded when CC is installed.
- `debug/SelfTest`, `debug/CubicProbe`: dev-only checks (`-Dsolarapocalypse.selftest` / `.probe`).
- `src/test/.../TimelineTest`: unit test.

## Commands (Git Bash, repo root)
```
./gradlew build
bash scripts/smoke_server.sh <tag>   # start/stop the dev server, copy its log to research/
./gradlew test                        # TimelineTest
bash scripts/probe_server.sh <tag> selftest   # fresh dev world, phases end to end, prints SOLAR TEST lines
bash scripts/probe_server.sh <tag>   # fresh dev world + CubicProbe (research), prints the SOLAR PROBE lines
./gradlew runServer -Pno_dev_mods   # dev server without Cubic Chunks (soft-dependency check); the next run restores CC
javap -p -c -cp ../SRPCCC/research/deobf/opencubicchunks-292243-5135427-deobf.jar <class>   # exact CC signatures
```
