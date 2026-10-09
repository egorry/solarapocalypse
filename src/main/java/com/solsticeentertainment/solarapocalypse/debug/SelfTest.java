package com.solsticeentertainment.solarapocalypse.debug;

import com.solsticeentertainment.solarapocalypse.ApocalypseClock;
import com.solsticeentertainment.solarapocalypse.BlockChanges;
import com.solsticeentertainment.solarapocalypse.BlockRules;
import com.solsticeentertainment.solarapocalypse.SolarConfig;
import com.solsticeentertainment.solarapocalypse.SolarFire;
import com.solsticeentertainment.solarapocalypse.cc.CubicSky;
import com.solsticeentertainment.solarapocalypse.cc.CwgSurface;
import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import com.solsticeentertainment.solarapocalypse.Sky;
import com.solsticeentertainment.solarapocalypse.Timeline;
import com.solsticeentertainment.solarapocalypse.cc.CubeEngine;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProvider;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockSand;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * Dev-only end-to-end check on a fresh Cubic Chunks world with the default config (-Dsolarapocalypse.selftest,
 * scripts/probe_server.sh <tag> selftest). Jumps the clock through the phases, lets the engine catch the loaded spawn
 * cubes up, counts blocks around spawn and checks sun damage on three pigs. Logs "SOLAR TEST" lines, then stops the server.
 */
public final class SelfTest {

    private static final int RADIUS = 6; // columns around spawn to count
    private static MinecraftServer server;
    private static WorldServer world;
    private static int stage, ticks, waited;
    private static EntityPig sunPig, roofPig, deepPig;
    private static long engineNanos, engineCubes, engineBlocks;
    private static int fireCount, cap, planeY;
    private static long lastChanged, maxChanges;
    private static final float[] firstHit = new float[2];

    private SelfTest() {}

    public static void run(MinecraftServer s) {
        server = s;
        world = s.getWorld(0);
        MinecraftForge.EVENT_BUS.register(SelfTest.class);
    }

