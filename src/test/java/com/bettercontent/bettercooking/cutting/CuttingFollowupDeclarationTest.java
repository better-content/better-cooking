package com.bettercontent.bettercooking.cutting;

import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.ChoppingBoardBlock;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.world.level.block.DispenserBlock;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Required source-bound native declarations, not a substitute for running the authored world cases. */
class CuttingFollowupDeclarationTest {
    private ClassNode node(InputStream input) throws IOException {
        assertNotNull(input); try (input) { var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node; }
    }
    private ClassNode node(Class<?> type) throws IOException { return node(type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")); }
    private List<MethodInsnNode> calls(ClassNode node, String method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (MethodNode candidate : node.methods) if (candidate.name.equals(method)) for (AbstractInsnNode instruction : candidate.instructions)
            if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }
    @Test void actualMappedAndShippedLootHaveTwoLegacyRefundAccessors() throws Exception {
        assertEquals(2, calls(node(ChoppingBoardBlock.class), "getDrops").stream().filter(c -> c.name.equals("getCurrentCutStack")).count());
        String path = System.getProperty("better_cooking.rawKcJar"); assertNotNull(path, "Bind required exact raw KC JAR, do not skip this source gate");
        assertEquals("0eee5579b36746825d6586c65051d9e68fc84178a19dec4372fc9d64b8629d62",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(path)))));
        try (ZipFile zip = new ZipFile(path)) {
            var raw = node(zip.getInputStream(zip.getEntry("com/github/ysbbbbbb/kaleidoscopecookery/block/kitchen/ChoppingBoardBlock.class")));
            assertEquals(2, calls(raw, "m_49635_").stream().filter(c -> c.name.equals("getCurrentCutStack")).count());
            assertEquals(1, calls(raw, "m_49635_").stream().filter(c -> c.name.equals("getCurrentCutCount")).count());
        }
    }
    @Test void vanillaRouterHasOneInvocationAndLootPreviewHasNoDirectSelectionDeliveryOrCursorWrite() throws Exception {
        assertEquals(1, calls(node(DispenserBlock.class), "dispenseFrom").stream().filter(c -> c.owner.equals("net/minecraft/core/dispenser/DispenseItemBehavior") && c.name.equals("dispense")).count());
        assertFalse(calls(node(NativeCuttingRouting.class), "dispense").stream().anyMatch(c -> c.name.equals("registerBehavior")));
        var session = node(CuttingSession.class);
        assertFalse(calls(session, "previewDrops").stream().anyMatch(c -> c.name.equals("select") || c.name.equals("deliver") || c.name.equals("nextFloat")));
        MethodNode preview = session.methods.stream().filter(m -> m.name.equals("previewDrops")).findFirst().orElseThrow();
        for (AbstractInsnNode instruction : preview.instructions)
            if (instruction instanceof FieldInsnNode field) assertNotEquals(Opcodes.PUTFIELD, field.getOpcode(), "Pure preview must not mutate live session fields");
        assertTrue(calls(session, "previewDrops").stream().anyMatch(c -> c.owner.equals("com/bettercontent/betterbumblezonecrops/api/CultivarAcquisition") && c.name.equals("restrictNewOutput")));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Bind exact pinned raw KC JAR"); System.setProperty("better_cooking.rawKcJar", args[0]);
        var test = new CuttingFollowupDeclarationTest(); test.actualMappedAndShippedLootHaveTwoLegacyRefundAccessors();
        test.vanillaRouterHasOneInvocationAndLootPreviewHasNoDirectSelectionDeliveryOrCursorWrite();
        System.out.println("CuttingFollowupDeclarationTest: 2 PASS (actual shipped+mapped drop and vanilla-router declarations, pure preview bytecode; not native acceptance)");
    }
}
