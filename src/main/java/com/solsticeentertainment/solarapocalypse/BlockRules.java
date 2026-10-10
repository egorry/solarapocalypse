package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The phases' convert and destroy lists, compiled once per config load into identity maps over every registered block
 * state, so applying them is a map lookup per block. Destroy maps hold, per phase, the rules active in that phase
 * (phases.destroyRuleMode). Conversions keep, per block state, the rules of every phase (latest phase first); a Step
 * resolves for the running phase which of them act in which layer (phases.convertRuleMode, convertDepth, layer=).
 */
public final class BlockRules {

    /** One convert rule as it applies to one block state: its target, chance ('@ n%'), phase and layers. */
    private static final class Rule {
        final IBlockState target;
        final double share;
        final int phase;
        final int lo, hi; // layer= / depth=, or lo 0: the default layers
        int off = Integer.MAX_VALUE; // a loop cut turns the rule off from this phase on

        Rule(IBlockState target, double share, int phase, int lo, int hi) {
            this.target = target;
            this.share = share;
            this.phase = phase;
            this.lo = lo;
            this.hi = hi;
        }
    }

    /**
     * The conversion of one block state: the matching rules of every phase, latest phase first. While a phase runs, its own
     * rules act in the top convertDepth layers of the column's surface (1 = the surface block and what is on it), carried
     * rules of earlier phases (CARRY) in the top convertDepth layers too, but only in layer 1 in an infinite phase (the layer
     * the erosion takes next); layer= or depth= on a rule overrides both. In each layer the latest phase with a rule acting
     * there decides.
     */
    public final class Step {
        private final Rule[] rules;
        private final BitSet active = new BitSet(); // phases in which a rule is on

        private Step(List<Rule> rules) {
            this.rules = rules.toArray(new Rule[0]);
        }

        private boolean on(Rule r, int phase) {
            return (carry ? r.phase <= phase : r.phase == phase) && phase < r.off && convertDepth[phase] > 0; // 0: none at all
        }

        private int lo(Rule r) {
            return r.lo > 0 ? r.lo : 1;
        }

        /** The deepest layer a rule acts in while a phase runs. */
        private int hi(Rule r, int phase) {
            if (r.lo > 0) return r.hi;
            return r.phase < phase && infinite[phase] ? 1 : convertDepth[phase];
        }

        private boolean acts(Rule r, int layer, int phase) {
            return on(r, phase) && layer >= lo(r) && layer <= hi(r, phase);
        }

        /** The phase whose rules decide a layer while a phase runs (the latest with a rule acting there), or -1. */
        private int decider(int layer, int phase) {
            for (Rule r : rules) if (acts(r, layer, phase)) return r.phase;
            return -1;
        }

        /**
         * The rule that converts the block at x, y, z in a layer of its column's surface while a phase runs (an index for
         * target and timing), or -1 if it stays. The deciding phase's rules acting in that layer share the block out in
         * order; the roll is a fixed hash of the position, that phase and the state, so a block's outcome never changes
         * between looks.
         */
        public int pick(int x, int y, int z, IBlockState from, int layer, int phase) {
            int set = -1;
            double total = 0, roll = -1;
            for (int i = 0; i < rules.length; i++) {
                Rule r = rules[i];
                if (!acts(r, layer, phase)) continue;
                if (set >= 0 && r.phase != set) break; // an older phase: the latest one with a rule here decides
                set = r.phase;
                double upTo = Math.min(1, total + r.share);
                if (upTo < 1 || total > 0) {
                    if (roll < 0) roll = Timeline.hash(x, y, z, CHANCE_SALT ^ set << 16 ^ Block.getStateId(from));
                    if (roll >= upTo) {
                        total = upTo;
                        continue;
                    }
                }
                return r.target == from ? -1 : i;
            }
            return -1;
        }

        public IBlockState target(int rule) {
            return rules[rule].target;
        }

        /**
         * The phase whose conversion timing a picked rule follows in a layer: the first phase, from the rule's own, in which
         * it reached that layer. A carried rule reaching deeper in a later phase (a bigger convertDepth) converts those
         * layers over that phase's conversion time; layers it reached before catch up at once.
         */
        public int timing(int rule, int layer, int phase) {
            Rule r = rules[rule];
            if (r.lo > 0) return r.phase;
            for (int k = r.phase; k < phase; k++) if (layer <= hi(r, k)) return k;
            return phase;
        }

