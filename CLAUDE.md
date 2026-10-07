# Solar Apocalypse: notes for Claude Code sessions

- Read `README_FIRST.md` (index of the docs and commands) before working. `DESIGN.md` (root) is the user's own feature
  list: read it, don't rewrite it.
- The user writes follow-up prompts into `prompt.txt` (the IntelliJ terminal mangles long prompts) and may append to it
  mid-task: re-check it once or twice while working. `prompt.txt`, `test_log_files/` and `research/` are git-ignored.
- Target pack: CurseForge instance `C:\Users\Solstice\curseforge\minecraft\Instances\Cubic Chunks` (CubicChunks 0.0.1271,
  CubicWorldGen 0.0.152, MixinBooter 11.17, LongerDays 1.0.4 with Time Multiplier 3). Never modify any CurseForge instance.
- Only Cubic Chunks is a (soft) dependency; nothing is tailored to the user's pack (no LongerDays, ProperFiniteWater or
  other pack-mod integration). Sibling projects `../SRPCCC` and `../SolsticeCCPatches` use the same setup; reuse their patterns and research material
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
- End-to-end check: `bash scripts/probe_server.sh <tag> selftest` (deletes `run/world_cubic`, runs `debug/SelfTest` via
  `-Dsolarapocalypse.selftest`: jumps through the phases, counts blocks around spawn, checks sun damage; the server stops
  itself). Research probe: same script without `selftest` (`debug/CubicProbe`). Logs land in `research/` (git-ignored).
  `run/config/solarapocalypse.cfg` is regenerated with defaults if deleted. Unit tests: `./gradlew test`.
  Without CC: `./gradlew runServer -Pno_dev_mods` (removes the dev mods from run/mods; the next normal run restores them).
  Console commands can be piped into `./gradlew runServer` (stdin reaches the server).
- CC pitfalls (details in docs/RESEARCH.md): on a CC server `World.getBlockState`, `World.getLight`, `canBlockSeeSky`,
  `getTopSolidOrLiquidBlock`, `Chunk.getBlockState` and setBlockState flag 1 GENERATE unloaded cubes; use
  `getLoadedCube`/`isBlockLoaded` and flag 18. CC treats never-generated cubes as air (false sky exposure).
