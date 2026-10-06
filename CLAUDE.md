# Solar Apocalypse: notes for Claude Code sessions

- Read `README_FIRST.md` (index of the docs and commands) before working. `DESIGN.md` (root) is the user's own feature
  list: read it, don't rewrite it.
- The user writes follow-up prompts into `prompt.txt` (the IntelliJ terminal mangles long prompts) and may append to it
  mid-task: re-check it once or twice while working. `prompt.txt`, `test_log_files/` and `research/` are git-ignored.
- Target pack: CurseForge instance `C:\Users\Solstice\curseforge\minecraft\Instances\Cubic Chunks` (CubicChunks 0.0.1271,
  CubicWorldGen 0.0.152, MixinBooter 11.17, LongerDays 1.0.4 with Time Multiplier 3). Never modify any CurseForge instance.
- Sibling projects `../SRPCCC` and `../SolsticeCCPatches` use the same setup; reuse their patterns and research material
  (CC 0.0.1271 decomp: `../SRPCCC/research/decomp/CubicChunks`, CC source: `../SRPCCC/research/repos/CubicChunks`,
  CWG: `../SRPCCC/research/{decomp,repos}/CubicWorldGen`, MC sources: `../SRPCCC/build/rfg/minecraft-src/java`).

## Build / run
- `./gradlew build`: Java 25 runs Gradle; the mod compiles for Java 8. Package `com.solsticeentertainment.solarapocalypse`,
  modid `solarapocalypse`.
- Cubic Chunks is a soft dependency: `compileOnly rfg.deobf(curse.maven...)`, `@Mod(dependencies = "after:cubicchunks")`.
  Code that references CC classes must only be loaded when `Loader.isModLoaded("cubicchunks")`.
- CC and CWG are installed into `run/mods` by `installDevMods` (gradle/scripts/extra.gradle) before `runClient`/`runServer`.
  Do NOT move CubicChunks onto the runtime classpath: GradleStart would load its coremod twice
  (`DuplicateModsFoundException: cubicchunkscore`).
- `run/config` mirrors the pack's `cubicchunks.cfg`, `cubicgen.cfg` and the two CC mixin configs; `run/server.properties`
  uses `level-type=CustomCubic`, `level-name=world_cubic`.
- Headless smoke test: `bash scripts/smoke_server.sh <tag>` (starts runServer, waits for `Done`/crash, stops
  it, copies `run/logs/latest.log` to `research/server_latest_<tag>.log`).
- Research probe: `bash scripts/probe_server.sh <tag>` (deletes `run/world_cubic`, runs `debug/CubicProbe` via
  `-Dsolarapocalypse.probe`, the server stops itself). Logs land in `research/` (git-ignored).
- CC pitfalls (details in docs/RESEARCH.md): on a CC server `World.getBlockState`, `World.getLight`, `canBlockSeeSky`,
  `getTopSolidOrLiquidBlock`, `Chunk.getBlockState` and setBlockState flag 1 GENERATE unloaded cubes; use
  `getLoadedCube`/`isBlockLoaded` and flag 18. CC treats never-generated cubes as air (false sky exposure).
