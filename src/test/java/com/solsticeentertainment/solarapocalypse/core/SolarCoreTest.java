package com.solsticeentertainment.solarapocalypse.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sun's size patch (sun.grow) on Minecraft's sky drawing, as in the dev client (MCP names) and in a pack (SRG names). */
class SolarCoreTest {

    private static final String CLASS = "net/minecraft/client/renderer/RenderGlobal.class";

    @Test
    void patchesTheSunInTheDevClient() throws IOException {
        try (InputStream in = SolarCoreTest.class.getResourceAsStream("/" + CLASS)) {
            Assumptions.assumeTrue(in != null, "Minecraft's client classes are not on the test classpath");
            check(read(in), "renderSky");
        }
    }

    @Test
    void patchesTheSunInAPack() throws IOException {
        File jar = new File("build/rfg/srg_merged_minecraft.jar"); // RetroFuturaGradle's Minecraft with SRG member names
        Assumptions.assumeTrue(jar.isFile(), "no " + jar);
        try (ZipFile zip = new ZipFile(jar); InputStream in = zip.getInputStream(zip.getEntry(CLASS))) {
            check(read(in), "func_174976_a");
        }
    }

    /** One call to SkyHook.sun, right after the 30.0F that SUN_TEXTURES follows; the moon's 20.0F untouched. */
    private static void check(byte[] bytes, String name) {
        SolarCore.skyPatched = false;
        byte[] patched = SolarCore.patchSky(bytes);
        assertTrue(SolarCore.skyPatched);
        ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);
        MethodNode sky = null;
        for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals("(FI)V")) sky = m;
        assertTrue(sky != null, name);
        int calls = 0, moons = 0;
        for (AbstractInsnNode insn : sky.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && Float.valueOf(20.0F).equals(((LdcInsnNode) insn).cst)) moons++;
            if (!(insn instanceof MethodInsnNode) || !((MethodInsnNode) insn).name.equals("sun")) continue;
            calls++;
            assertEquals(Float.valueOf(30.0F), ((LdcInsnNode) real(insn, -1)).cst);
            assertEquals(Opcodes.FSTORE, real(insn, 1).getOpcode());
            String texture = ((FieldInsnNode) real(insn, 4)).name;
            assertTrue(texture.equals("SUN_TEXTURES") || texture.equals("field_110928_i"), texture);
        }
        assertEquals(1, calls);
        assertEquals(1, moons);
    }

    /** The k-th real instruction after (k > 0) or before (k < 0) one, skipping labels and line numbers. */
    private static AbstractInsnNode real(AbstractInsnNode insn, int k) {
        for (int step = 0; step < Math.abs(k); ) {
            insn = k > 0 ? insn.getNext() : insn.getPrevious();
            if (insn.getOpcode() >= 0) step++;
        }
        return insn;
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
