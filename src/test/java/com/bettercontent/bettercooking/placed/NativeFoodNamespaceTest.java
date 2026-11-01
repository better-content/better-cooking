package com.bettercontent.bettercooking.placed;

import com.bettercontent.bettercooking.mixin.*;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Declarations from BOTH actual pinned shipped SRG and mapped bytecode, not source-name guesses. */
class NativeFoodNamespaceTest {
    private static final String ROOT = "com/github/ysbbbbbb/kaleidoscopecookery/block/food/";
    private static final String SHA = "0eee5579b36746825d6586c65051d9e68fc84178a19dec4372fc9d64b8629d62";
    private ClassNode read(InputStream stream) throws IOException {
        assertNotNull(stream); try (stream) { ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node; }
    }
    private ClassNode mapped(String name) throws IOException { return read(FoodBiteBlock.class.getResourceAsStream("/" + ROOT + name + ".class")); }
    private ClassNode own(Class<?> type) throws IOException { return read(type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")); }
    private long calls(ClassNode node, String method, String owner, String target) {
        long count = 0;
        for (MethodNode candidate : node.methods) if (candidate.name.equals(method)) for (AbstractInsnNode instruction : candidate.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(target)) count++;
        return count;
    }
    private void verify(ClassNode base, ClassNode pair, ClassNode large, String use, String placed) {
        assertEquals(2, calls(base, use, ROOT + "FoodBiteBlock", "eat"));
        assertEquals(2, calls(large, use, ROOT + "FoodBiteThreeByThreeBlock", "eat"));
        assertFalse(pair.methods.stream().anyMatch(m -> m.name.equals(use) || m.name.equals("eat")));
        assertFalse(base.methods.stream().anyMatch(m -> m.name.equals(placed)));
        assertEquals(0, calls(pair, placed, ROOT + "FoodBiteBlock", placed));
        assertEquals(1, calls(large, placed, ROOT + "FoodBiteBlock", placed));
        assertTrue(pair.methods.stream().anyMatch(m -> m.name.equals(placed) && m.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V")));
        assertEquals(1, calls(base, "eat", "com/github/ysbbbbbb/kaleidoscopecookery/item/quality/Quality", "getRatio"));
    }
    @Test void actualMappedNativeDeclarationsAndStrictAliases() throws Exception {
        verify(mapped("FoodBiteBlock"), mapped("FoodBiteOneByTwoBlock"), mapped("FoodBiteThreeByThreeBlock"), "use", "setPlacedBy");
        checkAliases(NativeFoodUseMixin.class, "cooking$nativeBite", "use", "m_6227_", 2);
        checkAliases(NativeLargeFoodUseMixin.class, "cooking$nativeLargeBite", "use", "m_6227_", 2);
        checkAliases(NativeFoodPlacementMixin.class, "cooking$placedPair", "setPlacedBy", "m_6402_", 1);
        assertEquals("net/minecraft/world/level/block/Block", mapped("FoodBlock").superName);
        assertFalse(mapped("FoodBlock").methods.stream().anyMatch(m -> m.name.equals("setPlacedBy")));
    }
    private void checkAliases(Class<?> type, String method, String mapped, String raw, int required) throws Exception {
        var node = own(type); MethodNode target = node.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (target.visibleAnnotations != null) annotations.addAll(target.visibleAnnotations);
        if (target.invisibleAnnotations != null) annotations.addAll(target.invisibleAnnotations);
        AnnotationNode injection = annotations.stream().filter(a -> a.desc.endsWith("/Inject;") || a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        Map<String,Object> values = new HashMap<>();
        for (int i = 0; i < injection.values.size(); i += 2) values.put((String) injection.values.get(i), injection.values.get(i+1));
        assertEquals(List.of(mapped, raw), values.get("method")); assertEquals(required, values.get("require"));
    }
    @Test void actualPinnedRawJarDeclarationsWhenExplicitlyBound() throws Exception {
        String location = System.getProperty("better_cooking.rawKcJar");
        assertNotNull(location, "Required binary gate: bind -Dbetter_cooking.rawKcJar to the exact pinned source JAR; do not skip shipped-namespace checks");
        raw(Path.of(location));
    }
    private void raw(Path path) throws Exception {
        assertEquals(SHA, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        try (ZipFile zip = new ZipFile(path.toFile())) {
            verify(read(zip.getInputStream(zip.getEntry(ROOT + "FoodBiteBlock.class"))),
                    read(zip.getInputStream(zip.getEntry(ROOT + "FoodBiteOneByTwoBlock.class"))),
                    read(zip.getInputStream(zip.getEntry(ROOT + "FoodBiteThreeByThreeBlock.class"))), "m_6227_", "m_6402_");
            ClassNode parent = read(zip.getInputStream(zip.getEntry(ROOT + "FoodBlock.class")));
            assertEquals("net/minecraft/world/level/block/Block", parent.superName);
            assertFalse(parent.methods.stream().anyMatch(m -> m.name.equals("m_6402_")));
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Bind exact raw pinned KC JAR path");
        var test = new NativeFoodNamespaceTest(); test.actualMappedNativeDeclarationsAndStrictAliases(); test.raw(Path.of(args[0]));
        System.out.println("NativeFoodNamespaceTest: 2 PASS (actual SHA-bound shipped SRG AND mapped declarations; strict require1/2, not native execution)");
    }
}