    private static void log(String format, Object... args) {
        SolarApocalypse.LOGGER.info("SOLAR TEST " + format, args);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        Timeline t = SolarApocalypse.timeline();
        switch (stage) {
            case 0:
                if (ticks < 40) return; // let spawn cubes finish lighting
                log("depth reference {}, CubicWorldGen model {}, phases start {} end {}", SolarConfig.depthReference,
                        CubicSky.model(world) != null, t.firstStart(), t.end(t.phaseCount() - 1));
                configCheck();
                modelCheck();
                rulesCheck();
                depthCheck();
                carryCheck();
                settleCheck();
                cap = SolarConfig.maxBlockChangesPerTick;
                SolarConfig.maxBlockChangesPerTick = 0; // catch-ups at full speed; the cap is checked at the end
                SolarConfig.phases[3].message = "&6The ground cracks"; // the phase 4 start log line carries it
                census("fresh world");
                spawnPigs();
                jump(t.end(0) - 1, "end of phase 1, every world tick made 12 ms slower");
                break;
            case 1:
                if (!drained(600)) return;
                census("after phase 1");
                pigs("phase 1 (fire only)");
                for (EntityPig pig : new EntityPig[]{sunPig, roofPig, deepPig}) pig.setEntityInvulnerable(true); // live through phase 3
                jump(t.end(2) - 1, "end of phase 3 (fire: 5 % solar)");
                break;
            case 2:
                if (!drained(3000)) return;
                fire("end of phase 3");
                // Entities stop ticking 300 ticks after the last player left, so hurt cooldowns never run out here:
                // reset them and check one hit (phase 4: sun 4 + background 1, background reaches 3 blocks of cover).
                for (EntityPig pig : new EntityPig[]{sunPig, roofPig, deepPig}) {
                    pig.setEntityInvulnerable(false);
                    pig.setHealth(pig.getMaxHealth());
                    pig.extinguish();
                    pig.hurtResistantTime = 0;
                }
                jump(t.start(3) + 1, "start of phase 4");
                break;
            case 3:
                // the first hit only: with entities still ticking, hurt cooldowns can run out and let a second one in
                if (firstHit[0] == 0 && sunPig.getHealth() < sunPig.getMaxHealth()) firstHit[0] = sunPig.getHealth();
                if (firstHit[1] == 0 && roofPig.getHealth() < roofPig.getMaxHealth()) firstHit[1] = roofPig.getHealth();
                if (ticks - waited < 25) return;
                log("pigs after the first hit in phase 4 (expect sun 5, roof 9, deep 10): sun {}, roof {}, deep {}",
                        firstHit[0], firstHit[1], deepPig.getHealth());
                jump(t.end(3) - 1, "end of phase 4 (fire: 10 % solar in total, phase 3's 5 % kept; 75 % of flammables)");
                break;
            case 4:
                if (!drained(3000)) return;
                fireCount = fire("end of phase 4");
                throwWater();
                stage++;
                waited = ticks;
                break;
            case 5:
                if (ticks - waited < 200) return;
                int out = 0;
                for (BlockPos spot : splashed) if (!SolarFire.is(world.getBlockState(spot))) out++;
                log("water bottles: {} of {} solar fires hit are out ({}); solar fire after 200 more ticks: {} (was {}, less what the"
                        + " bottles put out; it must not spread or burn out)", out, splashed.size(), splashed.size(), fire("200 ticks later"), fireCount);
                SolarConfig.nightDousesFire = true;
                timeOfDay(18000, 0, "midnight, blocks.nightDousesFire on");
                break;
            case 6:
                if (!drained(3000)) return;
                fire("midnight (expect no solar fire)");
                timeOfDay(13000, 0, "sunset half way: fires go out between 12000 and 14000");
                break;
            case 7:
                if (!drained(3000)) return;
                fire("time of day 13000 (expect about half of 10 %)");
                timeOfDay(2000, 1, "next morning: fires come back between 23000 and 1000");
                break;
            case 8:
                if (!drained(3000)) return;
                fire("morning (expect 10 % again)");
                SolarConfig.nightDousesFire = false;
                world.getWorldInfo().setRaining(true); // phase 5 inherits phase 4's NONE: this rain must stop
                world.getWorldInfo().setThundering(true);
                jump(t.start(4) + Timeline.days(0.5), "phase 5 + 0.5 days");
                break;
            case 9:
                if (!drained(3000)) return;
                log("weather in phase 5 (NONE, inherited from phase 4; rain was set before the jump): raining {} (false),"
                        + " thundering {} (false)", world.getWorldInfo().isRaining(), world.getWorldInfo().isThundering());
                fire("erosion (15 % per layer, earlier fire removed)");
                census("erosion");
                long depth = (long) t.depthAt(ApocalypseClock.progress(), Timeline.SURFACE);
                lineCheck(depth + " layers of erosion", (x, z) -> {
                    int ground = CubeEngine.groundAt(world, x, z);
                    return ground == BlockChanges.NO_Y ? BlockChanges.NO_Y : (int) (ground - depth + 1);
                });
                layerCheck();
                SolarConfig.maxBlockChangesPerTick = cap;
                jump(t.start(4) + Timeline.days(2), "phase 5 + 2 days, block changes capped at " + cap + " per tick");
                lastChanged = BlockChanges.changed;
                break;
            case 10:
                maxChanges = Math.max(maxChanges, BlockChanges.changed - lastChanged);
                lastChanged = BlockChanges.changed;
                if (ticks - waited < 100) return;
                log("most block changes in one tick over 100 ticks: {} (cap {}), {} cubes still queued", maxChanges, cap,
                        CubeEngine.queued(world));
                SolarConfig.maxBlockChangesPerTick = 0;
                stage++;
                waited = ticks;
                break;
            case 11:
                if (!drained(3000)) return; // the skip's catch-up first
                evenness("caught up after the skip");
                SolarConfig.maxBlockChangesPerTick = cap;
                log("--- the clock runs at a layer every 20 ticks for 400 ticks, block changes capped at {} per tick: the engine falls"
                        + " behind; phase 6 starts on the way (day 18), which must not bring cubes ahead of the rest", cap);
                stage++;
                waited = ticks;
                break;
            case 12:
                ApocalypseClock.set(ApocalypseClock.progress() + Timeline.DAY / 16 / 20); // phase 5: 16 layers a day
                if (ticks - waited < 400) return;
                evenness("the clock running ahead of the engine");
                SolarConfig.maxBlockChangesPerTick = 0;
                // below what the SURFACE line (frozen during phase 6) leaves of the highest ground, so the plane has terrain to cut
                planeY = highestGround() - (int) t.depthAt(t.start(5), Timeline.SURFACE) - 8;
                if ((planeY & 15) == 0) planeY += 2; // on a cube's bottom the cube above it would be empty: a check of nothing
                jump(t.reachTime(planeY, SolarApocalypse.topY(world), Timeline.TOP),
                        "phase 6 (TOP_Y line from Y " + SolarApocalypse.topY(world) + ") down to Y " + planeY);
                break;
            case 13:
                if (!drained(3000)) return;
                long top = SolarApocalypse.topY(world) - (long) t.depthAt(ApocalypseClock.progress(), Timeline.TOP) + 1;
                lineCheck("TOP_Y line at Y " + top, (x, z) -> (int) top);
                log("weather in phase 6 (THUNDER): raining {} (true), thundering {} (true), vanilla's rain counter {} (at most 12000)",
                        world.getWorldInfo().isRaining(), world.getWorldInfo().isThundering(), world.getWorldInfo().getRainTime());
                server.initiateShutdown();
                stage++;
                break;
            default:
        }
    }

