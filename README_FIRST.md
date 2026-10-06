# README_FIRST: start here

Solar Apocalypse is a Minecraft 1.12.2 Forge mod (IntelliJ workspace, Windows): over configurable phases the sun
evaporates water, converts and erodes blocks from the surface down and burns exposed mobs, also on terrain loaded later.
Cubic Chunks worlds first (CC is a soft dependency), vanilla-height worlds too.

Status: research done (turn 1); design next. [CLAUDE.md](CLAUDE.md) (loaded automatically) has the working rules.

## Documents
| File | Holds |
|---|---|
| [DESIGN.md](DESIGN.md) | The user's feature list (theirs: read, don't rewrite) |
| [docs/RESEARCH.md](docs/RESEARCH.md) | Feasibility research: how CC knows what faces the sky, the unknown-above problem, edit costs, retroactive hooks, the clock, prior art, probe results, open questions |
| [README.md](README.md) | What the mod is, development setup |
| [CHANGELOG.md](CHANGELOG.md) | Player-facing changes |

## Code (`src/main/java/com/solsticeentertainment/solarapocalypse/`)
- `SolarApocalypse`: the `@Mod`. With `-Dsolarapocalypse.probe` and CC loaded it runs the probe after server start.
- `debug/CubicProbe`: dev-only research probe (references CC classes; only loaded when CC is present).

## Commands (Git Bash, repo root)
```
./gradlew build
bash scripts/smoke_server.sh <tag>   # start/stop the dev server, copy its log to research/
bash scripts/probe_server.sh <tag>   # fresh dev world + CubicProbe, prints the SOLAR PROBE lines
javap -p -c -cp ../SRPCCC/research/deobf/opencubicchunks-292243-5135427-deobf.jar <class>   # exact CC signatures
```
