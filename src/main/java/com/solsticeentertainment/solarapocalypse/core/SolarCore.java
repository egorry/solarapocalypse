package com.solsticeentertainment.solarapocalypse.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Map;

/**
 * The mod's two patches, as a plain Forge coremod (no mixin mod needed). With Cubic Chunks installed, the end of its
 * Cube.trackSurface (a new or reloaded cube is lit and in its column's height map, and no player has been sent it yet)
 * calls CubeEngine.surfaceTracked, which brings the cube up to date there (performance.updateBeforeSending); nothing of
 * it without Cubic Chunks. On clients, Minecraft's sky drawing (RenderGlobal.renderSky) takes the sun's size from
 * client.SkyHook (sun.grow); nothing else in it changes. Should either method have changed (another Cubic Chunks
 * version, a mod rewriting the sky drawing), its patch is skipped with a warning: cubes then change after players get
 * them, as with the option off, or the sun keeps its size.
 */
@IFMLLoadingPlugin.Name("SolarApocalypseCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("com.solsticeentertainment.solarapocalypse.core.")
@IFMLLoadingPlugin.SortingIndex(1001) // after Forge's deobfuscation: Minecraft's methods have their SRG names
public final class SolarCore implements IFMLLoadingPlugin, IClassTransformer {

    private static final String CUBE = "io.github.opencubicchunks.cubicchunks.core.world.cube.Cube";
    private static final String SKY = "net.minecraft.client.renderer.RenderGlobal";

    /** Whether the sun's size was patched into the sky drawing (sun.grow; clients only). */
    public static boolean skyPatched;

    /** Whether Cubic Chunks' Cube class was patched (for /solar status and the self-test). */
    public static boolean patched;

    /** Cubic Chunks' Cube class loaded without the method to patch (a version the hook does not fit). */
    public static boolean missing;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes != null && SKY.equals(transformedName)) return patchSky(bytes);
        if (bytes == null || !CUBE.equals(transformedName)) return bytes;
        try {
            return patch(bytes);
        } catch (RuntimeException e) { // never stop the game over it: the class stays as Cubic Chunks made it
            patched = false;
            missing = true;
            LogManager.getLogger("solarapocalypse").error("Could not patch Cubic Chunks' Cube class", e);
            return bytes;
        }
    }

    private static byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!method.name.equals("trackSurface") || !method.desc.equals("()V")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.RETURN) continue;
                InsnList call = new InsnList();
                call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/solsticeentertainment/solarapocalypse/cc/CubeEngine",
                        "surfaceTracked", "(Lio/github/opencubicchunks/cubicchunks/api/world/ICube;)V", false));
                method.instructions.insertBefore(insn, call);
                patched = true;
            }
        }
        if (!patched) {
            missing = true;
            LogManager.getLogger("solarapocalypse").warn("Cubic Chunks' Cube.trackSurface not found: cubes change after players get them");
            return bytes;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /**
     * renderSky(float, int) (SRG func_174976_a): the sun's 'float size = 30.0F' just before it binds SUN_TEXTURES
     * (field_110928_i) becomes SkyHook.sun(30.0F). The anaglyph colours' 30.0F come earlier and the moon's 20.0F later.
     */
    static byte[] patchSky(byte[] bytes) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            for (MethodNode method : node.methods) {
                if (!method.name.equals("renderSky") && !method.name.equals("func_174976_a") || !method.desc.equals("(FI)V")) continue;
                AbstractInsnNode size = null;
                int since = 0;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() < 0) continue; // labels, line numbers, frames
                    since++;
                    if (insn instanceof LdcInsnNode && Float.valueOf(30.0F).equals(((LdcInsnNode) insn).cst)) {
                        size = insn;
                        since = 0;
                    } else if (size != null && since <= 6 && insn.getOpcode() == Opcodes.GETSTATIC
                            && ("SUN_TEXTURES".equals(((FieldInsnNode) insn).name) || "field_110928_i".equals(((FieldInsnNode) insn).name))) {
                        method.instructions.insert(size, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "com/solsticeentertainment/solarapocalypse/client/SkyHook", "sun", "(F)F", false));
                        skyPatched = true;
                        break;
                    }
                }
            }
            if (!skyPatched) {
                LogManager.getLogger("solarapocalypse").warn("Minecraft's sky drawing has no sun size to patch (another mod rewrote it?): sun.grow cannot show");
                return bytes;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException e) { // never stop the game over it: the sun keeps its size
            skyPatched = false;
            LogManager.getLogger("solarapocalypse").error("Could not patch Minecraft's sky drawing", e);
            return bytes;
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{SolarCore.class.getName()};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