    /**
     * How evenly the SURFACE erosion came down: layers each spawn column lost (its reference minus its top), against the
     * line. Even erosion keeps nearly every column within a layer of the others (caves and lakes under the reference aside).
     */
    private static void evenness(String when) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        Map<Integer, Integer> lost = new java.util.TreeMap<>();
        int columns = 0;
        for (int x = spawn.getX() - RADIUS * 16; x < spawn.getX() + RADIUS * 16; x++) {
            for (int z = spawn.getZ() - RADIUS * 16; z < spawn.getZ() + RADIUS * 16; z++) {
                Chunk column = cubes.getLoadedColumn(x >> 4, z >> 4);
                int ground = CubeEngine.groundAt(world, x, z);
                if (column == null || ground == BlockChanges.NO_Y) continue;
                lost.merge(ground - (((IColumn) column).getHeightValue(x & 15, z & 15) - 1), 1, Integer::sum);
                columns++;
            }
        }
        int best = 0, mode = 0;
        for (Map.Entry<Integer, Integer> e : lost.entrySet()) {
            int near = e.getValue() + lost.getOrDefault(e.getKey() + 1, 0);
            if (near > best) {
                best = near;
                mode = e.getKey();
            }
        }
        log("evenness, {}: line at {} layers, engine behind by {} layers; layers lost by column {}; {} % of {} columns within"
                        + " two neighbouring values ({}-{})", when, (long) SolarApocalypse.timeline().depthAt(ApocalypseClock.progress(), Timeline.SURFACE),
                String.format("%.1f", CubeEngine.behind * 16.0 / Timeline.DAY), lost, String.format("%.1f", best * 100.0 / Math.max(1, columns)),
                columns, mode, mode + 1);
    }

    private static final List<BlockPos> splashed = new ArrayList<>();

    /** Throws a water bottle down onto a few solar fires near spawn (entities tick for 300 ticks after this, players or not). */
    private static void throwWater() {
        BlockPos spawn = world.getSpawnPoint();
        for (int dx = -40; dx <= 40 && splashed.size() < 3; dx += 4) {
            for (int dz = -40; dz <= 40 && splashed.size() < 3; dz += 4) {
                BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
                for (int y = spawn.getY() + 64; y > spawn.getY() - 64; y--) {
                    at.setPos(spawn.getX() + dx, y, spawn.getZ() + dz);
                    if (!world.isBlockLoaded(at) || !SolarFire.is(world.getBlockState(at))) continue;
                    BlockPos fire = at.toImmutable();
                    if (splashed.stream().anyMatch(q -> q.distanceSq(fire) < 64)) break;
                    EntityPotion bottle = new EntityPotion(world, fire.getX() + 0.5, fire.getY() + 2.5, fire.getZ() + 0.5,
                            PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.WATER));
                    bottle.motionY = -0.5;
                    world.spawnEntity(bottle);
                    splashed.add(fire);
                    break;
                }
            }
        }
        world.resetUpdateEntityTick();
        log("threw water bottles onto {} solar fires", splashed.size());
    }

    private static void jump(long progress, String what) {
        ApocalypseClock.set(progress);
        SolarApocalypse.requeueAll();
        log("--- jump to {} (day {}, depth {} layers), {} cubes queued", what, String.format("%.2f", progress / (double) Timeline.DAY),
                (long) SolarApocalypse.timeline().depthAt(progress, Timeline.SURFACE), CubeEngine.queued(world));
        engineNanos = CubeEngine.nanos;
        engineCubes = CubeEngine.cubesProcessed;
        engineBlocks = BlockChanges.changed;
        waited = ticks;
        stage++;
    }

    /** Sets the overworld's time of day (days ahead) and queues every loaded cube (the clock stands still: no players). */
    private static void timeOfDay(long timeOfDay, int daysAhead, String what) {
        world.setWorldTime((world.getWorldTime() / Timeline.DAY + daysAhead) * Timeline.DAY + timeOfDay);
        SolarApocalypse.requeueAll();
        log("--- time of day {} ({}), {} cubes queued", timeOfDay, what, CubeEngine.queued(world));
        engineNanos = CubeEngine.nanos;
        engineCubes = CubeEngine.cubesProcessed;
        engineBlocks = BlockChanges.changed;
        waited = ticks;
        stage++;
    }

    private static boolean drained(int maxTicks) {
        int queued = CubeEngine.queued(world);
        if (queued > 0 && ticks - waited < maxTicks) {
            if ((ticks - waited) % 100 == 0) log("  {} ticks: {} cubes queued", ticks - waited, queued);
            return false;
        }
        log("  caught up in {} ticks ({} queued left): {} cube passes, {} blocks changed, {} ms engine time", ticks - waited, queued,
                CubeEngine.cubesProcessed - engineCubes, BlockChanges.changed - engineBlocks,
                String.format("%.1f", (CubeEngine.nanos - engineNanos) / 1e6));
        return true;
    }

    private static void spawnPigs() {
        BlockPos spawn = world.getSpawnPoint();
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        int x = spawn.getX(), z = spawn.getZ();
        sunPig = pig(x, top(cubes, x, z) + 1, z);
        int rx = x + 4, ry = top(cubes, rx, z) + 1;
        world.setBlockState(new BlockPos(rx, ry + 3, z), Blocks.STONE.getDefaultState(), 18); // a one-block roof
        roofPig = pig(rx, ry, z);
        int dx = x + 8, dy = top(cubes, dx, z) - 24;
        world.setBlockState(new BlockPos(dx, dy, z), Blocks.AIR.getDefaultState(), 18);
        world.setBlockState(new BlockPos(dx, dy + 1, z), Blocks.AIR.getDefaultState(), 18);
        deepPig = pig(dx, dy, z);
        for (EntityPig pig : new EntityPig[]{sunPig, roofPig, deepPig}) {
            BlockPos eye = new BlockPos(pig.posX, pig.posY + pig.getEyeHeight(), pig.posZ);
            log("pig at {}: sun {}, heat {}", eye, Sky.at(world, eye), Sky.heat(world, eye));
        }
    }

    private static EntityPig pig(int x, int y, int z) {
        EntityPig pig = new EntityPig(world);
        pig.setLocationAndAngles(x + 0.5, y, z + 0.5, 0, 0);
        pig.enablePersistence();
        pig.setNoAI(true);
        world.spawnEntity(pig);
        return pig;
    }

    private static void pigs(String when) {
        for (EntityPig pig : new EntityPig[]{sunPig, roofPig, deepPig}) {
            BlockPos eye = new BlockPos(pig.posX, pig.posY + pig.getEyeHeight(), pig.posZ);
            log("  pig at {}: sun {} (1 = exposed), heat {}, day {}", eye, Sky.at(world, eye), Sky.heat(world, eye), world.isDaytime());
        }
        log("pigs after {}: sun {} hp burning={}, roof {} hp burning={}, deep {} hp burning={}", when,
                sunPig.getHealth(), sunPig.isBurning(), roofPig.getHealth(), roofPig.isBurning(), deepPig.getHealth(), deepPig.isBurning());
    }

    /** Stage 1 runs with the world's own tick 12 ms slow, more than the 10 ms budget: the engine must still get its time. */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (stage != 1 || event.phase != TickEvent.Phase.START || event.world != world) return;
        long until = System.nanoTime() + 12_000_000L;
        while (System.nanoTime() < until) Thread.yield();
    }

    /** A config with phases.count 2 and a leftover phase_5 section: no phase_3 or later is made, phase_5 stays with a note. */
    private static void configCheck() {
        java.io.File fixture = SolarConfig.file(), test = new java.io.File(fixture.getParentFile(), "solarapocalypse-configcheck.cfg");
        try {
            java.nio.file.Files.write(test.toPath(), java.util.Arrays.asList("phases {", "    I:count=2", "}", "phase_5 {", "    D:days=4.0", "}"));
            SolarConfig.init(test);
            String text = new String(java.nio.file.Files.readAllBytes(test.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            log("config with count 2: phase_1 {} (true), phase_2 {} (true), phase_3 {} (false), phase_11 {} (false), leftover phase_5"
                            + " kept {} (true) with its note {} (true)", text.contains("phase_1 {"), text.contains("phase_2 {"),
                    text.contains("phase_3 {"), text.contains("phase_11 {"), text.contains("phase_5 {"), text.contains("Not used: phases.count is 2"));
        } catch (java.io.IOException e) {
            log("config check failed: {}", e);
        } finally {
            SolarConfig.init(fixture); // back to the fixture
            test.delete();
        }
    }

    /** Chances, selector exclusions, loop cutting and the vanilla fire blacklist, on a throwaway two-phase rule set (CARRY). */
    private static void rulesCheck() {
        SolarConfig.Phase a = new SolarConfig.Phase(), b = new SolarConfig.Phase();
        a.convert = new String[]{"minecraft:dirt -> minecraft:sand", "minecraft:gravel -> minecraft:sand @ 30%",
                "minecraft:gravel -> minecraft:clay @ 20", "minecraft:stone_brick_stairs -> minecraft:stone_stairs preserveState",
                "minecraft:anvil[ damage=0 ] -> minecraft:anvil[damage=1] @ 25% preserveState", "minecraft:double_plant -> minecraft:deadbush"};
        a.destroy = new String[]{"material:rock, !minecraft:cobblestone"};
        b.convert = new String[]{"minecraft:sand -> minecraft:dirt"};
        b.destroy = new String[0];
        a.evaporate = new String[]{"material:water, !temperature>=1000"};
        b.evaporate = new String[]{"fluid:lava"};
        a.convert = append(a.convert, "minecraft:glowstone -> minecraft:cobblestone");
        b.convert = append(b.convert, "minecraft:glowstone -> minecraft:magma dimensions=-1");
        b.destroy = new String[]{"minecraft:soul_sand dimensions=0, -1"};
        BlockRules r = BlockRules.compile(new SolarConfig.Phase[]{a, b}, 0), nether = BlockRules.compile(new SolarConfig.Phase[]{a, b}, -1),
                end = BlockRules.compile(new SolarConfig.Phase[]{a, b}, 1);
        IBlockState glowstone = Blocks.GLOWSTONE.getDefaultState(), soulSand = Blocks.SOUL_SAND.getDefaultState();
        log("rules, dimensions: glowstone in phase 2 -> {} in the overworld (cobblestone: the Nether-only rule does not shadow"
                        + " phase 1's), {} in the Nether (magma); soul sand destroyed in phase {} overworld (1), {} Nether (1), {} End (-1)",
                to(r, 1, glowstone, 0, 0, 1), to(nether, 1, glowstone, 0, 0, 1), r.destroyPhase(1, soulSand), nether.destroyPhase(1, soulSand),
                end.destroyPhase(1, soulSand));
        SolarConfig.Phase melt = new SolarConfig.Phase();
        melt.convert = new String[]{"minecraft:sand -> solarapocalypse:vitrified_sand preserveState"};
        melt.destroy = new String[0];
        melt.evaporate = new String[0];
        IBlockState redSand = Blocks.SAND.getDefaultState().withProperty(BlockSand.VARIANT, BlockSand.EnumType.RED_SAND);
        IBlockState vitrified = to(BlockRules.compile(new SolarConfig.Phase[]{melt}, 0), 0, redSand, 0, 0, 1);
        log("rules: red sand -> {} (vitrified_sand, variant red_sand), which drops sand meta {} (1)", vitrified,
                vitrified == null ? -1 : vitrified.getBlock().damageDropped(vitrified));
        log("rules: evaporation from phase water {} (expect 0), flowing water {} (0), lava {} (1), flowing lava {} (1), stone {} (-1)",
                r.evaporationPhase(Blocks.WATER.getDefaultState()), r.evaporationPhase(Blocks.FLOWING_WATER.getDefaultState()),
                r.evaporationPhase(Blocks.LAVA.getDefaultState()), r.evaporationPhase(Blocks.FLOWING_LAVA.getDefaultState()),
                r.evaporationPhase(Blocks.STONE.getDefaultState()));
        IBlockState dirt = Blocks.DIRT.getDefaultState(), sand = Blocks.SAND.getDefaultState(), gravel = Blocks.GRAVEL.getDefaultState();
        BlockRules.Step d = r.convert(1, dirt);
        IBlockState s = to(r, 1, sand, 0, 0, 1);
        int toSand = 0, toClay = 0, same = 0;
        for (int i = 0; i < 10000; i++) {
            IBlockState to = to(r, 0, gravel, i, i * 7, 1);
            if (to == sand) toSand++;
            else if (to == Blocks.CLAY.getDefaultState()) toClay++;
            if (to == to(r, 0, gravel, i, i * 7, 1)) same++;
        }
        log("rules: gravel of 10000 blocks -> {} sand (expect ~3000), {} clay (~2000), same on a second look {} (10000);"
                        + " stone destroyed in phase {} (expect 0), cobblestone {} (-1); loop cut in phase 2: dirt -> {} (none),"
                        + " sand -> {} (dirt); TNT blacklisted for vanilla fire {} (true)", toSand, toClay, same,
                r.destroyPhase(0, Blocks.STONE.getDefaultState()), r.destroyPhase(0, Blocks.COBBLESTONE.getDefaultState()),
                d == null ? "none" : d, s == null ? "none" : s, r.noVanillaFire(Blocks.TNT.getDefaultState()));
        IBlockState stairs = Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        IBlockState anvil = Blocks.ANVIL.getDefaultState().withProperty(BlockAnvil.FACING, EnumFacing.WEST);
        IBlockState damaged = anvil.withProperty(BlockAnvil.DAMAGE, 1);
        int hits = 0;
        for (int i = 0; i < 10000; i++) if (to(r, 0, anvil, i, i * 7, 1) == damaged) hits++;
        IBlockState upper = Blocks.DOUBLE_PLANT.getDefaultState().withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.UPPER);
        log("rules: stairs -> {} (stone stairs, facing east, half top), anvil facing west -> {} in {} of 10000 (~2500), top half of"
                        + " a tall plant -> {} (none), bottom half -> {} (deadbush)", to(r, 0, stairs, 0, 0, 1), damaged, hits,
                r.convert(0, upper), to(r, 0, Blocks.DOUBLE_PLANT.getDefaultState(), 0, 0, 1));
    }

    /** Phase 5 of the fixture has stone -> cobblestone depth=3-4: after erosion, by layer below each column's top. */
    private static void layerCheck() {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int[] stone = new int[6], cobble = new int[6];
        for (int x = spawn.getX() - RADIUS * 16; x < spawn.getX() + RADIUS * 16; x++) {
            for (int z = spawn.getZ() - RADIUS * 16; z < spawn.getZ() + RADIUS * 16; z++) {
                Chunk column = cubes.getLoadedColumn(x >> 4, z >> 4);
                if (column == null) continue;
                int top = ((IColumn) column).getHeightValue(x & 15, z & 15) - 1;
                for (int layer = 1; layer <= 5; layer++) {
                    ICube cube = cubes.getLoadedCube(x >> 4, (top - layer + 1) >> 4, z >> 4);
                    if (cube == null || cube.getStorage() == null) continue;
                    Block b = cube.getStorage().get(x & 15, (top - layer + 1) & 15, z & 15).getBlock();
                    if (b == Blocks.STONE) stone[layer]++;
                    else if (b == Blocks.COBBLESTONE) cobble[layer]++;
                }
            }
        }
        StringBuilder s = new StringBuilder();
        for (int layer = 1; layer <= 5; layer++) s.append(layer).append(": ").append(stone[layer]).append('/').append(cobble[layer]).append("  ");
        log("depth-staged rule after erosion (stone -> cobblestone depth=3-4), stone/cobblestone by layer below the top: {}"
                + "(cobblestone only in layers 3 and 4)", s);
    }

    /** The user's staged example: each layer below the surface one stage further along (layer 1 = the surface block). */
    private static void depthCheck() {
        SolarConfig.Phase p = new SolarConfig.Phase();
        p.convertDepth = 1;
        p.convert = new String[]{"minecraft:grass -> minecraft:grass_path depth=5", "minecraft:grass_path -> minecraft:dirt depth=4",
                "minecraft:dirt -> minecraft:gravel depth=3", "minecraft:gravel -> minecraft:sand depth=2", "minecraft:sand -> minecraft:glass depth=1",
                "minecraft:stone -> minecraft:cobblestone depth=2-3"};
        p.destroy = new String[0];
        p.evaporate = new String[0];
        BlockRules r = BlockRules.compile(new SolarConfig.Phase[]{p}, 0);
        StringBuilder grass = new StringBuilder(), stone = new StringBuilder();
        for (int layer = 6; layer >= 1; layer--) {
            grass.append(layer).append(':').append(name(staged(r, 0, Blocks.GRASS.getDefaultState(), layer))).append(' ');
            stone.append(layer).append(':').append(name(staged(r, 0, Blocks.STONE.getDefaultState(), layer))).append(' ');
        }
        log("rules, depth: grass by layer {}(6 grass, 5 path, 4 dirt, 3 gravel, 2 sand, 1 glass); stone with depth=2-3 {}(only 2, 3"
                + " cobblestone); a grass block at layer 6 next looks at layer {} (5)", grass, stone, r.convert(0, Blocks.GRASS.getDefaultState()).nextLayer(6, 0));
    }

    /**
     * Carried rules per layer (the user's gradient): phase 1 converts grass step by step to vitrified sand at convertDepth 1,
     * phase 2 (convertDepth 3) has no rules of its own, phase 3 is infinite with layer= rules for layers 2 to 5.
     */
    private static void carryCheck() {
        SolarConfig.Phase a = phase(1, 0, "minecraft:grass -> minecraft:grass_path", "minecraft:grass_path -> minecraft:dirt",
                "minecraft:dirt -> minecraft:gravel", "minecraft:gravel -> minecraft:sand", "minecraft:sand -> solarapocalypse:vitrified_sand");
        SolarConfig.Phase b = phase(3, 0);
        SolarConfig.Phase c = phase(5, SolarConfig.INFINITE, "minecraft:grass_path -> solarapocalypse:vitrified_sand layer=2",
                "minecraft:grass -> minecraft:sand layer=2", "minecraft:dirt -> minecraft:sand layer=2", "minecraft:gravel -> minecraft:sand layer=2",
                "minecraft:grass_path -> minecraft:gravel layer=3", "minecraft:grass -> minecraft:gravel layer=3", "minecraft:dirt -> minecraft:gravel layer=3",
                "minecraft:grass_path -> minecraft:dirt layer=4", "minecraft:grass -> minecraft:dirt layer=4", "minecraft:grass -> minecraft:grass_path layer=5",
                "minecraft:clay -> minecraft:sandstone layer=1", "minecraft:sandstone -> minecraft:clay layer=2");
        BlockRules r = BlockRules.compile(new SolarConfig.Phase[]{a, b, c}, 0);
        IBlockState grass = Blocks.GRASS.getDefaultState();
        StringBuilder fresh = new StringBuilder(), stepped = new StringBuilder(), middle = new StringBuilder();
        IBlockState block = grass;
        for (int layer = 6; layer >= 1; layer--) {
            fresh.append(layer).append(':').append(name(staged(r, 2, grass, layer))).append(' ');
            block = staged(r, 2, block, layer); // the same block as the surface comes down
            stepped.append(layer).append(':').append(name(block)).append(' ');
        }
        for (int layer = 4; layer >= 1; layer--) middle.append(layer).append(':').append(name(staged(r, 1, grass, layer))).append(' ');
        BlockRules.Step step = r.convert(1, grass);
        IBlockState clay = Blocks.CLAY.getDefaultState(), sandstone = Blocks.SANDSTONE.getDefaultState();
        log("rules, carry: infinite phase, grass appearing in a layer {}(6 grass, 5 grass_path, 4 dirt, 3 gravel, 2 sand, 1"
                        + " vitrified_sand: the carried rules in layer 1 only); one grass block as the surface comes down {}(the same);"
                        + " phase 2 (convertDepth 3, no own rules) {}(4 grass, 3 2 1 vitrified_sand); timing of grass -> grass_path in"
                        + " phase 2: layer 1 phase {} (0), layer 3 phase {} (1); in phase 3 a grass block at layer 7 next looks at {} (5);"
                        + " clay layer=1 / sandstone layer=2: clay at 1 -> {} (sandstone), sandstone at 2 -> {} (clay), no loop warning",
                fresh, stepped, middle, step.timing(step.pick(0, 64, 0, grass, 1, 1), 1, 1), step.timing(step.pick(0, 64, 0, grass, 3, 1), 3, 1),
                r.convert(2, grass).nextLayer(7, 2), name(to(r, 2, clay, 0, 0, 1)), name(to(r, 2, sandstone, 0, 0, 2)));
    }

    private static SolarConfig.Phase phase(int convertDepth, int depth, String... convert) {
        SolarConfig.Phase p = new SolarConfig.Phase();
        p.convertDepth = convertDepth;
        p.depth = depth;
        p.convert = convert;
        p.destroy = new String[0];
        p.evaporate = new String[0];
        return p;
    }

    /** What a block becomes in a layer while a phase runs (one rule, the first look), or null if it stays. */
    private static IBlockState to(BlockRules r, int phase, IBlockState state, int x, int z, int layer) {
        BlockRules.Step step = r.convert(phase, state);
        int rule = step == null ? -1 : step.pick(x, 64, z, state, layer, phase);
        return rule < 0 ? null : step.target(rule);
    }

    private static String name(IBlockState state) {
        return state == null ? "null" : state.getBlock().getRegistryName().getPath();
    }

    /** A block followed through the conversion chain in one layer, as BlockChanges.evaluate does once everything is due. */
    private static IBlockState staged(BlockRules r, int phase, IBlockState state, int layer) {
        for (int n = 0; n < 16; n++) {
            IBlockState to = to(r, phase, state, 0, 0, layer);
            if (to == null) break;
            state = to;
        }
        return state;
    }

    private static String[] append(String[] list, String entry) {
        String[] out = java.util.Arrays.copyOf(list, list.length + 1);
        out[list.length] = entry;
        return out;
    }

    /** Turns grass under tall grass and under a sunflower into path: both pop off at once, and nothing drops. */
    private static void settleCheck() {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int y = Integer.MIN_VALUE; // in open air above the terrain, so every neighbour is air
        for (int dx = -8; dx <= -2; dx++) y = Math.max(y, top(cubes, spawn.getX() + dx, spawn.getZ() - 3) + 6);
        BlockPos a = new BlockPos(spawn.getX() - 3, y, spawn.getZ() - 3);
        BlockPos b = new BlockPos(spawn.getX() - 5, y, spawn.getZ() - 3);
        IBlockState grass = Blocks.GRASS.getDefaultState(), path = Blocks.GRASS_PATH.getDefaultState();
        world.setBlockState(a, grass, 18);
        world.setBlockState(a.up(), Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS), 18);
        world.setBlockState(b, grass, 18);
        world.setBlockState(b.up(), Blocks.DOUBLE_PLANT.getDefaultState(), 18);
        world.setBlockState(b.up(2), Blocks.DOUBLE_PLANT.getDefaultState().withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.UPPER), 18);
        // a wall torch on the side of the grass that becomes path (a path's sides hold nothing)
        world.setBlockState(a.east(), Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.EAST), 18);
        BlockPos c = new BlockPos(spawn.getX() - 7, y, spawn.getZ() - 3);
        IBlockState farmland = Blocks.FARMLAND.getDefaultState();
        world.setBlockState(c, farmland, 18);
        world.setBlockState(c.up(), Blocks.WHEAT.getDefaultState().withProperty(BlockCrops.AGE, 7), 18); // ripe: drops for sure
        BlockPos d = new BlockPos(spawn.getX() - 9, y, spawn.getZ() - 3); // tall grass on grass converted to a dead bush
        IBlockState lower = Blocks.DOUBLE_PLANT.getDefaultState().withProperty(BlockDoublePlant.VARIANT, BlockDoublePlant.EnumPlantType.GRASS);
        world.setBlockState(d, grass, 18);
        world.setBlockState(d.up(), lower, 18);
        world.setBlockState(d.up(2), lower.withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.UPPER), 18);
        AxisAlignedBB box = new AxisAlignedBB(b).union(new AxisAlignedBB(a)).union(new AxisAlignedBB(d)).grow(4);
        int before = world.getEntitiesWithinAABB(EntityItem.class, box).size();
        BlockChanges.apply(world, a, grass, path);
        BlockChanges.apply(world, b, grass, path);
        BlockChanges.apply(world, c, farmland, path);
        BlockChanges.apply(world, d.up(), lower, Blocks.DEADBUSH.getDefaultState());
        log("settle: tall grass on new path -> {} (air), sunflower -> {} / {} (air, air), wall torch on its side -> {} (air),"
                        + " ripe wheat on farmland turned to path -> {} (air), double tall grass on grass -> dead bush: {} / {} (air: a"
                        + " dead bush cannot stay on grass, air), items dropped {} (0)",
                world.getBlockState(a.up()).getBlock().getRegistryName(), world.getBlockState(b.up()).getBlock().getRegistryName(),
                world.getBlockState(b.up(2)).getBlock().getRegistryName(), world.getBlockState(a.east()).getBlock().getRegistryName(),
                world.getBlockState(c.up()).getBlock().getRegistryName(), world.getBlockState(d.up()).getBlock().getRegistryName(),
                world.getBlockState(d.up(2)).getBlock().getRegistryName(),
                world.getEntitiesWithinAABB(EntityItem.class, box).size() - before);
    }

    /** Counts solar fire (by epoch) and vanilla fire in the loaded spawn columns; returns the solar fire count. */
    private static int fire(String when) {
        CubeEngine.FireCensus c = CubeEngine.fireCensus(world, world.getSpawnPoint(), RADIUS);
        log("fire at {}: solar {} ({} % of {} x/z positions, by epoch {}), vanilla {}", when, c.solar,
                String.format("%.1f", c.solar * 100.0 / (c.columns * 256)), c.columns * 256, c.epochs, c.vanilla);
        return c.solar;
    }

    /** Compares the CubicWorldGen surface model with the generated ground of the loaded spawn columns. */
    private static void modelCheck() {
        CwgSurface model = CubicSky.model(world);
        if (model == null) return;
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int exact = 0, near = 0, lower = 0, higher = 0, unknown = 0, worst = 0;
        long t0 = System.nanoTime();
        int columns = 0;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                if (cubes.getLoadedColumn(cx, cz) == null) continue;
                columns++;
                for (int i = 0; i < 256; i++) model.top((cx << 4) + (i & 15), (cz << 4) + (i >> 4));
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                if (cubes.getLoadedColumn(cx, cz) == null) continue;
                for (int i = 0; i < 256; i++) {
                    int x = (cx << 4) + (i & 15), z = (cz << 4) + (i >> 4);
                    int ground = ground(cubes, x, z);
                    if (ground == Integer.MIN_VALUE) {
                        unknown++;
                        continue;
                    }
                    int d = ground - model.top(x, z);
                    if (d == 0) exact++;
                    else if (Math.abs(d) == 1) near++;
                    else if (d < 0) lower++;
                    else higher++;
                    if (d > 0) worst = Math.max(worst, d);
                }
            }
        }
        log("CWG surface model, {} columns in {} ms: ground = model {}, off by 1 {}, lower (caves, lakes) {}, higher {} (max +{}), unknown {}",
                columns, String.format("%.1f", ms), exact, near, lower, higher, worst, unknown);
    }

    /** Topmost ground block (BlockChanges.isGround) under the column's top, from loaded cubes; MIN_VALUE if not loaded. */
    private static int ground(ICubeProvider cubes, int x, int z) {
        for (int y = top(cubes, x, z), n = 0; n < 64; y--, n++) {
            ICube cube = cubes.getLoadedCube(x >> 4, y >> 4, z >> 4);
            if (cube == null) return Integer.MIN_VALUE;
            ExtendedBlockStorage storage = cube.getStorage();
            if (storage != null && BlockChanges.isGround(storage.get(x & 15, y & 15, z & 15))) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** After erosion to `depth` layers: counts blocks left above reference - depth + 1 in the loaded spawn columns. */
    /** Checks that nothing is left at or above a line (lowest Y that must be gone per x, z) in the ready spawn cubes. */
    private static void lineCheck(String what, IntBinaryOperator lowestGone) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        int left = 0, checked = 0, noReference = 0, cubesSeen = 0, highest = Integer.MIN_VALUE;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                Chunk column = cubes.getLoadedColumn(cx, cz);
                if (column == null) continue;
                for (ICube cube : new ArrayList<>(((IColumn) column).getLoadedCubes())) {
                    ExtendedBlockStorage storage = cube.getStorage();
                    if (storage == null || storage.isEmpty() || !cube.isSurfaceTracked()) continue;
                    cubesSeen++;
                    for (int i = 0; i < 4096; i++) {
                        if (storage.get(i & 15, i >> 8, (i >> 4) & 15).getMaterial() != Material.AIR) highest = Math.max(highest, cube.getY() * 16 + (i >> 8));
                    }
                    for (int i = 0; i < 256; i++) {
                        int x = (cx << 4) + (i & 15), z = (cz << 4) + (i >> 4);
                        int gone = lowestGone.applyAsInt(x, z);
                        if (gone == BlockChanges.NO_Y) {
                            noReference++;
                            continue;
                        }
                        for (int ly = 0; ly < 16; ly++) {
                            int y = cube.getY() * 16 + ly;
                            if (y < gone) continue;
                            checked++;
                            IBlockState left0 = storage.get(i & 15, ly, i >> 4);
                            if (left0.getMaterial() != Material.AIR && !SolarFire.is(left0)) left++; // solar fire stands on the surface
                        }
                    }
                }
            }
        }
        log("line check, {}: {} positions above the line in {} ready cubes (highest block Y {}), {} still hold a block, {} without reference",
                what, checked, cubesSeen, highest, left, noReference);
    }

    /** The highest terrain surface (SURFACE reference) among the spawn columns. */
    private static int highestGround() {
        BlockPos spawn = world.getSpawnPoint();
        int highest = Integer.MIN_VALUE;
        for (int x = spawn.getX() - RADIUS * 16; x < spawn.getX() + RADIUS * 16; x++) {
            for (int z = spawn.getZ() - RADIUS * 16; z < spawn.getZ() + RADIUS * 16; z++) highest = Math.max(highest, CubeEngine.groundAt(world, x, z));
        }
        return highest;
    }

    private static int top(ICubeProvider cubes, int x, int z) {
        Chunk column = cubes.getLoadedColumn(x >> 4, z >> 4);
        return ((IColumn) column).getHeightValue(x & 15, z & 15) - 1;
    }

    /** Counts block kinds in the loaded cubes of the columns around spawn: total, and sun-exposed (at or above the top). */
    private static void census(String when) {
        ICubeProvider cubes = ((ICubicWorld) world).getCubeCache();
        BlockPos spawn = world.getSpawnPoint();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (String k : new String[]{"grass", "dirt", "sand", "water", "lava", "ice/snow", "plants", "leaves", "wood", "stone", "other"}) {
            counts.put(k, new int[2]);
        }
        int columns = 0, cubeCount = 0, highest = Integer.MIN_VALUE, lowestTop = Integer.MAX_VALUE;
        for (int cx = (spawn.getX() >> 4) - RADIUS; cx <= (spawn.getX() >> 4) + RADIUS; cx++) {
            for (int cz = (spawn.getZ() >> 4) - RADIUS; cz <= (spawn.getZ() >> 4) + RADIUS; cz++) {
                Chunk column = cubes.getLoadedColumn(cx, cz);
                if (column == null) continue;
                columns++;
                IColumn heights = (IColumn) column;
                List<ICube> loaded = new ArrayList<>(heights.getLoadedCubes());
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) lowestTop = Math.min(lowestTop, heights.getHeightValue(lx, lz) - 1);
                for (ICube cube : loaded) {
                    ExtendedBlockStorage storage = cube.getStorage();
                    cubeCount++;
                    if (storage == null || storage.isEmpty()) continue;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int top = heights.getHeightValue(lx, lz) - 1;
                            for (int ly = 0; ly < 16; ly++) {
                                IBlockState s = storage.get(lx, ly, lz);
                                if (s.getMaterial() == Material.AIR) continue;
                                int y = cube.getY() * 16 + ly;
                                highest = Math.max(highest, y);
                                int[] c = counts.get(kind(s));
                                c[0]++;
                                if (y >= top) c[1]++;
                            }
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            if (e.getValue()[0] > 0) sb.append(e.getKey()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]).append(", ");
        }
        log("census {}: {} columns, {} cubes, highest block Y {}, lowest top Y {}; total/exposed: {}", when, columns, cubeCount,
                highest, lowestTop, sb);
    }

    private static String kind(IBlockState s) {
        Material m = s.getMaterial();
        if (s.getBlock() == Blocks.GRASS) return "grass";
        if (s.getBlock() == Blocks.DIRT) return "dirt";
        if (m == Material.SAND) return "sand";
        if (m == Material.WATER) return "water";
        if (m == Material.LAVA) return "lava";
        if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.SNOW || m == Material.CRAFTED_SNOW) return "ice/snow";
        if (m == Material.PLANTS || m == Material.VINE) return "plants";
        if (m == Material.LEAVES) return "leaves";
        if (m == Material.WOOD) return "wood";
        if (m == Material.ROCK) return "stone";
        return "other";
    }
}
