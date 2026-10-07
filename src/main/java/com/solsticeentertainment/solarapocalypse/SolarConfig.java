package com.solsticeentertainment.solarapocalypse;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * config/solarapocalypse.cfg. Loaded once at pre-init and again by "/solar reload". Every value is read into these
 * fields; the rest of the mod never touches the Configuration object.
 */
public final class SolarConfig {

    public enum ClockMode { SUN, TICKS }
    public enum Scaling { CONSTANT, LINEAR, QUADRATIC, EXPONENTIAL }
    public enum Speed { PHASE, RATE, INSTANT }
    public enum EvaporationMode { INSTANT, LAYERS }
    public enum FireResistance { NONE, DIRECT, BACKGROUND, BOTH }
    public enum DepthReference { SURFACE, TOP_Y }
    public enum RuleMode { CARRY, ISOLATED }

    public static final int INFINITE = -1;
    public static final int AUTO = Integer.MIN_VALUE;

    public static final class Phase {
        public double days;
        public int depth;            // layers below the reference surface reached by the end of this phase; INFINITE = no end
        public Speed speed;
        public double layersPerDay;  // RATE speed, and the descent rate once the depth is infinite
        public String[] convert;
        public int convertDepth;     // conversions reach this many layers from the column's current surface
        public String[] destroy;
        public double sunDamage;
        public int sunFireSeconds;
        public double backgroundDamage;
        public int backgroundFireSeconds;
    }

    private static File file;

    // clock
    public static ClockMode clockMode;
    public static int ticksPerDay;
    public static int maxSunJump;
    public static boolean progressWhileEmpty;
    // phases
    public static double safeDays;
    public static double baseDays;
    public static Scaling scaling;
    public static double scalingFactor;
    public static RuleMode ruleMode;
    public static Phase[] phases;
    // world
    public static Set<Integer> dimensions;
    public static DepthReference depthReference;
    public static int topY;
    public static int surfaceMargin;
    public static int skyClearance;
    public static int skyCeilingY;
    public static int sunFloorY;
    // blocks
    public static boolean dropItems;
    public static boolean blockPhysics;
    // evaporation
    public static int waterPhase;
    public static int lavaPhase;
    public static int otherLiquidsPhase;
    public static EvaporationMode evaporationMode;
    public static double evaporationLayersPerDay;
    public static int evaporationTopY;
    // entities
    public static int damageIntervalTicks;
    public static boolean sunNeedsDaytime;
    public static boolean affectPlayers;
    public static boolean spareFireImmune;
    public static FireResistance fireResistance;
    public static int backgroundMinSkyLight;
    public static Set<String> entityBlacklist;
    // performance
    public static double tickBudgetMs;
    public static double lagThresholdMs;

    private SolarConfig() {}

    public static void init(File configFile) {
        file = configFile;
        load();
    }

