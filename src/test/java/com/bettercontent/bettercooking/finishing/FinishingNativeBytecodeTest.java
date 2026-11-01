package com.bettercontent.bettercooking.finishing;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reads actual pinned mapped provider classes without Minecraft bootstrap. NOT transformer acceptance. */
public class FinishingNativeBytecodeTest {
    public static void main(String[] args) {
        var controls=new FinishingNativeBytecodeTest();
        controls.potLateCommitFollowsFinalSelection();controls.stockpotInputsRemainAtUniqueClearBoundary();
        controls.steamerCommitsOneSpecificSlotAndRetainsNativeFallback();controls.teaHasThreeStatusWritesAndActualLifecycleDeclarations();
        controls.kitchenEntitiesAreNotInventedContainersButRealGettersAreTyped();controls.furnaceAssemblesButSkilletRequiresRealCampfireSubtype();
        System.out.println("PASS 6 actual pinned mapped-bytecode controls; NOT transformed/native acceptance");
    }
    private static final String KC="com/github/ysbbbbbb/kaleidoscopecookery/";
    private static final String BE=KC+"blockentity/kitchen/";
    private static final String LEVEL="Lnet/minecraft/world/level/Level;";
    record Op(int opcode,String owner,String name,String descriptor) {}
    record Method(String name,String descriptor,List<Op> code) {}
    record Type(String parent,List<String> interfaces,List<Method> methods) {}
    private static Type read(String name) {
        try(InputStream input=FinishingNativeBytecodeTest.class.getClassLoader().getResourceAsStream(name+".class")) {
            assertNotNull(input,"Missing actual mapped provider: "+name);
            List<String> interfaces=new ArrayList<>();List<Method> methods=new ArrayList<>();String[] parent={null};
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                public void visit(int v,int a,String n,String signature,String superName,String[] faces) {
                    parent[0]=superName;interfaces.addAll(List.of(faces));
                }
                public MethodVisitor visitMethod(int a,String n,String d,String s,String[] exceptions) {
                    List<Op> code=new ArrayList<>();methods.add(new Method(n,d,code));
                    return new MethodVisitor(Opcodes.ASM9) {
                        public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf) {code.add(new Op(op,owner,name,desc));}
                        public void visitFieldInsn(int op,String owner,String name,String desc) {code.add(new Op(op,owner,name,desc));}
                        public void visitTypeInsn(int op,String type) {code.add(new Op(op,type,"", ""));}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            return new Type(parent[0],interfaces,methods);
        } catch(Exception failure) {throw new AssertionError(name,failure);}
    }
    private static Method method(String owner,String name,String descriptor) {
        return read(owner).methods.stream().filter(m->m.name.equals(name)&&m.descriptor.equals(descriptor)).findFirst()
            .orElseThrow(()->new AssertionError("No actual declaring method "+owner+"."+name+descriptor));
    }
    private static List<Integer> positions(Method method,int opcode,String owner,String name) {
        List<Integer> indices=new ArrayList<>();for(int i=0;i<method.code.size();i++) {
            Op op=method.code.get(i);if(op.opcode==opcode&&op.owner.equals(owner)&&op.name.equals(name))indices.add(i);
        } return indices;
    }
    private static int single(Method method,int opcode,String owner,String name) {
        List<Integer> found=positions(method,opcode,owner,name);assertEquals(1,found.size(),owner+"."+name);return found.get(0);
    }
    @Test void potLateCommitFollowsFinalSelection() {
        String owner=BE+"PotBlockEntity";
        Method cook=method(owner,"tickCooking","("+LEVEL+"Lnet/minecraft/util/RandomSource;)V");
        int late=single(cook,Opcodes.PUTFIELD,owner,"currentTick");
        assertTrue(positions(cook,Opcodes.PUTFIELD,owner,"result").stream().allMatch(index->index<late));
        assertTrue(single(cook,Opcodes.PUTFIELD,owner,"status")<late);
        assertTrue(single(cook,Opcodes.INVOKEVIRTUAL,owner,"setChanged")>late);
    }
    @Test void stockpotInputsRemainAtUniqueClearBoundary() {
        String owner=BE+"StockpotBlockEntity";Method tick=method(owner,"tick","("+LEVEL+")V");
        int clear=single(tick,Opcodes.INVOKEVIRTUAL,"net/minecraft/core/NonNullList","clear");
        assertTrue(positions(tick,Opcodes.PUTFIELD,owner,"result").isEmpty(),"result was selected in earlier native setRecipe, not reassembled at commit");
        assertTrue(single(tick,Opcodes.INVOKEVIRTUAL,owner,"setRecipe")<clear);
        assertTrue(positions(tick,Opcodes.PUTFIELD,owner,"status").stream().anyMatch(index->index<clear));
    }
    @Test void steamerCommitsOneSpecificSlotAndRetainsNativeFallback() {
        String owner=BE+"SteamerBlockEntity";
        Method cook=method(owner,"cookingTick","("+LEVEL+"Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;L"+owner+";)V");
        int set=single(cook,Opcodes.INVOKEVIRTUAL,"net/minecraft/core/NonNullList","set");
        Op call=cook.code.get(set);assertEquals("(ILjava/lang/Object;)Ljava/lang/Object;",call.descriptor);
        assertTrue(single(cook,Opcodes.INVOKEVIRTUAL,"java/util/Optional","orElse")<set);
    }
    @Test void teaHasThreeStatusWritesAndActualLifecycleDeclarations() {
        String owner=BE+"TeapotBlockEntity";Method tick=method(owner,"tick","("+LEVEL+")V");
        List<Integer> writes=positions(tick,Opcodes.PUTFIELD,owner,"status");assertEquals(3,writes.size());
        assertTrue(positions(tick,Opcodes.PUTFIELD,owner,"result").stream().allMatch(index->index<writes.get(2)));
        method(owner,"saveAdditional","(Lnet/minecraft/nbt/CompoundTag;)V");
        method(owner,"load","(Lnet/minecraft/nbt/CompoundTag;)V");
    }
    private static boolean container(String name,Set<String> seen) {
        if(name==null||!seen.add(name))return false;
        if(name.equals("net/minecraft/world/Container"))return true;
        Type type=read(name);if(container(type.parent,seen))return true;
        return type.interfaces.stream().anyMatch(face->container(face,seen));
    }
    @Test void kitchenEntitiesAreNotInventedContainersButRealGettersAreTyped() {
        for(String entity:List.of("PotBlockEntity","StockpotBlockEntity","SteamerBlockEntity","TeapotBlockEntity"))
            assertFalse(container(BE+entity,new HashSet<>()),entity);
        method(BE+"PotBlockEntity","getContainer","()Lnet/minecraft/world/SimpleContainer;");
        String stock=KC+"crafting/container/StockpotContainer";
        method(BE+"StockpotBlockEntity","getContainer","()L"+stock+";");
        assertTrue(container(stock,new HashSet<>()));
    }
    @Test void furnaceAssemblesButSkilletRequiresRealCampfireSubtype() {
        String furnace="net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity";
        Method burn=method(furnace,"burn","(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/crafting/Recipe;Lnet/minecraft/core/NonNullList;I)Z");
        single(burn,Opcodes.INVOKEINTERFACE,"net/minecraft/world/item/crafting/Recipe","assemble");
        String skillet="vectorwing/farmersdelight/common/block/entity/SkilletBlockEntity";
        Method matching=read(skillet).methods.stream().filter(m->m.name.equals("getMatchingRecipe")).findFirst().orElseThrow();
        assertTrue(matching.code.stream().anyMatch(op->op.opcode==Opcodes.INSTANCEOF&&op.owner.equals("net/minecraft/world/item/crafting/CampfireCookingRecipe")));
        Method output=method(skillet,"cookAndOutputItems","(Lnet/minecraft/world/item/ItemStack;"+LEVEL+")V");
        single(output,Opcodes.INVOKEVIRTUAL,"net/minecraft/world/item/crafting/CampfireCookingRecipe","assemble");
    }
}