        /**
         * The deepest layer above `layer` where the rules acting change (the block's next chance as the surface comes down):
         * a rule starts acting, or one acting here stops, which can hand the layer to an older phase. 0 if none.
         */
        public int nextLayer(int layer, int phase) {
            int next = 0;
            for (Rule r : rules) {
                if (!on(r, phase)) continue;
                int hi = hi(r, phase);
                if (hi < layer) next = Math.max(next, hi);
                else if (lo(r) <= layer) next = Math.max(next, lo(r) - 1);
            }
            return next;
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            for (Rule r : rules) {
                s.append(s.length() == 0 ? "" : ", ").append(r.target);
                if (r.share < 1) s.append(String.format(Locale.ROOT, " @ %.4g%%", r.share * 100));
                s.append(" (phase_").append(r.phase + 1).append(r.lo > 0 ? " layer=" + r.lo + "-" + r.hi : "").append(')');
            }
            return s.toString();
        }
    }

    private static final int CHANCE_SALT = 0x5EED << 12;

    /** A rule's target: a block state, and with preserveState the source block's properties the target block has too. */
    private static final class Target {
        final IBlockState state;
        final Set<String> given; // properties the rule sets itself
        final boolean preserve;

        Target(IBlockState state, Set<String> given, boolean preserve) {
            this.state = state;
            this.given = given;
            this.preserve = preserve;
        }

        IBlockState of(IBlockState from) {
            if (!preserve) return state;
            IBlockState to = state;
            for (Map.Entry<IProperty<?>, Comparable<?>> e : from.getProperties().entrySet()) {
                IProperty<?> p = to.getBlock().getBlockState().getProperty(e.getKey().getName());
                if (p == null || given.contains(p.getName())) continue;
                Comparable<?> value = p.parseValue(valueName(e.getKey(), e.getValue())).orNull(); // by name: stairs of any mod
                if (value != null) to = with(to, p, value);
            }
            return to;
        }
    }

    private final Map<IBlockState, Step> convert = new IdentityHashMap<>();
    private final boolean carry = SolarConfig.convertRuleMode == SolarConfig.RuleMode.CARRY;
    private final int[] convertDepth, convertCount;
    private final boolean[] infinite; // the phase's depth is infinite (it and every later phase)
    private int deepestLayer = 1;
    private final List<Map<IBlockState, Integer>> destroy = new ArrayList<>(); // state -> earliest phase destroying it
    private final Set<IBlockState> noVanillaFire = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<IBlockState, Integer> evaporate = new IdentityHashMap<>(); // liquid state -> first phase evaporating it

    private BlockRules(SolarConfig.Phase[] phases) {
        convertDepth = new int[phases.length];
        convertCount = new int[phases.length];
        infinite = new boolean[phases.length];
        boolean endless = false;
        for (int i = 0; i < phases.length; i++) {
            convertDepth[i] = Math.max(0, phases[i].convertDepth);
            infinite[i] = endless |= phases[i].depth == SolarConfig.INFINITE;
        }
    }

