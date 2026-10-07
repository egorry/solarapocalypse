package com.solsticeentertainment.solarapocalypse;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The phases' convert and destroy lists, compiled once per config load into identity maps over every registered block
 * state, so applying them is two map lookups per block. Per phase the maps hold the rules active in that phase
 * (phases.ruleMode): CARRY = every phase so far, the latest phase's rule winning per block; ISOLATED = that phase only.
 */
public final class BlockRules {

    public enum Liquid { NONE, WATER, LAVA, OTHER }

    /**
     * The conversion of one block state: its targets with their chances (rules with '@ n%'), and the (0-based) phase
     * whose rules they are, which sets when they are due.
     */
    public static final class Step {
        private final IBlockState[] targets;
        private final double[] upTo; // cumulative chance of each target; past the last one the block stays
        public final int phase;

        Step(List<IBlockState> targets, List<Double> upTo, int phase) {
            this.targets = targets.toArray(new IBlockState[0]);
            this.upTo = new double[upTo.size()];
            for (int i = 0; i < this.upTo.length; i++) this.upTo[i] = upTo.get(i);
            this.phase = phase;
        }

        /**
         * What the block at x, y, z becomes, or null if it stays. The roll is a fixed hash of the position, the phase and
         * the state, so a block's outcome never changes between looks.
         */
        public IBlockState pick(int x, int y, int z, IBlockState from) {
            IBlockState to = null;
            if (targets.length == 1 && upTo[0] >= 1) {
                to = targets[0];
            } else {
                double roll = Timeline.hash(x, y, z, CHANCE_SALT ^ phase << 16 ^ Block.getStateId(from));
                for (int i = 0; i < targets.length && to == null; i++) if (roll < upTo[i]) to = targets[i];
            }
            return to == from ? null : to;
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < targets.length; i++) {
                s.append(i == 0 ? "" : ", ").append(targets[i]);
                double chance = upTo[i] - (i == 0 ? 0 : upTo[i - 1]);
                if (chance < 1) s.append(String.format(Locale.ROOT, " @ %.4g%%", chance * 100));
            }
            return s.toString();
        }
    }

    private static final int CHANCE_SALT = 0x5EED << 12;

    private final List<Map<IBlockState, Step>> convert = new ArrayList<>();
    private final List<Map<IBlockState, Integer>> destroy = new ArrayList<>(); // state -> earliest phase destroying it
    private final Set<IBlockState> noVanillaFire = Collections.newSetFromMap(new IdentityHashMap<>());

    private BlockRules() {}

    public static BlockRules compile(SolarConfig.Phase[] phases) {
        BlockRules rules = new BlockRules();
        List<IBlockState> states = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) states.addAll(block.getBlockState().getValidStates());
        for (int i = 0; i < phases.length; i++) {
            String where = "phase_" + (i + 1);
            List<Predicate<IBlockState>> from = new ArrayList<>();
            List<IBlockState> to = new ArrayList<>();
            List<Double> share = new ArrayList<>();
            for (String line : phases[i].convert) {
                int arrow = line.indexOf("->");
                if (arrow < 0) {
                    SolarApocalypse.LOGGER.warn("{}.convert: '{}' has no '->'", where, line);
                    continue;
                }
                String right = line.substring(arrow + 2);
                int at = right.indexOf('@');
                double chance = at < 0 ? 1 : percent(right.substring(at + 1), where);
                Predicate<IBlockState> selector = selectors(new String[]{line.substring(0, arrow)}, where);
                IBlockState target = target(at < 0 ? right : right.substring(0, at), where);
                if (selector == null || target == null || chance <= 0) continue;
                from.add(selector);
                to.add(target);
                share.add(chance);
            }
            Predicate<IBlockState> gone = selectors(phases[i].destroy, where);
            boolean carry = SolarConfig.ruleMode == SolarConfig.RuleMode.CARRY && i > 0;
            Map<IBlockState, Step> conversions = carry ? new IdentityHashMap<>(rules.convert.get(i - 1)) : new IdentityHashMap<>();
            Map<IBlockState, Integer> removals = carry ? new IdentityHashMap<>(rules.destroy.get(i - 1)) : new IdentityHashMap<>();
            for (IBlockState state : states) {
                if (state.getMaterial() == Material.AIR) continue;
                // matching rules share the block out in order ('@ n%', 100 % without); the latest phase's set wins
                List<IBlockState> targets = new ArrayList<>();
                List<Double> upTo = new ArrayList<>();
                double total = 0;
                boolean changes = false;
                for (int r = 0; r < from.size() && total < 1; r++) {
                    if (!from.get(r).test(state)) continue;
                    total = Math.min(1, total + share.get(r));
                    targets.add(to.get(r));
                    upTo.add(total);
                    changes |= to.get(r) != state;
                }
                if (changes) conversions.put(state, new Step(targets, upTo, i));
                if (gone != null && gone.test(state)) removals.putIfAbsent(state, i);
            }
            cutLoops(conversions, where);
            rules.convert.add(conversions);
            rules.destroy.add(removals);
        }
        Predicate<IBlockState> noFire = selectors(SolarConfig.vanillaFireBlacklist, "blocks.vanillaFireBlacklist");
        if (noFire != null) for (IBlockState state : states) if (noFire.test(state)) rules.noVanillaFire.add(state);
        return rules;
    }

    /** Breaks loops in a phase's conversions (A -> B -> A): the loop's oldest rule is dropped, with a warning. */
    private static void cutLoops(Map<IBlockState, Step> conversions, String where) {
        for (boolean again = true; again; ) {
            again = false;
            Set<IBlockState> clear = Collections.newSetFromMap(new IdentityHashMap<>());
            for (IBlockState first : conversions.keySet()) {
                List<IBlockState> loop = loopFrom(conversions, first, new ArrayList<>(), clear);
                if (loop == null) continue;
                IBlockState oldest = loop.get(0);
                for (IBlockState q : loop) if (conversions.get(q).phase < conversions.get(oldest).phase) oldest = q;
                StringBuilder chain = new StringBuilder();
                for (IBlockState q : loop) chain.append(q).append(" -> ");
                SolarApocalypse.LOGGER.warn("{}.convert: rules loop ({}{}); dropping {} -> {}", where, chain, loop.get(0), oldest,
                        conversions.get(oldest));
                conversions.remove(oldest);
                again = true;
                break;
            }
        }
    }

    /** Depth-first search over conversion targets: a loop reachable from a state (its states in order), or null. */
    private static List<IBlockState> loopFrom(Map<IBlockState, Step> conversions, IBlockState state, List<IBlockState> path,
                                              Set<IBlockState> clear) {
        int seen = path.indexOf(state); // block states are singletons: equals is identity
        if (seen >= 0) return new ArrayList<>(path.subList(seen, path.size()));
        Step step = conversions.get(state);
        if (step == null || clear.contains(state)) return null;
        path.add(state);
        for (IBlockState target : step.targets) {
            if (target == state) continue;
            List<IBlockState> loop = loopFrom(conversions, target, path, clear);
            if (loop != null) return loop;
        }
        path.remove(path.size() - 1);
        clear.add(state);
        return null;
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

    /** The conversion active for a state while a phase (0-based) runs, or null. */
    public Step convert(int phase, IBlockState state) {
        return convert.get(phase).get(state);
    }

    /** Block states with an active conversion / destroy rule in a phase (for the load summary). */
    public int convertCount(int phase) {
        return convert.get(phase).size();
    }

    public int destroyCount(int phase) {
        return destroy.get(phase).size();
    }

    /** The phase whose destroy rule removes a state while a phase runs, or -1. */
    public int destroyPhase(int phase, IBlockState state) {
        Integer q = destroy.get(phase).get(state);
        return q == null ? -1 : q;
    }

    public static Liquid liquid(IBlockState state) {
        Material m = state.getMaterial();
        if (!m.isLiquid() && !(state.getBlock() instanceof IFluidBlock)) return Liquid.NONE;
        if (m == Material.WATER) return Liquid.WATER;
        if (m == Material.LAVA) return Liquid.LAVA;
        return state.getBlock() instanceof IFluidBlock || state.getBlock() instanceof BlockLiquid ? Liquid.OTHER : Liquid.NONE;
    }

    /**
     * A list of selectors (each entry may hold several, separated by commas): any of the plain ones, minus any of the ones
     * starting with '!'. Null if no plain selector could be read.
     */
    private static Predicate<IBlockState> selectors(String[] entries, String where) {
        Predicate<IBlockState> any = null, none = null;
        for (String entry : entries) {
            for (String raw : entry.split(",")) {
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

    @SuppressWarnings("deprecation")
    private static IBlockState target(String raw, String where) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("air")) return Blocks.AIR.getDefaultState();
        String[] parts = s.split(":");
        if (parts.length < 2 || parts.length > 3) {
            SolarApocalypse.LOGGER.warn("{}: cannot read target '{}'", where, s);
            return null;
        }
        Block block = block(parts[0] + ":" + parts[1], where);
        if (block == null) return null;
        if (parts.length == 2) return block.getDefaultState();
        int meta = meta(parts[2], where);
        return meta < 0 ? null : block.getStateFromMeta(meta);
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
