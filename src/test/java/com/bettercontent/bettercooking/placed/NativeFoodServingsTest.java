package com.bettercontent.bettercooking.placed;

import com.github.ysbbbbbb.kaleidoscopecookery.block.food.*;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.NinePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.food.FoodProperties;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeFoodServingsTest {
    private static final String PREFIX = "com/github/ysbbbbbb/kaleidoscopecookery/block/food/";
    private ClassNode node(String name) throws IOException {
        try (InputStream input = FoodBiteBlock.class.getResourceAsStream("/" + PREFIX + name + ".class")) {
            assertNotNull(input); ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private List<MethodInsnNode> calls(ClassNode type, String method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (MethodNode candidate : type.methods) if (candidate.name.equals(method))
            for (AbstractInsnNode instruction : candidate.instructions)
                if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }
    @Test void pinnedBaseAndLargeUseOwnExactlyTwoNativeEatCalls() throws Exception {
        for (String name : List.of("FoodBiteBlock", "FoodBiteThreeByThreeBlock")) {
            var calls = calls(node(name), "use").stream().filter(c -> c.name.equals("eat") && c.owner.equals(PREFIX + name)).toList();
            assertEquals(2, calls.size(), name);
            assertTrue(calls.stream().allMatch(c -> c.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionResult;")));
        }
        assertFalse(node("FoodBiteOneByTwoBlock").methods.stream().anyMatch(m -> m.name.equals("use") || m.name.equals("eat")));
        assertFalse(node("FoodBiteThreeByThreeBlock").methods.stream().anyMatch(m -> m.name.equals("eat")));
    }
    @Test void pinnedPlacementHierarchyRequiresOnlyPairReturnHook() throws Exception {
        assertEquals(0, calls(node("FoodBiteOneByTwoBlock"), "setPlacedBy").stream().filter(c -> c.name.equals("setPlacedBy")).count());
        assertEquals(1, calls(node("FoodBiteThreeByThreeBlock"), "setPlacedBy").stream()
                .filter(c -> c.name.equals("setPlacedBy") && c.owner.equals(PREFIX + "FoodBiteBlock") && c.getOpcode() == Opcodes.INVOKESPECIAL).count());
        assertTrue(calls(node("FoodBiteOneByTwoBlock"), "setPlacedBy").stream().anyMatch(c -> c.owner.equals("net/minecraft/core/Direction") && c.name.equals("getClockWise")));
        assertFalse(node("FoodBiteBlock").methods.stream().anyMatch(m -> m.name.equals("setPlacedBy")));
    }
    @Test void qualityRatioAndNativeEffectDrawCallsitesRemainSingle() throws Exception {
        var calls = calls(node("FoodBiteBlock"), "eat");
        assertEquals(1, calls.stream().filter(c -> c.name.equals("getRatio") && c.desc.equals("()D")).count());
        assertEquals(1, calls.stream().filter(c -> c.owner.equals("net/minecraft/world/food/FoodData") && c.name.equals("eat") && c.desc.equals("(IF)V")).count());
        assertEquals(1, calls.stream().filter(c -> c.name.equals("addEffect")).count());
        assertEquals(2, calls.stream().filter(c -> c.owner.equals("net/minecraft/util/RandomSource") && c.name.equals("nextFloat")).count()); // effect chance + eat-sound pitch
    }
    @Test void pairAndNinePartAnchorsRespectEveryFacingAndActualWorldOffset() {
        BlockPos anchor = new BlockPos(9, 70, -11);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(anchor, NativeFoodServings.pairAnchor(anchor, facing, false));
            assertEquals(anchor, NativeFoodServings.pairAnchor(anchor.relative(facing.getClockWise()), facing, true));
            for (NinePart part : NinePart.values())
                assertEquals(anchor, NativeFoodServings.largeAnchor(anchor.offset(part.getPosX(), 0, part.getPosY()), part.getPosX(), part.getPosY()));
            for (int bites = 0; bites <= 8; bites++) assertEquals(8 - bites, NativeFoodServings.remaining(8, bites));
        }
        assertThrows(IllegalArgumentException.class, () -> NativeFoodServings.largeAnchor(anchor, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> NativeFoodServings.remaining(8, 9));
    }
    public static void main(String[] args) throws Exception {
        var tests = new NativeFoodServingsTest();
        tests.pinnedBaseAndLargeUseOwnExactlyTwoNativeEatCalls();
        tests.pinnedPlacementHierarchyRequiresOnlyPairReturnHook();
        tests.qualityRatioAndNativeEffectDrawCallsitesRemainSingle();
        tests.pairAndNinePartAnchorsRespectEveryFacingAndActualWorldOffset();
        System.out.println("NativeFoodServingsTest: 4 PASS (real pinned ASM and geometry JVM controls, not native acceptance)");
    }
}
