package com.solsticeentertainment.solarapocalypse;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public enum Weather { UNCHANGED, NONE, RAIN, THUNDER }

    public static final int INFINITE = -1;
    public static final int AUTO = Integer.MIN_VALUE;

    public static final class Phase {
        public double days;
        public int depth;            // layers below the reference surface reached by the end of this phase; INFINITE = no end
        public Speed speed;
        public double layersPerDay;  // RATE speed, and the descent rate once the depth is infinite
        public String[] convert;
        public int convertDepth;     // conversions reach this many layers from the column's current surface
        public double convertDays;   // conversions spread over this after destruction; -1 = rest of the phase's days
        public double ignitePercent, igniteFlammablePercent;
        public String[] destroy;
        public String[] evaporate;
        public double sunDamage;
        public int sunFireSeconds;
        public double backgroundDamage;
        public int backgroundFireSeconds;
        public String message, sound, splash;
        public DepthReference depthReference; // null = the previous phase's (phase 1: world.depthReference)
        public Weather weather;               // null (INHERIT) until load resolves it
    }

    private static File file;

    // clock
    public static ClockMode clockMode;
    public static int ticksPerDay;
    public static boolean progressWhileEmpty;
    // phases
    public static double safeDays;
    public static double baseDays;
    public static Scaling scaling;
    public static double scalingFactor;
    public static RuleMode convertRuleMode, destroyRuleMode;
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
    public static String[] vanillaFireBlacklist;
    public static boolean nightDousesFire;
    public static int skipBoostSeconds, skipMaxBlockChangesPerTick;
    public static double skipTickBudgetMs;
    // evaporation
    public static EvaporationMode evaporationMode;
    public static double evaporationLayersPerDay;
    public static int evaporationTopY;
    // entities
    public static int damageIntervalTicks;
    public static boolean sunNeedsDaytime;
    public static int sunAtNightFromPhase;
    public static boolean affectPlayers;
    public static boolean spareFireImmune;
    public static FireResistance fireResistance;
    public static int backgroundMinSkyLight;
    public static Set<String> entityBlacklist;
    // performance
    public static double tickBudgetMs;
    public static double freeTickShare;
    public static int maxBlockChangesPerTick;
    // splash
    public static int splashTitleColor, splashFlickerColor, splashMessageColor;
    public static int splashFadeInTicks, splashStayTicks, splashFadeOutTicks;
    public static String splashTitleFont, splashMessageFont;

    private SolarConfig() {}

    public static void init(File configFile) {
        file = configFile;
        load();
    }

    public static File file() {
        return file;
    }

    public static void load() {
        Tracked c = new Tracked(file);
        numberedCategoriesInOrder(c);
        c.load();

        String cat = "clock";
        c.setCategoryComment(cat, "How apocalypse time advances. 1 day = 24000 progress units.");
        clockMode = enumValue(c, cat, "mode", ClockMode.SUN,
                "SUN: count the overworld's sun clock (worldTime) moving forward, so a day lasts as long as the sun takes, with any\n" +
                "day-length mod. Skipped time counts: sleeping, /time add, and /time set (as the skip forward to that time of day;\n" +
                "setting the time of day back by less than 100 counts nothing). Stops while doDaylightCycle is false.\n" +
                "TICKS: count server ticks; one day = ticksPerDay ticks.");
        ticksPerDay = c.getInt("ticksPerDay", cat, 24000, 1, Integer.MAX_VALUE, "TICKS mode: server ticks per apocalypse day.");
        progressWhileEmpty = c.getBoolean("progressWhileEmpty", cat, false,
                "true: time runs while no player is online and the changes are caught up when terrain loads.\n" +
                "false: the apocalypse pauses while the server is empty.");

        cat = "phases";
        c.setCategoryComment(cat, "Phase timing. Each phase has its own section phase_1, phase_2, ...");
        safeDays = c.get(cat, "safeDays", 1.0, "Phase 0: days before the apocalypse starts.").getDouble();
        int count = c.getInt("count", cat, Defaults.COUNT, 1, 1000,
                "Number of phases. Sections for new phases are created with empty effects.");
        baseDays = c.get(cat, "baseDays", 2.0, "Length of a phase whose own 'days' is 0.").getDouble();
        scaling = enumValue(c, cat, "scaling", Scaling.CONSTANT,
                "Length of phase n (when its own 'days' is 0): CONSTANT baseDays, LINEAR baseDays*n, QUADRATIC baseDays*n*n,\n" +
                "EXPONENTIAL baseDays*scalingFactor^(n-1).");
        scalingFactor = c.get(cat, "scalingFactor", 1.5, "EXPONENTIAL scaling factor.").getDouble();
        convertRuleMode = enumValue(c, cat, "convertRuleMode", RuleMode.CARRY,
                "CARRY: the convert rules of every phase so far apply; for a block several phases convert, the latest phase's\n" +
                "rules acting in its layer win. Terrain loaded late then looks like terrain that lived through every phase.\n" +
                "ISOLATED: only the running phase's rules apply.\n" +
                "Either way a chain of rules (grass -> dirt, dirt -> sand) is followed to its end.");
        destroyRuleMode = enumValue(c, cat, "destroyRuleMode", RuleMode.CARRY,
                "CARRY: the destroy lists of every phase so far apply. ISOLATED: only the running phase's list.");

        cat = "world";
        c.setCategoryComment(cat, "Where the apocalypse runs and how the sun's reach is measured.");
        dimensions = new HashSet<>();
        for (int d : c.get(cat, "dimensions", new int[]{0}, "Dimension ids the apocalypse affects.").getIntList()) dimensions.add(d);
        depthReference = enumValue(c, cat, "depthReference", DepthReference.SURFACE,
                "What phase depths count down from (layer 1 is the reference itself, layer 2 the block below, ...):\n" +
                "SURFACE: each column's own terrain surface, so every column loses the same thickness at any altitude. Taken\n" +
                "from the generator in CubicWorldGen worlds, otherwise recorded when the column is first seen (ignoring trees).\n" +
                "TOP_Y: the fixed Y topY for every column (mountains go first, valleys when the depth gets down to them).\n" +
                "Blocks above the reference count as reached from phase 1 on. Phases can switch with their own depthReference.");
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
        dropItems = c.getBoolean("dropItems", cat, false,
                "Destroyed blocks drop their items, and so do blocks that pop off a changed block (plants, crops, torches...).\n" +
                "Container contents never drop when false.");
        blockPhysics = c.getBoolean("blockPhysics", cat, false,
                "Changes notify neighbours: sand and gravel fall, liquids flow into removed space. In Cubic Chunks this can\n" +
                "generate neighbouring cubes at cube edges.");
        vanillaFireBlacklist = c.getStringList("vanillaFireBlacklist", cat, new String[]{"minecraft:tnt"},
                "Flammable blocks that never get vanilla fire from the sun (selectors, as in the phases' destroy lists); they are\n" +
                "treated like other blocks (solar fire, which never lights anything). With the gamerule doFireTick false no block\n" +
                "gets vanilla fire.");
        nightDousesFire = c.getBoolean("nightDousesFire", cat, false,
                "true: night puts the sun's fire out. Each solar fire goes out at its own moment over the sunset (time of day\n" +
                "12000-14000) and the same spots light again over the sunrise (23000-1000); no new fire is lit at night (vanilla\n" +
                "fire already burning is left to vanilla). false: fire, once lit, stays (infinite phases redraw it on every layer).");

        cat = "evaporation";
        c.setCategoryComment(cat, "How sun-exposed liquids vanish. Which liquids, from which phase: each phase's evaporate list.");
        evaporationMode = enumValue(c, cat, "mode", EvaporationMode.LAYERS,
                "INSTANT: every exposed liquid block goes as soon as its phase starts (spread over ticks by the time budget).\n" +
                "LAYERS: from its phase's start, a level descends from topY at layersPerDay; exposed liquid above it goes.");
        evaporationLayersPerDay = c.get(cat, "layersPerDay", 4.0, "LAYERS mode: layers per day.").getDouble();
        evaporationTopY = intOrAuto(c, cat, "topY", "auto", "LAYERS mode: Y the level starts at. 'auto': the world's sea level.");

        cat = "entities";
        c.setCategoryComment(cat, "Sun damage. Amounts per phase are in the phase sections.");
        damageIntervalTicks = c.getInt("intervalTicks", cat, 20, 1, 72000, "Damage is dealt every this many ticks.");
        sunNeedsDaytime = c.getBoolean("sunNeedsDaytime", cat, true, "Direct sun damage only while the world counts it as day.");
        sunAtNightFromPhase = c.getInt("sunAtNightFromPhase", cat, 7, 0, 1000,
                "With sunNeedsDaytime: from this phase on, direct sun damage happens at night too. 0 = never.");
        affectPlayers = c.getBoolean("affectPlayers", cat, true, "Players take damage (never in creative or spectator).");
        spareFireImmune = c.getBoolean("spareFireImmune", cat, false, "Fire-immune mobs (blazes, magma cubes...) take no damage.");
        fireResistance = enumValue(c, cat, "fireResistance", FireResistance.DIRECT,
                "What Fire Resistance protects from: NONE, DIRECT (sun), BACKGROUND (heat), BOTH.");
        backgroundMinSkyLight = c.getInt("backgroundMinSkyLight", cat, 4, 0, 15,
                "Background heat reaches mobs wherever the sky light is at least this (under trees and overhangs, in houses with\n" +
                "openings), day and night. Sealed rooms and caves without sky light are safe.");
        entityBlacklist = new HashSet<>(Arrays.asList(c.getStringList("blacklist", cat, new String[0],
                "Entity ids that never take sun damage, e.g. minecraft:villager_golem.")));

        cat = "performance";
        tickBudgetMs = c.get(cat, "tickBudgetMs", 4.0, "At most this many milliseconds per server tick are spent changing blocks.").getDouble();
        freeTickShare = c.get(cat, "freeTickShare", 0.5,
                "...and at most this share of the time left in a 50 ms tick by everything else (average of the last 100 ticks),\n" +
                "so a busy server slows the apocalypse down instead of lagging. At least 0.5 ms per tick always runs.").getDouble();
        maxBlockChangesPerTick = c.getInt("maxBlockChangesPerTick", cat, 512, 0, Integer.MAX_VALUE,
                "...and at most this many block changes per server tick, which caps what clients are sent and must redraw.\n" +
                "0 = no limit.");
        skipBoostSeconds = c.getInt("skipBoostSeconds", cat, 30, 0, 3600,
                "After a /time set or /time add skip (not sleeping, which keeps the budget above), the engine catches up with the\n" +
                "bigger budget below for this many seconds: faster, with some lag. 0 = no boost.");
        skipTickBudgetMs = c.get(cat, "skipTickBudgetMs", 20.0, "Milliseconds per server tick during that boost.").getDouble();
        skipMaxBlockChangesPerTick = c.getInt("skipMaxBlockChangesPerTick", cat, 2048, 0, Integer.MAX_VALUE,
                "Block changes per server tick during that boost (0 = no limit).");

        cat = "splash";
        c.setCategoryComment(cat, "The splash title of a phase (phase_n.splash) on every player's screen. Text in a phase's message\n" +
                "and splash can use & formatting codes (&c red, &l bold...).");
        splashTitleColor = color(c, cat, "titleColor", "FF6A00", "Title colour (hex RGB).");
        splashFlickerColor = color(c, cat, "flickerColor", "FFC21F", "The title flickers between titleColor and this (same value: no flicker).");
        splashMessageColor = color(c, cat, "messageColor", "FFE0A0", "Colour of the phase's message under the title.");
        splashFadeInTicks = c.getInt("fadeInTicks", cat, 10, 1, 1200, "Ticks the splash takes to appear.");
        splashStayTicks = c.getInt("stayTicks", cat, 80, 0, 12000, "Ticks it stays.");
        splashFadeOutTicks = c.getInt("fadeOutTicks", cat, 30, 1, 1200, "Ticks it takes to disappear.");
        splashTitleFont = c.getString("titleFont", cat, Tags.MOD_ID + ":textures/font/splash.png",
                "Client: font texture of the title (a 16 x 16 character grid like minecraft:textures/font/ascii.png). Minecraft's\n" +
                "own font while the texture is missing or this is empty.").trim();
        splashMessageFont = c.getString("messageFont", cat, "", "Client: font texture of the message; empty = Minecraft's own.").trim();

        phases = new Phase[count];
        for (int i = 0; i < count; i++) phases[i] = loadPhase(c, i + 1);
        for (int i = 0; i < count; i++) if (phases[i].weather == null) phases[i].weather = i == 0 ? Weather.UNCHANGED : phases[i - 1].weather;
        // only phase_1..phase_<count> are read (and created); a section above the count is left as it is, with a note
        for (String name : c.getCategoryNames()) {
            Matcher m = Pattern.compile("phase_(\\d+)").matcher(name);
            if (m.matches()) c.setCategoryComment(name, Integer.parseInt(m.group(1)) <= count ? null
                    : "Not used: phases.count is " + count + ". Kept so that a higher count brings it back; delete it if you like.");
        }

        c.dropUnused();
        c.save(); // also puts an older file's sections in order
    }

    /** Remembers the keys the mod reads, so keys an older version wrote can be dropped. */
    private static final class Tracked extends Configuration {
        private final Set<String> read = new HashSet<>();

        Tracked(File file) {
            super(file);
        }

        @Override
        public Property get(String category, String key, String defaultValue, String comment, Property.Type type) {
            read.add(category + '|' + key);
            return super.get(category, key, defaultValue, comment, type);
        }

        @Override
        public Property get(String category, String key, String[] defaultValues, String comment, Property.Type type) {
            read.add(category + '|' + key);
            return super.get(category, key, defaultValues, comment, type);
        }

        /** Drops keys no longer read from the sections that are (sections for phases above phases.count are kept). */
        void dropUnused() {
            Set<String> sections = new HashSet<>();
            for (String k : read) sections.add(k.substring(0, k.indexOf('|')));
            for (String name : sections) {
                ConfigCategory category = getCategory(name);
                for (String key : new ArrayList<>(category.keySet())) {
                    if (read.contains(name + '|' + key)) continue;
                    category.remove(key);
                    SolarApocalypse.LOGGER.info("Config: dropped {}.{}, no longer used", name, key);
                }
            }
        }
    }

    private static Phase loadPhase(Configuration c, int n) {
        String cat = "phase_" + n;
        Defaults d = Defaults.of(n);
        Phase p = new Phase();
        p.days = c.get(cat, "days", d.days,
                "Minimum length in days (0 = phases.baseDays with phases.scaling). The phase first destroys (from its start, at\n" +
                "its speed), then converts; if that takes longer, the next phase waits until it is done.").getDouble();
        String depth = c.getString("depth", cat, d.depth,
                "Layers below the reference (depthReference) destroyed by the end of this phase (whole number), or 'infinite'.\n" +
                "Never lower than an earlier phase's with the same reference; once infinite, every later phase is infinite.");
        p.depth = "infinite".equalsIgnoreCase(depth.trim()) ? INFINITE : parseInt(depth, 0);
        String reference = c.getString("depthReference", cat, "INHERIT",
                "What this phase's depth counts from: SURFACE or TOP_Y (see world.depthReference); INHERIT = the previous\n" +
                "phase's (phase 1: world.depthReference). Each reference has its own depth line, which only its phases move;\n" +
                "a block goes when either line reaches it. A TOP_Y line starts at world.topY and descends at this phase's speed,\n" +
                "flattening mountains first.", new String[]{"INHERIT", "SURFACE", "TOP_Y"}).trim().toUpperCase(Locale.ROOT);
        p.depthReference = reference.equals("SURFACE") ? DepthReference.SURFACE : reference.equals("TOP_Y") ? DepthReference.TOP_Y : null;
        String weather = c.getString("weather", cat, "INHERIT",
                "Weather while this phase runs, in the apocalypse's dimensions: UNCHANGED (as usual), NONE (no rain), RAIN (always\n" +
                "raining), THUNDER (always a thunderstorm); INHERIT = the previous phase's (phase 1: UNCHANGED). /weather cannot\n" +
                "change it meanwhile; it lasts half a day into a following UNCHANGED phase. A thunderstorm darkens the sky so the\n" +
                "world stops counting it as day: no direct sun damage while entities.sunNeedsDaytime applies, and players can\n" +
                "sleep at any time (each sleep skips to the next morning, which the SUN clock counts). Rain puts out burning mobs\n" +
                "and vanilla fire under the open sky; solar fire stays.",
                new String[]{"INHERIT", "UNCHANGED", "NONE", "RAIN", "THUNDER"}).trim().toUpperCase(Locale.ROOT);
        for (Weather w : Weather.values()) if (w.name().equals(weather)) p.weather = w;
        if (p.weather == null && !weather.equals("INHERIT")) SolarApocalypse.LOGGER.warn("Config {}.weather: unknown value '{}', using INHERIT", cat, weather);
        p.speed = enumValue(c, cat, "speed", d.speed,
                "How destruction reaches the depth: PHASE over the phase's days, RATE at layersPerDay, INSTANT at the phase start.\n" +
                "Infinite depth always descends at layersPerDay; each layer then stays for 1/layersPerDay of a day.");
        p.layersPerDay = c.get(cat, "layersPerDay", d.layersPerDay,
                "RATE speed, and infinite depth: layers per day. One layer takes (day length in seconds) / layersPerDay seconds;\n" +
                "a day is 1200 s (20 minutes) with the vanilla day length, or clock.ticksPerDay / 20 s in TICKS mode. For a\n" +
                "layer every S seconds use layersPerDay = 1200 / S: 16 = a layer every 75 s, 600 = a layer every 2 s.").getDouble();
        p.convert = c.getStringList("convert", cat, d.convert,
                "Block conversions of the surface layer (see convertDepth).\n" +
                "Format: <selector> -> <block> [modifiers]. Selectors: modid:name, modid:name:meta, modid:name[property=value,...],\n" +
                "modid:*, #oreDictName, material:<name>, * (any breakable block); several separated by commas, and !<selector>\n" +
                "excludes. Target: modid:name, modid:name:meta, modid:name[property=value,...] or air. Modifiers, in any order:\n" +
                "@ <chance>%, and preserveState (keep the block's facing and other properties the target has too, unless the target\n" +
                "sets them), e.g. minecraft:anvil[damage=0] -> minecraft:anvil[damage=1] @ 25% preserveState. The top half of a tall\n" +
                "plant or door never converts by itself: it goes when its bottom half changes. dimensions=-1 (or 0,-1; * = all, as\n" +
                "without it) limits a rule to those dimensions; a rule scoped elsewhere does not hide an older one. layer=2 limits a\n" +
                "rule to layer 2 of the surface (1 = the surface block and what is on it), layer=2-4 to layers 2 to 4, depth=3 to\n" +
                "the top 3 (= layer=1-3). Without them a rule acts in the top convertDepth layers of the running phase, except that\n" +
                "in an infinite phase an earlier phase's rule acts only in layer 1, the one the erosion takes next. Blocks then step\n" +
                "through stages as the surface comes down: grass -> grass_path layer=5, grass_path -> dirt layer=4, ...\n" +
                "Rules matching a block share it out in order: dirt -> gravel @ 70% converts 70 % of dirt and leaves the rest\n" +
                "(add dirt -> sand @ 30% to cover it); a rule without @ takes all that is left. Which blocks convert is random\n" +
                "but fixed per block and rule phase: a later phase only rolls again if it has the rule too (anvils damaged a bit\n" +
                "more each phase: repeat the rule in each phase). phases.convertRuleMode decides whether earlier phases' rules\n" +
                "still apply (per block and layer, the latest phase with a rule acting there wins).");
        p.convertDepth = c.getInt("convertDepth", cat, d.convertDepth, 1, 1 << 20,
                "Conversions reach the top this many layers of each column's current surface (1 = the top block, plus plants,\n" +
                "snow layers and the like on it), earlier phases' rules included (layers they reach for the first time convert\n" +
                "over this phase's convertDays). Converting a block to air makes the block below the new surface.\n" +
                "In an infinite phase the conversions run all the time, ahead of the descending destruction; earlier phases' rules\n" +
                "then act only in layer 1, this phase's own in all of them (or where their layer= says).");
        p.convertDays = c.get(cat, "convertDays", d.convertDays,
                "Conversions start once this phase's destruction is done and land at random but fixed moments over this many\n" +
                "days: -1 = the rest of the phase's days (at once if none are left), 0 = at once. Infinite phases: at once.").getDouble();
        p.destroy = c.getStringList("destroy", cat, d.destroy,
                "Selectors of blocks removed down to the phase's depth (erosion). * = every breakable block; !<selector>\n" +
                "excludes (* and !minecraft:obsidian on two lines). dimensions=... limits an entry to those dimensions, as in convert.");
        p.evaporate = c.getStringList("evaporate", cat, d.evaporate,
                "Liquids that evaporate from this phase on wherever the sun reaches them (see the evaporation section); a liquid\n" +
                "goes from the first phase that lists it. Selectors as in convert (* = every liquid), plus fluid:<name> (Forge\n" +
                "fluid name, e.g. fluid:water) and temperature<K, <=, >, >= (Forge fluid temperature in kelvin: water 300, lava\n" +
                "1300). Example: material:water and !temperature<250 here, temperature<250 in a later phase for cold liquids.\n" +
                "dimensions=... limits an entry to those dimensions, as in convert.");
        p.ignitePercent = c.get(cat, "ignitePercent", d.ignite,
                "Percent of surface blocks alight with solar fire once the phase's conversions are done (chosen at random but\n" +
                "fixed): looks, sounds and burns like fire but never spreads. A total: 25 then 50 in the next phase keeps the first\n" +
                "25 % alight and lights as many again. Fire is never put out between phases; in an infinite phase it is redrawn on\n" +
                "every new layer. No solar fire next to (diagonals too) a block that can burn; ice and snow nearby melt as in vanilla.").getDouble();
        p.igniteFlammablePercent = c.get(cat, "igniteFlammablePercent", d.ignite,
                "Percent of flammable surface blocks (wood, leaves, wool...) set alight with vanilla fire instead, which spreads\n" +
                "and burns them as usual.").getDouble();
        p.sunDamage = c.get(cat, "sunDamage", d.sunDamage, "Damage to mobs in direct sunlight per interval (2 = one heart).").getDouble();
        p.sunFireSeconds = c.getInt("sunFireSeconds", cat, d.sunFire, 0, 3600, "Seconds mobs in direct sunlight are set on fire.");
        p.backgroundDamage = c.get(cat, "backgroundDamage", d.backgroundDamage,
                "Damage per interval to mobs wherever sky light reaches (entities.backgroundMinSkyLight), sun or not.").getDouble();
        p.backgroundFireSeconds = c.getInt("backgroundFireSeconds", cat, 0, 0, 3600, "Seconds background heat sets mobs on fire.");
        p.message = c.getString("message", cat, "Phase " + n,
                "Chat message to every player when the phase starts; empty = none. (Defaults are placeholders for testing.)");
        p.sound = c.getString("sound", cat, "minecraft:entity.lightning.thunder",
                "Sound played to every player when the phase starts: any sound name the client knows (from a sounds.json), e.g.\n" +
                "minecraft:entity.lightning.thunder; empty = none.").trim();
        p.splash = c.getString("splash", cat, "Phase " + number(n),
                "Title shown big on every player's screen when the phase starts, with the message small underneath (see the\n" +
                "splash section); empty = none.");
        return p;
    }

    /**
     * Defaults for a fresh config: eleven phases. Plants, snow and cloth burn first (some by chance), wood and leaves
     * next, the ground degrades (dirt, gravel, sand, vitrified sand; stone cracks), then erosion: a few layers at one a day, and
     * from phase 11 infinite at 200 layers a day. Phases above 11 are empty.
     */
    private static final class Defaults {
        private static final double[] DAYS = {1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 0};
        private static final String[] DEPTH = {"0", "0", "0", "0", "0", "0", "1", "2", "3", "5", "infinite"};
        private static final double[] LAYERS_PER_DAY = {16, 16, 16, 16, 16, 16, 1, 1, 1, 1, 200};
        private static final int[] CONVERT_DEPTH = {1, 1, 1, 2, 2, 2, 2, 3, 4, 5, 5};
        private static final double[] CONVERT_DAYS = {-1, -1, -1, -1, -1, -1, 1, 1, 1, 1, -1};
        private static final double[] IGNITE = {0, 25, 50, 75, 80, 85, 90, 95, 95, 95, 100};
        private static final double[] SUN_DAMAGE = {0, 0.5, 1, 1.5, 2, 3, 4, 5, 6, 8, 10};
        private static final int[] SUN_FIRE = {0, 4, 6, 8, 10, 10, 10, 10, 10, 10, 10};
        private static final double[] BACKGROUND_DAMAGE = {0, 0, 0, 0.25, 0.5, 0.75, 1, 1.5, 2, 3, 4};
        private static final String[][] CONVERT = {
                {"minecraft:grass -> minecraft:grass_path", "minecraft:mycelium -> minecraft:grass_path",
                        "minecraft:farmland -> minecraft:grass_path"},
                {"minecraft:grass_path -> minecraft:dirt @ 30%", "material:snow -> air @ 30%", "material:crafted_snow -> air @ 30%", "material:tnt -> air"},
                {"minecraft:grass_path -> minecraft:dirt", "minecraft:dirt -> minecraft:gravel", "material:cloth -> air",
                        "material:carpet -> air", "material:snow -> air", "material:crafted_snow -> air", "material:plants -> air",
                        "material:leaves -> air", "material:gourd -> air", "material:web -> air", "material:vine -> air",
                        "material:ice -> air", "material:packed_ice -> air", "material:cactus -> air"},
                {"material:wood -> air", "minecraft:clay -> minecraft:hardened_clay", "minecraft:gravel -> minecraft:sand"},
                {"minecraft:sand -> solarapocalypse:vitrified_sand preserveState"},
                {"minecraft:stone:0 -> minecraft:cobblestone", "minecraft:stonebrick -> minecraft:cobblestone"},
                {}, {}, {}, {},
                // the gradient under the erosion: path, dirt, gravel, sand, then vitrified sand on top (carried rules, layer 1)
                {"minecraft:grass -> minecraft:grass_path layer=5", "minecraft:grass -> minecraft:dirt layer=4",
                        "minecraft:grass_path -> minecraft:dirt layer=4",
                        "minecraft:grass, minecraft:grass_path, minecraft:dirt -> minecraft:gravel layer=3",
                        "minecraft:grass, minecraft:dirt, minecraft:gravel -> minecraft:sand layer=2",
                        "minecraft:grass_path -> solarapocalypse:vitrified_sand layer=2"}};
        // water from phase 3, other liquids from 4, lava from 6
        private static final String[][] EVAPORATE = {{}, {}, {"material:water"}, {"*", "!material:lava"}, {}, {"material:lava"}};
        static final int COUNT = DAYS.length;

        double days;
        String depth = "0";
        Speed speed = Speed.PHASE;
        double layersPerDay = 16;
        int convertDepth = 1;
        double convertDays = -1;
        String[] convert = new String[0];
        String[] destroy = new String[0];
        String[] evaporate = new String[0];
        double ignite, sunDamage, backgroundDamage;
        int sunFire;

        static Defaults of(int n) {
            Defaults d = new Defaults();
            if (n < 1 || n > COUNT) return d;
            int i = n - 1;
            d.days = DAYS[i];
            d.depth = DEPTH[i];
            d.speed = i >= 6 ? Speed.RATE : Speed.PHASE;
            d.layersPerDay = LAYERS_PER_DAY[i];
            d.convertDepth = CONVERT_DEPTH[i];
            d.convertDays = CONVERT_DAYS[i];
            if (i < CONVERT.length) d.convert = CONVERT[i];
            if (i >= 6) d.destroy = new String[]{"*"};
            if (i < EVAPORATE.length) d.evaporate = EVAPORATE[i];
            d.ignite = IGNITE[i];
            d.sunDamage = SUN_DAMAGE[i];
            d.sunFire = SUN_FIRE[i];
            d.backgroundDamage = BACKGROUND_DAMAGE[i];
            return d;
        }
    }

    /** Forge saves categories sorted as text (phase_1, phase_10, phase_11, phase_2...): sort numbers by value instead. */
    private static void numberedCategoriesInOrder(Configuration c) {
        try {
            Field field = Configuration.class.getDeclaredField("categories");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ConfigCategory> categories = (Map<String, ConfigCategory>) field.get(c);
            Map<String, ConfigCategory> sorted = new TreeMap<>(Comparator.comparing(SolarConfig::paddedNumbers).thenComparing(s -> s));
            sorted.putAll(categories);
            field.set(c, sorted);
        } catch (ReflectiveOperationException | RuntimeException e) {
            SolarApocalypse.LOGGER.debug("Config categories stay in text order", e);
        }
    }

    private static String paddedNumbers(String s) {
        Matcher m = Pattern.compile("\\d+").matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) m.appendReplacement(out, String.format("%12s", m.group()).replace(' ', '0'));
        return m.appendTail(out).toString();
    }

    private static final String[] NUMBERS = {"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen", "Twenty"};

    /** 1 -> "One" (up to twenty, then digits), for the placeholder splash titles. */
    private static String number(int n) {
        return n < NUMBERS.length ? NUMBERS[n] : String.valueOf(n);
    }

    private static int color(Configuration c, String cat, String key, String def, String comment) {
        String s = c.getString(key, cat, def, comment).trim().replace("#", "");
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            SolarApocalypse.LOGGER.warn("Config {}.{}: '{}' is not a hex colour, using {}", cat, key, s, def);
            return Integer.parseInt(def, 16);
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
