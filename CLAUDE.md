# Solar Apocalypse: notes for Claude Code sessions

- Read `README_FIRST.md` (pointers to the docs, task lists, code layout and commands) before working.
- `docs/DESIGN.md` is the user's own feature list: read it, don't rewrite it.
- The user writes prompts into `prompt.txt` (the IntelliJ terminal mangles long prompts) and may append to it mid-task:
  re-check it once or twice while working. `prompt.txt`, `test_log_files/` and `research/` are git-ignored.
- Keep `docs/tasks/` current while working: TODO.md (in progress / will do), COMPLETED.md (done action items, short),
  OUTOFSCOPE.md (limitations, won't fix). Player-facing changes go to `docs/CHANGELOG.md`, behaviour to `docs/SPEC.md`.
  Keep this file and README_FIRST.md as static pointers.
- "Commit" means commit and push. Commit and push at the end of every turn (and freely in between). Ask the user
  before anything else in git (deleting, rebasing, branching, force-pushing...).
- Only Cubic Chunks (and CubicWorldGen) are soft dependencies; nothing is tailored to the user's modpack.
- The user's CurseForge instances may be read, never modified. Target pack: `C:\Users\Solstice\curseforge\minecraft\Instances\Cubic Chunks`.
- Sibling projects `../SRPCCC` and `../SolsticeCCPatches` share the setup and research material (CC 0.0.1271 decomp:
  `../SRPCCC/research/decomp/CubicChunks`, CC source: `../SRPCCC/research/repos/CubicChunks`, CWG:
  `../SRPCCC/research/{decomp,repos}/CubicWorldGen`, MC sources: `../SRPCCC/build/rfg/minecraft-src/java`).

## Build / run
- `./gradlew build`: Java 25 runs Gradle; the mod compiles for Java 8. Package `com.solsticeentertainment.solarapocalypse`,
  modid `solarapocalypse`.
- Soft dependencies: `compileOnly rfg.deobf(curse.maven...)`, `@Mod(dependencies = "after:cubicchunks")`. Code that
  references CC or CWG classes lives in `cc/` and runs only behind `Loader.isModLoaded(...)` checks.
- CC and CWG are installed into `run/mods` by `installDevMods` (gradle/scripts/extra.gradle) before `runClient`/`runServer`.
  Do NOT move CubicChunks onto the runtime classpath: GradleStart would load its coremod twice
  (`DuplicateModsFoundException: cubicchunkscore`).
- `run/` is git-ignored: `run/config` mirrors the pack's CC/CWG configs; `run/server.properties` uses
  `level-type=CustomCubic`, `level-name=world_cubic`. Delete `run/config/solarapocalypse.cfg` after config changes to
  regenerate it with the current defaults. Console commands can be piped into `./gradlew runServer` (stdin reaches it).
- Dev checks write their logs to `research/`. The self-test deletes `run/world_cubic` and runs on its own config
  (`scripts/selftest.cfg`, copied to `run/config/solarapocalypse-selftest.cfg`). The user keeps named configs in
  `run/config` (`solarapocalypse - <name>.cfg`): leave them alone.
- CC pitfalls (details in docs/RESEARCH.md): on a CC server `World.getBlockState`, `World.getLight`, `canBlockSeeSky`,
  `getTopSolidOrLiquidBlock`, `Chunk.getBlockState` and setBlockState flag 1 GENERATE unloaded cubes; use
  `getLoadedCube`/`isBlockLoaded` and flag 18. CC treats never-generated cubes as air (false sky exposure).