    public static void load() {
        Configuration c = new Configuration(file);
        c.load();

        String cat = "clock";
        c.setCategoryComment(cat, "How apocalypse time advances. 1 day = 24000 progress units.");
        clockMode = enumValue(c, cat, "mode", ClockMode.SUN,
                "SUN: count the overworld's sun clock (worldTime) moving forward, so a day lasts as long as the sun takes, with any\n" +
                "day-length mod, and sleeping counts. Stops while doDaylightCycle is false.\n" +
                "TICKS: count server ticks; one day = ticksPerDay ticks.");
        ticksPerDay = c.getInt("ticksPerDay", cat, 24000, 1, Integer.MAX_VALUE, "TICKS mode: server ticks per apocalypse day.");
        maxSunJump = c.getInt("maxSunJump", cat, 24000, 1, Integer.MAX_VALUE,
                "SUN mode: a single-tick forward jump of worldTime larger than this is ignored (/time set, /time add).\n" +
                "Sleeping skips less than one day, so it still counts.");
        progressWhileEmpty = c.getBoolean("progressWhileEmpty", cat, false,
                "true: time runs while no player is online and the changes are caught up when terrain loads.\n" +
                "false: the apocalypse pauses while the server is empty.");

        cat = "phases";
        c.setCategoryComment(cat, "Phase timing. Each phase has its own section phase_1, phase_2, ...");
        safeDays = c.get(cat, "safeDays", 3.0, "Phase 0: days before the apocalypse starts.").getDouble();
        int count = c.getInt("count", cat, 5, 1, 1000, "Number of phases. Sections for new phases are created with empty effects.");
        baseDays = c.get(cat, "baseDays", 3.0, "Length of a phase whose own 'days' is 0.").getDouble();
        scaling = enumValue(c, cat, "scaling", Scaling.CONSTANT,
                "Length of phase n (when its own 'days' is 0): CONSTANT baseDays, LINEAR baseDays*n, QUADRATIC baseDays*n*n,\n" +
                "EXPONENTIAL baseDays*scalingFactor^(n-1).");
        scalingFactor = c.get(cat, "scalingFactor", 1.5, "EXPONENTIAL scaling factor.").getDouble();
        ruleMode = enumValue(c, cat, "ruleMode", RuleMode.CARRY,
                "CARRY: the convert and destroy rules of every phase so far apply; for a block several phases convert, the latest\n" +
                "phase's rule wins. Terrain loaded late then looks like terrain that lived through every phase.\n" +
                "ISOLATED: only the running phase's rules apply.\n" +
                "Either way a chain of rules (grass -> dirt, dirt -> sand) is followed to its end.");

        cat = "world";
        c.setCategoryComment(cat, "Where the apocalypse runs and how the sun's reach is measured.");
        dimensions = new HashSet<>();
        for (int d : c.get(cat, "dimensions", new int[]{0}, "Dimension ids the apocalypse affects.").getIntList()) dimensions.add(d);
        depthReference = enumValue(c, cat, "depthReference", DepthReference.SURFACE,
                "What phase depths count down from (layer 1 is the reference itself, layer 2 the block below, ...):\n" +
                "SURFACE: each column's own terrain surface, so every column loses the same thickness at any altitude. Taken\n" +
                "from the generator in CubicWorldGen worlds, otherwise recorded when the column is first seen (ignoring trees).\n" +
                "TOP_Y: the fixed Y topY for every column (mountains go first, valleys when the depth gets down to them).\n" +
                "Blocks above the reference count as reached from phase 1 on.");
        topY = intOrAuto(c, cat, "topY", "auto",
                "TOP_Y reference. 'auto': in Cubic Chunks worlds the height the world's generator reports (CubicWorldGen: the\n" +
                "preset's estimated terrain height), otherwise the world height.");
        surfaceMargin = c.getInt("surfaceMargin", cat, 48, 0, 1 << 20,
                "CubicWorldGen worlds: blocks above the generator's terrain surface that trees and structures can reach. Above\n" +
                "surface + margin nothing generated can cover the sun.");
        skyClearance = c.getInt("skyClearance", cat, 32, 0, 1 << 20,
                "Cubic Chunks: a position only counts as sun-exposed when the cubes up to this many blocks above it are loaded\n" +
                "(or above skyCeilingY, or above the CubicWorldGen surface + surfaceMargin). Never-generated cubes count as air in\n" +
                "Cubic Chunks, so without this, caves deep underground near the top of the loaded area would count as open sky.");
        skyCeilingY = intOrAuto(c, cat, "skyCeilingY", "none",
                "Cubic Chunks: no terrain exists above this Y; positions above it are exposed unless something loaded covers them.\n" +
                "'none' disables it.", "none");
        sunFloorY = intOrAuto(c, cat, "sunFloorY", "none",
                "Positions below this Y are always shaded (for mobs and for exposure-based block effects). 'none' disables it.", "none");

        cat = "blocks";
        c.setCategoryComment(cat, "How block changes are applied.");
        dropItems = c.getBoolean("dropItems", cat, false, "Destroyed blocks drop their items. Container contents never drop when false.");
        blockPhysics = c.getBoolean("blockPhysics", cat, false,
                "Changes notify neighbours: sand and gravel fall, liquids flow into removed space. In Cubic Chunks this can\n" +
                "generate neighbouring cubes at cube edges.");

        cat = "evaporation";
        c.setCategoryComment(cat, "Sun-exposed liquids vanish from these phases on (0 = never).");
        waterPhase = c.getInt("waterPhase", cat, 1, 0, 1000, "First phase that evaporates water.");
        lavaPhase = c.getInt("lavaPhase", cat, 1, 0, 1000, "First phase that evaporates lava.");
        otherLiquidsPhase = c.getInt("otherLiquidsPhase", cat, 1, 0, 1000, "First phase that evaporates other (modded) liquids.");
        evaporationMode = enumValue(c, cat, "mode", EvaporationMode.INSTANT,
                "INSTANT: every exposed liquid block goes as soon as its phase starts (spread over ticks by the time budget).\n" +
                "LAYERS: a level descends from topY at layersPerDay; exposed liquid above it goes.");
        evaporationLayersPerDay = c.get(cat, "layersPerDay", 24.0, "LAYERS mode: layers per day.").getDouble();
        evaporationTopY = intOrAuto(c, cat, "topY", "auto", "LAYERS mode: Y the level starts at. 'auto': the world's sea level.");

        cat = "entities";
        c.setCategoryComment(cat, "Sun damage. Amounts per phase are in the phase sections.");
        damageIntervalTicks = c.getInt("intervalTicks", cat, 20, 1, 72000, "Damage is dealt every this many ticks.");
        sunNeedsDaytime = c.getBoolean("sunNeedsDaytime", cat, true, "Direct sun damage only while the world counts it as day.");
        affectPlayers = c.getBoolean("affectPlayers", cat, true, "Players take damage (never in creative or spectator).");
        spareFireImmune = c.getBoolean("spareFireImmune", cat, true, "Fire-immune mobs (blazes, magma cubes...) take no damage.");
        fireResistance = enumValue(c, cat, "fireResistance", FireResistance.NONE,
                "What Fire Resistance protects from: NONE, DIRECT (sun), BACKGROUND (heat), BOTH.");
        backgroundMinSkyLight = c.getInt("backgroundMinSkyLight", cat, 1, 0, 15,
                "Background heat reaches mobs wherever the sky light is at least this (under trees and overhangs, in houses with\n" +
                "openings), day and night. Sealed rooms and caves without sky light are safe.");
        entityBlacklist = new HashSet<>(Arrays.asList(c.getStringList("blacklist", cat, new String[0],
                "Entity ids that never take sun damage, e.g. minecraft:villager_golem.")));

        cat = "performance";
        tickBudgetMs = c.get(cat, "tickBudgetMs", 10.0, "Milliseconds per server tick spent changing blocks.").getDouble();
        lagThresholdMs = c.get(cat, "lagThresholdMs", 45.0,
                "When the average server tick takes longer than this, block changes run at most 1 ms per tick.").getDouble();

        phases = new Phase[count];
        for (int i = 0; i < count; i++) phases[i] = loadPhase(c, i + 1);

        if (c.hasChanged()) c.save();
    }

