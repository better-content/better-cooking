package com.bettercontent.bettercooking.kitchen;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads actual dependency bytecode, never initializes Minecraft or follows inherited method lookup. */
public final class KitchenNativeDeclarationTest {
    private static final String REMOVE = "onRemove(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V";
    private static final String BEHAVIOUR = "net/minecraft/world/level/block/state/BlockBehaviour";
    private static final String BLOCK = "net/minecraft/world/level/block/Block";
    private static final String PREFIX = "com/bettercontent/bettercooking/mixin/";
    private record Bytecode(String name, Set<String> declarations, List<String> mixinTargets, List<String> injectSelectors) {}
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Bytecode read(String name) throws IOException {
        Set<String> declarations = new HashSet<>(); List<String> targets = new ArrayList<>(); List<String> selectors = new ArrayList<>();
        try (var stream = KitchenNativeDeclarationTest.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Required pinned class resource missing: " + name);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitArray(String key) {
                            if (!key.equals("value") && !key.equals("targets")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String ignored, Object value) {
                                    targets.add(value instanceof Type type ? type.getInternalName() : value.toString().replace('.', '/'));
                                }
                            };
                        }
                    };
                }
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    declarations.add(method + descriptor);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (!annotation.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public AnnotationVisitor visitArray(String key) {
                                    if (!key.equals("method")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String ignored, Object value) { selectors.add(value.toString()); }
                                    };
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return new Bytecode(name, declarations, targets, selectors);
    }
    private static void declared(Bytecode target, String selector) {
        long matches = target.declarations().stream().filter(method -> selector.contains("(") ? method.equals(selector) : method.startsWith(selector + "(")).count();
        check(matches == 1, "Injector must match exactly one DECLARED method, not an inherited phantom: " + target.name() + "." + selector + " matches=" + matches);
    }
    @Test void destructionTargetsActualDeclaringOwner() throws IOException {
        Bytecode mixin = read(PREFIX + "KitchenBoardBlockMixin");
        check(mixin.mixinTargets().equals(List.of(BEHAVIOUR)), "Board destruction mixin must target BlockBehaviour, not Block or KC's inheriting block");
        check(mixin.injectSelectors().equals(List.of(REMOVE)), "Board destruction selector must pin its complete native descriptor");
        declared(read(BEHAVIOUR), REMOVE);
    }
    @Test void inheritedBlockTargetIsRejected() throws IOException {
        Bytecode block = read(BLOCK);
        check(!block.declarations().contains(REMOVE), "Pinned Block unexpectedly gained an onRemove declaration; inspect changed dependency");
        boolean rejected = false;
        try { declared(block, REMOVE); } catch (AssertionError expected) { rejected = true; }
        check(rejected, "Compatibility check must reject prior phantom Block.onRemove target");
    }
    @Test void everyKitchenInjectionResolvesToActualDeclaration() throws IOException {
        int selectors = 0;
        for (String name : List.of("KitchenBoardMixin", "KitchenMatMixin", "KitchenBoardBlockMixin", "KitchenMatBlockMixin")) {
            Bytecode mixin = read(PREFIX + name);
            check(!mixin.mixinTargets().isEmpty() && !mixin.injectSelectors().isEmpty(), "Missing concrete kitchen targets/selectors: " + name);
            for (String target : mixin.mixinTargets()) for (String selector : mixin.injectSelectors()) { declared(read(target), selector); selectors++; }
        }
        check(selectors == 7, "Expected all seven native injection selectors (2 board, 3 mat, 2 destruction), got " + selectors);
    }
    public static void main(String[] args) throws IOException {
        var tests = new KitchenNativeDeclarationTest();
        tests.destructionTargetsActualDeclaringOwner(); tests.inheritedBlockTargetIsRejected(); tests.everyKitchenInjectionResolvesToActualDeclaration();
        System.out.println("Kitchen bytecode declaration controls PASS3; seven selectors declared; phantom Block.onRemove rejected (not native/mixin application)");
    }
}
