# Solar Apocalypse

A Minecraft 1.12.2 Forge mod: over a configurable number of phases the sun becomes deadly. Surface water evaporates,
blocks scorch into other blocks and erode layer by layer from the surface down, and mobs burn in direct sunlight (later
everywhere). Changes also apply to terrain that is generated or loaded after a phase began.

Status: early development; the core works in Cubic Chunks worlds. See [README_FIRST.md](README_FIRST.md),
[docs/SPEC.md](docs/SPEC.md) and [docs/RESEARCH.md](docs/RESEARCH.md).

- Cubic Chunks worlds are supported first (CubicChunks 1.12.2-0.0.1271, CubicWorldGen 0.0.152). Cubic Chunks is optional:
  the mod never requires it.
- Vanilla-height worlds are planned after the Cubic Chunks part.

## Development
CleanroomMC TemplateDevEnv: Java 25 runs Gradle 9.7 + RetroFuturaGradle; the mod compiles for Java 8 against Forge
14.23.5. `./gradlew build` builds; `./gradlew runServer` / `runClient` install the deobfuscated CubicChunks and
CubicWorldGen jars into `run/mods` first (`gradle/scripts/extra.gradle`). The dev server creates a `CustomCubic` world.

License: MIT.
