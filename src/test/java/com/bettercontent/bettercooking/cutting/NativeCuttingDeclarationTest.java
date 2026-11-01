package com.bettercontent.bettercooking.cutting;

import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.block.dispenser.ChoppingBoardDispenseBehavior;
import com.bettercontent.bettercooking.mixin.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeCuttingDeclarationTest {
    private ClassNode node(Class<?> type) throws IOException {
        try (InputStream input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input); ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private List<MethodInsnNode> calls(ClassNode node, String method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (MethodNode candidate : node.methods) if (candidate.name.equals(method))
            for (AbstractInsnNode instruction : candidate.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }
    @Test void pinnedNativeBoardPaysBeforeActualResultsAndDoesNotUseAssembleOrQualityReroll() throws Exception {
        var nativeBoard = node(ChoppingBoardBlockEntity.class); var calls = calls(nativeBoard, "onPutItem");
        int split = -1, results = -1;
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).name.equals("split")) split = i;
            if (calls.get(i).name.equals("getResults")) results = i;
        }
        assertTrue(split >= 0 && results > split);
        assertEquals(1, calls.stream().filter(c -> c.name.equals("getResults")).count());
        assertFalse(calls.stream().anyMatch(c -> c.name.equals("assemble") || c.name.equals("getResultItem") || c.owner.contains("QualityEvaluator")));
        assertEquals(1, calls(nativeBoard, "onCutItem").stream().filter(c -> c.name.equals("popResource")).count());
    }
    @Test void pinnedDispenserContainsRedundantShrinkAndOwnedHooksCoverBothShippedNamespaces() throws Exception {
        var calls = calls(node(ChoppingBoardDispenseBehavior.class), "execute");
        assertEquals(1, calls.stream().filter(c -> c.name.equals("onPutItem")).count());
        assertEquals(1, calls.stream().filter(c -> c.name.equals("shrink")).count());
        var output = node(MixinNativeCuttingOutput.class);
        assertTrue(output.methods.stream().filter(m -> m.name.equals("cooking$save")).anyMatch(m -> annotations(m).contains("m_183515_")));
        assertTrue(output.methods.stream().filter(m -> m.name.equals("cooking$load")).anyMatch(m -> annotations(m).contains("m_142466_")));
        var dispenser = node(MixinNativeCuttingDispenser.class);
        assertTrue(dispenser.methods.stream().filter(m -> m.name.equals("cooking$begin")).anyMatch(m -> annotations(m).contains("m_7498_")));
        assertTrue(dispenser.methods.stream().filter(m -> m.name.equals("cooking$mappedOuterDebit")).anyMatch(m -> annotations(m).contains("shrink(I)V")));
        assertTrue(dispenser.methods.stream().filter(m -> m.name.equals("cooking$runtimeOuterDebit")).anyMatch(m -> annotations(m).contains("m_41774_(I)V")));
    }
    private String annotations(MethodNode method) {
        StringBuilder text = new StringBuilder();
        if (method.visibleAnnotations != null) for (AnnotationNode annotation : method.visibleAnnotations) text.append(values(annotation));
        if (method.invisibleAnnotations != null) for (AnnotationNode annotation : method.invisibleAnnotations) text.append(values(annotation));
        return text.toString();
    }
    private String values(Object object) {
        if (object instanceof AnnotationNode node) return node.desc + values(node.values);
        if (object instanceof List<?> list) return list.stream().map(this::values).reduce("", (a,b) -> a + " " + b);
        return String.valueOf(object);
    }
    public static void main(String[] args) throws Exception {
        var test = new NativeCuttingDeclarationTest(); test.pinnedNativeBoardPaysBeforeActualResultsAndDoesNotUseAssembleOrQualityReroll();
        test.pinnedDispenserContainsRedundantShrinkAndOwnedHooksCoverBothShippedNamespaces();
        System.out.println("NativeCuttingDeclarationTest: 2 PASS (actual pinned mapped native ASM; not mixin/native execution)");
    }
}
