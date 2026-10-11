package com.solsticeentertainment.solarapocalypse.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Map;

/**
 * The mod's one patch, as a plain Forge coremod (no mixin mod needed). With Cubic Chunks installed, the end of its
 * Cube.trackSurface (a new or reloaded cube is lit and in its column's height map, and no player has been sent it yet)
 * calls CubeEngine.surfaceTracked, which brings the cube up to date there (performance.updateBeforeSending). Nothing
 * else is touched, and nothing at all without Cubic Chunks. Should Cubic Chunks change that method, the patch is
 * skipped with a warning and cubes change after players get them, as with the option off.
 */
@IFMLLoadingPlugin.Name("SolarApocalypseCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("com.solsticeentertainment.solarapocalypse.core.")
public final class SolarCore implements IFMLLoadingPlugin, IClassTransformer {

    private static final String CUBE = "io.github.opencubicchunks.cubicchunks.core.world.cube.Cube";

    /** Whether Cubic Chunks' Cube class was patched (for /solar status and the self-test). */
    public static boolean patched;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !CUBE.equals(transformedName)) return bytes;
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
            LogManager.getLogger("solarapocalypse").warn("Cubic Chunks' Cube.trackSurface not found: cubes change after players get them");
            return bytes;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
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
