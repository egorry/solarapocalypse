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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The phases' convert and destroy lists, compiled once per config load into identity maps over every registered block
 * state, so applying them is two map lookups per block. Per phase the maps hold the rules active in that phase
 * (phases.ruleMode): CARRY = every phase so far, the latest phase's rule winning per block; ISOLATED = that phase only.
 */
public final class BlockRules {

    public enum Liquid { NONE, WATER, LAVA, OTHER }

    /** One conversion: the result, and the (0-based) phase whose rule it is, which sets when it is due. */
    public static final class Step {
        public final IBlockState target;
        public final int phase;

        Step(IBlockState target, int phase) {
            this.target = target;
            this.phase = phase;
        }
    }

    private final List<Map<IBlockState, Step>> convert = new ArrayList<>();
    private final List<Map<IBlockState, Integer>> destroy = new ArrayList<>(); // state -> earliest phase destroying it

    private BlockRules() {}

    public static BlockRules compile(SolarConfig.Phase[] phases) {
        BlockRules rules = new BlockRules();
        List<IBlockState> states = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) states.addAll(block.getBlockState().getValidStates());
        for (int i = 0; i < phases.length; i++) {
            String where = "phase_" + (i + 1);
            List<Predicate<IBlockState>> from = new ArrayList<>();
            List<IBlockState> to = new ArrayList<>();
            for (String line : phases[i].convert) {
                int arrow = line.indexOf("->");
                Predicate<IBlockState> selector = arrow < 0 ? null : selector(line.substring(0, arrow), where);
                IBlockState target = arrow < 0 ? null : target(line.substring(arrow + 2), where);
                if (selector == null || target == null) {
                    if (arrow < 0) SolarApocalypse.LOGGER.warn("{}.convert: '{}' has no '->'", where, line);
                    continue;
                }
                from.add(selector);
                to.add(target);
            }
            List<Predicate<IBlockState>> gone = new ArrayList<>();
            for (String s : phases[i].destroy) {
                Predicate<IBlockState> selector = selector(s, where);
                if (selector != null) gone.add(selector);
            }
            boolean carry = SolarConfig.ruleMode == SolarConfig.RuleMode.CARRY && i > 0;
            Map<IBlockState, Step> conversions = carry ? new IdentityHashMap<>(rules.convert.get(i - 1)) : new IdentityHashMap<>();
            Map<IBlockState, Integer> removals = carry ? new IdentityHashMap<>(rules.destroy.get(i - 1)) : new IdentityHashMap<>();
            for (IBlockState state : states) {
                if (state.getMaterial() == Material.AIR) continue;
                for (int r = 0; r < from.size(); r++) {
                    if (from.get(r).test(state)) {
                        if (to.get(r) != state) conversions.put(state, new Step(to.get(r), i));
                        break;
                    }
                }
                for (Predicate<IBlockState> g : gone) {
                    if (g.test(state)) {
                        removals.putIfAbsent(state, i);
                        break;
                    }
                }
            }
            rules.convert.add(conversions);
            rules.destroy.add(removals);
        }
        return rules;
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