    private static Phase loadPhase(Configuration c, int n) {
        String cat = "phase_" + n;
        Defaults d = Defaults.of(n);
        Phase p = new Phase();
        p.days = c.get(cat, "days", 0.0, "Length in days; 0 = phases.baseDays with phases.scaling.").getDouble();
        String depth = c.getString("depth", cat, d.depth,
                "Layers below the reference (world.depthReference) destroyed by the end of this phase (whole number), or 'infinite'.\n" +
                "Never lower than an earlier phase's; once infinite, every later phase is infinite.");
        p.depth = "infinite".equalsIgnoreCase(depth.trim()) ? INFINITE : parseInt(depth, 0);
        p.speed = enumValue(c, cat, "speed", d.speed,
                "How the depth is reached: PHASE over the whole phase, RATE at layersPerDay (the phase lasts until the layers are\n" +
                "done), INSTANT at the phase start. Conversions run once the phase's destruction is done, at random but fixed\n" +
                "moments over the rest of the phase (INSTANT: at once); in an infinite phase they run from its start.");
        p.layersPerDay = c.get(cat, "layersPerDay", d.layersPerDay, "RATE speed, and infinite depth: layers per day.").getDouble();
        p.convert = c.getStringList("convert", cat, d.convert,
                "Block conversions of the surface layer (see convertDepth).\n" +
                "Format: <selector> -> <block>. Selectors: modid:name, modid:name:meta, modid:*, #oreDictName,\n" +
                "material:<name>, * (any breakable block). Target: modid:name, modid:name:meta or air.\n" +
                "Within a phase the first matching rule wins; phases.ruleMode decides whether earlier phases' rules still apply.");
        p.convertDepth = c.getInt("convertDepth", cat, 1, 1, 1 << 20,
                "Conversions reach the top this many layers of each column's current surface (1 = the top block, plus plants,\n" +
                "snow layers and the like on it). Converting a block to air makes the block below the new surface.");
        p.destroy = c.getStringList("destroy", cat, d.destroy,
                "Selectors of blocks removed down to the phase's depth (erosion). * = every breakable block.");
        p.sunDamage = c.get(cat, "sunDamage", d.sunDamage, "Damage to mobs in direct sunlight per interval (2 = one heart).").getDouble();
        p.sunFireSeconds = c.getInt("sunFireSeconds", cat, d.sunFire, 0, 3600, "Seconds mobs in direct sunlight are set on fire.");
        p.backgroundDamage = c.get(cat, "backgroundDamage", d.backgroundDamage,
                "Damage per interval to mobs wherever sky light reaches (entities.backgroundMinSkyLight), sun or not.").getDouble();
        p.backgroundFireSeconds = c.getInt("backgroundFireSeconds", cat, 0, 0, 3600, "Seconds background heat sets mobs on fire.");
        return p;
    }