    /**
     * The rules for one dimension: an entry with dimensions=... (e.g. dimensions=-1 or dimensions=0,-1; * = all, as without
     * it) only exists in those dimensions. Filtering comes first, so a rule scoped elsewhere never shadows an older rule;
     * phases, CARRY and rule order then work as usual.
     */
    public static BlockRules compile(SolarConfig.Phase[] phases, int dimension) {
        BlockRules rules = new BlockRules(phases);
        List<IBlockState> states = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) states.addAll(block.getBlockState().getValidStates());
        for (int i = 0; i < phases.length; i++) {
            String where = "phase_" + (i + 1);
            List<Predicate<IBlockState>> from = new ArrayList<>();
            List<Target> to = new ArrayList<>();
            List<Double> share = new ArrayList<>();
            List<int[]> layers = new ArrayList<>();
            Map<IBlockState, List<Rule>> matched = new IdentityHashMap<>();
            for (String raw : scoped(phases[i].convert, dimension)) {
                String line = raw.replaceAll("\\s+(?=[^\\[\\]]*\\])", ""); // no spaces inside [...]
                int arrow = line.indexOf("->");
                if (arrow < 0) {
                    SolarApocalypse.LOGGER.warn("{}.convert: '{}' has no '->'", where, raw);
                    continue;
                }
                // target, then modifiers in any order: '@ n%', preserveState, layer=k, layer=a-b, depth=k (= layer=1-k)
                String[] words = line.substring(arrow + 2).replaceAll("\\s*%", "%").replace("@", " @ ").trim().split("\\s+");
                double chance = 1;
                boolean preserve = false;
                int[] layer = {0, 0}; // the default layers (Step.hi)
                for (int w = 1; w < words.length; w++) {
                    Matcher depth = LAYERS.matcher(words[w]);
                    if (words[w].equals("@") && w + 1 < words.length) chance = percent(words[++w], where);
                    else if (words[w].equalsIgnoreCase("preserveState")) preserve = true;
                    else if (depth.matches()) {
                        int a = Integer.parseInt(depth.group(2)), b = depth.group(3) == null ? a : Integer.parseInt(depth.group(3));
                        boolean top = depth.group(1).equalsIgnoreCase("depth") && depth.group(3) == null; // depth=k: the top k
                        layer = new int[]{top ? 1 : Math.min(a, b), Math.max(a, b)};
                        if (Math.min(a, b) < 1) {
                            SolarApocalypse.LOGGER.warn("{}.convert: '{}' in '{}': layers count from 1 (the surface block)", where, words[w], raw);
                            layer = null;
                            break;
                        }
                    } else SolarApocalypse.LOGGER.warn("{}.convert: unknown modifier '{}' in '{}'", where, words[w], raw);
                }
                if (layer == null) continue;
                Predicate<IBlockState> selector = selectors(new String[]{line.substring(0, arrow)}, where);
                Target target = target(words[0], preserve, where);
                if (selector == null || target == null || chance <= 0) continue;
                from.add(selector);
                to.add(target);
                share.add(chance);
                layers.add(layer);
                rules.deepestLayer = Math.max(rules.deepestLayer, layer[1]);
            }
            Predicate<IBlockState> gone = selectors(scoped(phases[i].destroy, dimension), where);
            Predicate<IBlockState> dry = selectors(scoped(phases[i].evaporate, dimension), where);
            boolean carryDestroy = SolarConfig.destroyRuleMode == SolarConfig.RuleMode.CARRY && i > 0;
            Map<IBlockState, Integer> removals = carryDestroy ? new IdentityHashMap<>(rules.destroy.get(i - 1)) : new IdentityHashMap<>();
            for (IBlockState state : states) {
                if (state.getMaterial() == Material.AIR) continue;
                // matching rules share the block out in order ('@ n%', 100 % without) among those acting in its layer
                // (Step.pick). The top half of a tall plant or door follows its bottom half (it pops off when that changes).
                List<Rule> list = new ArrayList<>();
                boolean changes = false;
                for (int r = 0; r < from.size() && !upperHalf(state); r++) {
                    if (!from.get(r).test(state)) continue;
                    IBlockState target = to.get(r).of(state);
                    list.add(new Rule(target, share.get(r), i, layers.get(r)[0], layers.get(r)[1]));
                    changes |= target != state;
                }
                if (changes) matched.put(state, list);
                if (gone != null && gone.test(state)) removals.putIfAbsent(state, i);
                if (dry != null && isLiquid(state) && dry.test(state)) rules.evaporate.putIfAbsent(state, i);
            }
            for (Map.Entry<IBlockState, List<Rule>> e : matched.entrySet()) {
                Step step = rules.convert.get(e.getKey());
                List<Rule> all = new ArrayList<>(e.getValue()); // latest phase first
                if (step != null) all.addAll(Arrays.asList(step.rules));
                rules.convert.put(e.getKey(), rules.new Step(all));
            }
            if (!matched.isEmpty() || SolarConfig.convertRuleMode == SolarConfig.RuleMode.CARRY && !rules.convert.isEmpty()) {
                rules.deepestLayer = Math.max(rules.deepestLayer, rules.convertDepth[i]);
            }
            rules.destroy.add(removals);
        }
        for (int i = 0; i < phases.length; i++) rules.cutLoops(i, "phase_" + (i + 1));
        for (Step step : rules.convert.values()) {
            for (int i = 0; i < phases.length; i++) {
                for (Rule r : step.rules) {
                    if (!step.on(r, i)) continue;
                    step.active.set(i);
                    rules.convertCount[i]++;
                    break;
                }
            }
        }
        Predicate<IBlockState> noFire = selectors(SolarConfig.vanillaFireBlacklist, "blocks.vanillaFireBlacklist");
        if (noFire != null) for (IBlockState state : states) if (noFire.test(state)) rules.noVanillaFire.add(state);
        return rules;
    }

    /**
     * Breaks loops in the conversions while a phase runs (A -> B -> A in one layer): the deciding rules of the loop's member
     * with the oldest ones are turned off from this phase on, with a warning. Rules in different layers never loop.
     */
    private void cutLoops(int phase, String where) {
        TreeSet<Integer> layers = new TreeSet<>(); // where the rules acting change: one layer per stretch is enough
        for (Step step : convert.values()) {
            for (Rule r : step.rules) {
                if (!step.on(r, phase)) continue;
                layers.add(step.lo(r));
                layers.add(step.hi(r, phase) + 1);
            }
        }
        for (boolean again = true; again; ) { // after a cut, every layer again: older rules may now decide a checked one
            again = false;
            search:
            for (int layer : layers) {
                Set<IBlockState> clear = Collections.newSetFromMap(new IdentityHashMap<>());
                for (IBlockState first : convert.keySet()) {
                    List<IBlockState> loop = loopFrom(first, layer, phase, new ArrayList<>(), clear);
                    if (loop == null) continue;
                    IBlockState oldest = loop.get(0);
                    for (IBlockState q : loop) if (convert.get(q).decider(layer, phase) < convert.get(oldest).decider(layer, phase)) oldest = q;
                    Step step = convert.get(oldest);
                    int set = step.decider(layer, phase);
                    StringBuilder chain = new StringBuilder();
                    for (IBlockState q : loop) chain.append(q).append(" -> ");
                    SolarApocalypse.LOGGER.warn("{}: convert rules loop in layer {} ({}{}); phase_{}'s rules for {} are off from {} on",
                            where, layer, chain, loop.get(0), set + 1, oldest, where);
                    for (Rule r : step.rules) if (r.phase == set && step.acts(r, layer, phase)) r.off = phase;
                    again = true;
                    break search;
                }
            }
        }
    }

    /** Depth-first search over the conversions deciding a layer: a loop reachable from a state (its states in order), or null. */
    private List<IBlockState> loopFrom(IBlockState state, int layer, int phase, List<IBlockState> path, Set<IBlockState> clear) {
        int seen = path.indexOf(state); // block states are singletons: equals is identity
        if (seen >= 0) return new ArrayList<>(path.subList(seen, path.size()));
        Step step = convert.get(state);
        if (step == null || clear.contains(state)) return null;
        int set = step.decider(layer, phase);
        path.add(state);
        for (Rule r : step.rules) {
            if (set < 0 || r.phase != set || r.target == state || !step.acts(r, layer, phase)) continue;
            List<IBlockState> loop = loopFrom(r.target, layer, phase, path, clear);
            if (loop != null) return loop;
        }
        path.remove(path.size() - 1);
        clear.add(state);
        return null;
    }

    /** The top half of a two-block plant or door (a 'half' property set to 'upper'). */
    private static boolean upperHalf(IBlockState state) {
        for (Map.Entry<IProperty<?>, Comparable<?>> e : state.getProperties().entrySet()) {
            if (e.getKey().getName().equals("half") && valueName(e.getKey(), e.getValue()).equals("upper")) return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(IProperty property, Comparable value) {
        return property.getName(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState with(IBlockState state, IProperty property, Comparable value) {
        return state.withProperty(property, value);
    }

    /** "facing=east,half=top" for a block -> its properties and values; null (with a warning) if one cannot be read. */
    private static Map<IProperty<?>, Comparable<?>> properties(Block block, String list, String where) {
        Map<IProperty<?>, Comparable<?>> out = new LinkedHashMap<>();
        for (String pair : list.split(",")) {
            String[] kv = pair.split("=");
            IProperty<?> p = kv.length == 2 ? block.getBlockState().getProperty(kv[0].trim()) : null;
            Comparable<?> value = p == null ? null : p.parseValue(kv[1].trim()).orNull();
            if (value == null) {
                List<String> known = new ArrayList<>();
                for (IProperty<?> q : block.getBlockState().getProperties()) known.add(q.getName() + q.getAllowedValues());
                SolarApocalypse.LOGGER.warn("{}: cannot read '{}' for {}, its properties: {}", where, pair, block.getRegistryName(), known);
                return null;
            }
            out.put(p, value);
        }
        return out;
    }

    private static final Pattern LAYERS = Pattern.compile("(?i)(depth|layers?)=(-?\\d{1,9})(?:-(\\d{1,9}))?");
    private static final Pattern DIMENSIONS = Pattern.compile("(?i)\\s*\\bdimensions\\s*=\\s*(\\*|-?\\d{1,9}(?:\\s*,\\s*-?\\d{1,9})*)");

    /** The entries that apply in a dimension, without their dimensions=... modifier. */
    private static String[] scoped(String[] entries, int dimension) {
        List<String> out = new ArrayList<>();
        for (String entry : entries) {
            Matcher m = DIMENSIONS.matcher(entry);
            if (!m.find()) {
                out.add(entry);
                continue;
            }
            String rest = entry.substring(0, m.start()) + " " + entry.substring(m.end());
            for (String d : m.group(1).split(",")) {
                if (d.trim().equals("*") || Integer.parseInt(d.trim()) == dimension) {
                    out.add(rest.trim());
                    break;
                }
            }
        }
        return out.toArray(new String[0]);
    }

    /** "30%" or "30" -> 0.3; -1 if unreadable. */
    private static double percent(String raw, String where) {
        try {
            return Math.max(0, Math.min(100, Double.parseDouble(raw.trim().replace("%", "").trim()))) / 100;
        } catch (NumberFormatException e) {
            SolarApocalypse.LOGGER.warn("{}.convert: '{}' is not a percentage", where, raw.trim());
            return -1;
        }
    }

    /** Whether a block never gets vanilla fire from the sun (blocks.vanillaFireBlacklist). */
    public boolean noVanillaFire(IBlockState state) {
        return noVanillaFire.contains(state);
    }

    /** The conversion of a state, if one of its rules is on while a phase (0-based) runs, or null. */
    public Step convert(int phase, IBlockState state) {
        Step step = convert.get(state);
        return step != null && step.active.get(phase) ? step : null;
    }

    /** The deepest layer below the surface any conversion acts in (1 = the surface block only). */
    public int deepestLayer() {
        return deepestLayer;
    }

    /** Block states with a conversion / destroy rule on in a phase (for the load summary). */
    public int convertCount(int phase) {
        return convertCount[phase];
    }

    public int destroyCount(int phase) {
        return destroy.get(phase).size();
    }

    /** The phase whose destroy rule removes a state while a phase runs, or -1. */
    public int destroyPhase(int phase, IBlockState state) {
        Integer q = destroy.get(phase).get(state);
        return q == null ? -1 : q;
    }

    /** The (0-based) phase from which a liquid evaporates (the first phase whose evaporate list has it), or -1. */
    public int evaporationPhase(IBlockState state) {
        Integer q = evaporate.get(state);
        return q == null ? -1 : q;
    }

    /** Liquid states whose evaporation starts in a phase (for the load summary). */
    public int evaporateCount(int phase) {
        int n = 0;
        for (int q : evaporate.values()) if (q == phase) n++;
        return n;
    }

    public static boolean isLiquid(IBlockState state) {
        return state.getBlock() instanceof BlockLiquid || state.getBlock() instanceof IFluidBlock;
    }

    /** The Forge fluid of a liquid block (vanilla water and lava included), or null. */
    private static Fluid fluid(IBlockState state) {
        Block block = state.getBlock();
        return block instanceof IFluidBlock ? ((IFluidBlock) block).getFluid() : FluidRegistry.lookupFluidForBlock(block);
    }

    private static final Pattern TEMPERATURE = Pattern.compile("temperature\\s*(<=|>=|<|>)\\s*(-?\\d{1,9})");

    /**
     * A list of selectors (each entry may hold several, separated by commas): any of the plain ones, minus any of the ones
     * starting with '!'. Null if no plain selector could be read.
     */
    private static Predicate<IBlockState> selectors(String[] entries, String where) {
        Predicate<IBlockState> any = null, none = null;
        for (String entry : entries) {
            for (String raw : entry.split(",(?![^\\[]*\\])")) { // commas inside [...] separate properties
                String s = raw.trim();
                if (s.isEmpty()) continue;
                boolean not = s.startsWith("!");
                Predicate<IBlockState> one = selector(not ? s.substring(1) : s, where);
                if (one == null) continue;
                if (not) none = none == null ? one : none.or(one);
                else any = any == null ? one : any.or(one);
            }
        }
        if (any == null || none == null) return any;
        Predicate<IBlockState> include = any, exclude = none;
        return state -> include.test(state) && !exclude.test(state);
    }

    private static Predicate<IBlockState> selector(String raw, String where) {
        String s = raw.trim();
        if (s.equals("*")) return state -> !unbreakable(state);
        if (s.toLowerCase(Locale.ROOT).startsWith("fluid:")) {
            String name = s.substring(6).trim();
            if (!FluidRegistry.isFluidRegistered(name)) {
                SolarApocalypse.LOGGER.warn("{}: unknown fluid in '{}'", where, s);
                return null;
            }
            return state -> {
                Fluid f = fluid(state);
                return f != null && f.getName().equals(name);
            };
        }
        Matcher temperature = TEMPERATURE.matcher(s.toLowerCase(Locale.ROOT));
        if (temperature.matches()) { // Forge fluid temperature in kelvin: water 300, lava 1300
            String op = temperature.group(1);
            int kelvin = Integer.parseInt(temperature.group(2));
            return state -> {
                Fluid f = fluid(state);
                if (f == null) return false;
                int t = f.getTemperature();
                return op.equals("<") ? t < kelvin : op.equals("<=") ? t <= kelvin : op.equals(">") ? t > kelvin : t >= kelvin;
            };
        }
        if (s.startsWith("#")) {
            int id = OreDictionary.getOreID(s.substring(1));
            return state -> !unbreakable(state) && hasOre(state, id);
        }
        if (s.toLowerCase(Locale.ROOT).startsWith("material:")) {
            Material m = MATERIALS.get(s.substring(9).trim().toLowerCase(Locale.ROOT));
            if (m == null) {
                SolarApocalypse.LOGGER.warn("{}: unknown material in '{}', known: {}", where, s, MATERIALS.keySet());
                return null;
            }
            return state -> state.getMaterial() == m && !unbreakable(state);
        }
        int open = s.indexOf('[');
        if (open > 0 && s.endsWith("]")) {
            Block block = block(s.substring(0, open).trim(), where);
            Map<IProperty<?>, Comparable<?>> wanted = block == null ? null : properties(block, s.substring(open + 1, s.length() - 1), where);
            if (wanted == null) return null;
            return state -> {
                if (state.getBlock() != block) return false;
                for (Map.Entry<IProperty<?>, Comparable<?>> e : wanted.entrySet()) {
                    if (!e.getValue().equals(state.getProperties().get(e.getKey()))) return false;
                }
                return true;
            };
        }
        String[] parts = s.split(":");
        if (parts.length == 2 && parts[1].equals("*")) {
            String mod = parts[0];
            return state -> mod.equals(state.getBlock().getRegistryName().getNamespace()) && !unbreakable(state);
        }
        if (parts.length < 2 || parts.length > 3) {
            SolarApocalypse.LOGGER.warn("{}: cannot read selector '{}'", where, s);
            return null;
        }
        Block block = block(parts[0] + ":" + parts[1], where);
        if (block == null) return null;
        if (parts.length == 2) return state -> state.getBlock() == block;
        int meta = meta(parts[2], where);
        return meta < 0 ? null : state -> state.getBlock() == block && block.getMetaFromState(state) == meta;
    }

    /** "air", "mod:block", "mod:block:meta" or "mod:block[property=value,...]" (unset properties: the default's). */
    @SuppressWarnings("deprecation")
    private static Target target(String raw, boolean preserve, String where) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("air")) return new Target(Blocks.AIR.getDefaultState(), Collections.emptySet(), false);
        int open = s.indexOf('[');
        if (open > 0 && s.endsWith("]")) {
            Block block = block(s.substring(0, open).trim(), where);
            Map<IProperty<?>, Comparable<?>> given = block == null ? null : properties(block, s.substring(open + 1, s.length() - 1), where);
            if (given == null) return null;
            IBlockState state = block.getDefaultState();
            Set<String> names = new HashSet<>();
            for (Map.Entry<IProperty<?>, Comparable<?>> e : given.entrySet()) {
                state = with(state, e.getKey(), e.getValue());
                names.add(e.getKey().getName());
            }
            return new Target(state, names, preserve);
        }
        String[] parts = s.split(":");
        if (parts.length < 2 || parts.length > 3) {
            SolarApocalypse.LOGGER.warn("{}: cannot read target '{}'", where, s);
            return null;
        }
        Block block = block(parts[0] + ":" + parts[1], where);
        if (block == null) return null;
        if (parts.length == 2) return new Target(block.getDefaultState(), Collections.emptySet(), preserve);
        int meta = meta(parts[2], where);
        if (meta < 0) return null;
        Set<String> all = new HashSet<>(); // metadata sets every property
        for (IProperty<?> p : block.getBlockState().getProperties()) all.add(p.getName());
        return new Target(block.getStateFromMeta(meta), all, preserve);
    }

    private static Block block(String id, String where) {
        ResourceLocation key = new ResourceLocation(id);
        if (!ForgeRegistries.BLOCKS.containsKey(key)) {
            SolarApocalypse.LOGGER.warn("{}: unknown block '{}'", where, id);
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(key);
    }

    private static int meta(String s, String where) {
        try {
            int meta = Integer.parseInt(s.trim());
            if (meta >= 0 && meta < 16) return meta;
        } catch (NumberFormatException ignored) {
        }
        SolarApocalypse.LOGGER.warn("{}: metadata '{}' must be 0-15", where, s);
        return -1;
    }

    private static boolean hasOre(IBlockState state, int oreId) {
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == net.minecraft.init.Items.AIR) return false;
        for (int id : OreDictionary.getOreIDs(new ItemStack(item, 1, state.getBlock().getMetaFromState(state)))) {
            if (id == oreId) return true;
        }
        return false;
    }

    /** Wildcard selectors skip blocks that cannot be broken (bedrock, barriers, portal frames, command blocks). */
    @SuppressWarnings("deprecation")
    private static boolean unbreakable(IBlockState state) {
        try {
            return state.getBlock().getBlockHardness(state, null, null) < 0;
        } catch (RuntimeException e) {
            return false; // a modded block that needs a world for its hardness: treat as breakable
        }
    }

    private static final Map<String, Material> MATERIALS = new HashMap<>();

    static {
        Object[] pairs = {
                "grass", Material.GRASS, "ground", Material.GROUND, "wood", Material.WOOD, "rock", Material.ROCK,
                "iron", Material.IRON, "anvil", Material.ANVIL, "water", Material.WATER, "lava", Material.LAVA,
                "leaves", Material.LEAVES, "plants", Material.PLANTS, "vine", Material.VINE, "sponge", Material.SPONGE,
                "cloth", Material.CLOTH, "fire", Material.FIRE, "sand", Material.SAND, "circuits", Material.CIRCUITS,
                "carpet", Material.CARPET, "glass", Material.GLASS, "redstone_light", Material.REDSTONE_LIGHT,
                "tnt", Material.TNT, "coral", Material.CORAL, "ice", Material.ICE, "packed_ice", Material.PACKED_ICE,
                "snow", Material.SNOW, "crafted_snow", Material.CRAFTED_SNOW, "cactus", Material.CACTUS,
                "clay", Material.CLAY, "gourd", Material.GOURD, "dragon_egg", Material.DRAGON_EGG,
                "portal", Material.PORTAL, "cake", Material.CAKE, "web", Material.WEB, "piston", Material.PISTON};
        for (int i = 0; i < pairs.length; i += 2) MATERIALS.put((String) pairs[i], (Material) pairs[i + 1]);
    }
}