    /** Defaults for a fresh config: five phases, leaves and water before wood, erosion last. Phases above 5 are empty. */
    private static final class Defaults {
        String depth = "0";
        Speed speed = Speed.PHASE;
        double layersPerDay = 16;
        String[] convert = new String[0];
        String[] destroy = new String[0];
        double sunDamage, backgroundDamage;
        int sunFire;

        static Defaults of(int n) {
            Defaults d = new Defaults();
            switch (n) {
                case 1:
                    d.convert = new String[]{
                            "minecraft:grass -> minecraft:dirt", "minecraft:mycelium -> minecraft:dirt",
                            "minecraft:farmland -> minecraft:dirt", "minecraft:grass_path -> minecraft:dirt",
                            "material:plants -> air", "material:vine -> air", "material:cactus -> air",
                            "material:snow -> air", "material:crafted_snow -> air", "material:ice -> air", "material:packed_ice -> air"};
                    d.sunFire = 2;
                    break;
                case 2:
                    d.convert = new String[]{"material:leaves -> air", "material:gourd -> air", "material:web -> air"};
                    d.sunDamage = 1;
                    d.sunFire = 4;
                    break;
                case 3:
                    d.convert = new String[]{"material:wood -> air", "material:cloth -> air", "material:carpet -> air",
                            "material:tnt -> air", "minecraft:dirt -> minecraft:sand"};
                    d.sunDamage = 2;
                    d.sunFire = 6;
                    d.backgroundDamage = 0.5;
                    break;
                case 4:
                    d.convert = new String[]{"minecraft:clay -> minecraft:hardened_clay", "minecraft:gravel -> minecraft:sand"};
                    d.sunDamage = 4;
                    d.sunFire = 8;
                    d.backgroundDamage = 1;
                    break;
                case 5:
                    d.depth = "infinite";
                    d.speed = Speed.RATE;
                    d.destroy = new String[]{"*"};
                    d.sunDamage = 8;
                    d.sunFire = 10;
                    d.backgroundDamage = 2;
                    break;
                default:
            }
            return d;
        }
    }

    private static <E extends Enum<E>> E enumValue(Configuration c, String cat, String key, E def, String comment) {
        E[] values = def.getDeclaringClass().getEnumConstants();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        String s = c.getString(key, cat, def.name(), comment, names).trim().toUpperCase(Locale.ROOT);
        for (E e : values) if (e.name().equals(s)) return e;
        SolarApocalypse.LOGGER.warn("Config {}.{}: unknown value '{}', using {}", cat, key, s, def);
        return def;
    }

    private static int intOrAuto(Configuration c, String cat, String key, String def, String comment) {
        return intOrAuto(c, cat, key, def, comment, "auto");
    }

    private static int intOrAuto(Configuration c, String cat, String key, String def, String comment, String word) {
        String s = c.getString(key, cat, def, comment).trim();
        return s.equalsIgnoreCase(word) ? AUTO : parseInt(s, AUTO);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            SolarApocalypse.LOGGER.warn("Config: '{}' is not a whole number", s);
            return fallback;
        }
    }
}
