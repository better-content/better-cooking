package com.bettercontent.bettercooking.drinks;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Actual mapped provider/compiled-source bytecode reads, NOT native execution or mock entities. */
public final class NativeDrinkInputFrameTest {
    private static final String KC="com/github/ysbbbbbb/kaleidoscopecookery/";
    private static ClassNode read(String name)throws IOException{
        try(var input=NativeDrinkInputFrameTest.class.getClassLoader().getResourceAsStream(name+".class")){
            assertNotNull(input,"Missing actual class resource "+name);ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);return node;
        }
    }
    private static MethodNode method(ClassNode type,String name){return type.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static boolean calls(MethodNode method,String owner,String name){
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;return false;
    }
    private static Object value(AnnotationNode annotation,String name){
        if(annotation.values!=null)for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals(name))return annotation.values.get(i+1);return null;
    }
    private static void cancellableHead(MethodNode method,String selector){
        java.util.List<AnnotationNode> annotations=new java.util.ArrayList<>();
        if(method.visibleAnnotations!=null)annotations.addAll(method.visibleAnnotations);if(method.invisibleAnnotations!=null)annotations.addAll(method.invisibleAnnotations);
        AnnotationNode inject=annotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        assertEquals(java.util.List.of(selector),value(inject,"method"));assertEquals(Boolean.TRUE,value(inject,"cancellable"));assertEquals(1,value(inject,"require"));
        var ats=(java.util.List<?>)value(inject,"at");assertEquals(1,ats.size());assertEquals("HEAD",value((AnnotationNode)ats.get(0),"value"));
    }
    private static int callIndex(MethodNode method,String owner,String name){
        int index=0;for(var instruction:method.instructions){if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return index;index++;}return -1;
    }
    @Test void safeOnlyGuardsAreStrictCancellableHeadsBeforeNativePayment()throws IOException{
        String safety="com/bettercontent/bettercooking/drinks/NativeDrinkSafety",capture="com/bettercontent/bettercooking/drinks/NativeDrinkCapture";
        ClassNode tea=read("com/bettercontent/bettercooking/mixin/NativeTeapotBaseCaptureMixin");
        cancellableHead(method(tea,"cooking$beforeFill"),"addTeaFluid(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z");
        MethodNode lambda=tea.methods.stream().filter(m->calls(m,safety,"allowsFluidItem")).findFirst().orElseThrow();
        assertTrue(callIndex(lambda,safety,"allowsFluidItem")<callIndex(lambda,capture,"base"));
        assertTrue(calls(lambda,"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable","setReturnValue"));
        ClassNode pot=read("com/bettercontent/bettercooking/mixin/NativeStockpotBaseCaptureMixin");MethodNode guard=method(pot,"cooking$beforeBase");
        cancellableHead(guard,"addSoupBase(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z");
        assertTrue(callIndex(guard,safety,"allowsSoupItem")<callIndex(guard,capture,"base"));
        assertTrue(calls(guard,"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable","setReturnValue"));
        ClassNode nativeTea=read(KC+"blockentity/kitchen/TeapotBlockEntity");
        assertTrue(nativeTea.methods.stream().anyMatch(m->m.name.startsWith("lambda$addTeaFluid$")&&calls(m,KC+"util/FluidUtils","emptyItem")));
        assertTrue(calls(method(read(KC+"blockentity/kitchen/StockpotBlockEntity"),"addSoupBase"),"net/minecraft/world/item/ItemStack","shrink"));
    }
    @Test void unknownWaterDripGuardIsBeforeNativeFieldCommit()throws IOException{
        ClassNode tea=read("com/bettercontent/bettercooking/mixin/NativeTeapotBaseCaptureMixin");MethodNode guard=method(tea,"cooking$beforeDrip");
        cancellableHead(guard,"receiveDripstoneFluid(Lnet/minecraft/world/level/material/Fluid;)Z");
        assertTrue(calls(guard,"com/bettercontent/bettercooking/drinks/NativeDrinkSafety","allowsDripstone"));
        assertTrue(calls(guard,"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable","setReturnValue"));
        ClassNode nativeTea=read(KC+"blockentity/kitchen/TeapotBlockEntity");boolean field=false;
        for(var instruction:method(nativeTea,"receiveDripstoneFluid").instructions)if(instruction instanceof FieldInsnNode f&&f.getOpcode()==org.objectweb.asm.Opcodes.PUTFIELD&&f.name.equals("teaFluidId"))field=true;
        assertTrue(field,"Actual pinned native drip commit frame changed");
    }
    @Test void safetyQueryUsesActualCopyAndNeverInvokesConsumptionOrEffects()throws IOException{
        ClassNode safety=read("com/bettercontent/bettercooking/drinks/NativeDrinkSafety");MethodNode query=method(safety,"allowsFluidItem");
        assertTrue(callIndex(query,"net/minecraft/world/item/ItemStack","copy")<callIndex(query,"dev/ghen/thirst/content/purity/WaterPurity","getPurity"));
        assertTrue(calls(query,"com/bettercontent/betterdrinkingwater/WaterQuality","normalize"));
        for(MethodNode method:safety.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call)
            assertFalse(call.name.equals("givePurityEffects")||call.name.equals("drink")||call.name.equals("sip")||call.name.equals("shrink")||call.name.equals("drain")||call.name.equals("post"),"Input guard acquired consumption/effect side effects");
    }
    @Test void v2TemplateParserUsesPublicPurityWithoutMetadataStripping()throws IOException{
        MethodNode parser=method(read("com/bettercontent/bettercooking/drinks/NativeDrinkIngredients"),"parseDefaults");
        assertTrue(calls(parser,"dev/ghen/thirst/content/purity/WaterPurity","addPurity"));
        assertTrue(calls(parser,"net/minecraft/nbt/CompoundTag","equals"),"Full expected native NBT equality missing");
        // Ordinary JUnit runtime deliberately does NOT load nativeFixture output/classes.
        for(var instruction:parser.instructions)if(instruction instanceof MethodInsnNode call)
            assertFalse(call.owner.equals("net/minecraft/nbt/CompoundTag")&&call.name.equals("remove"),"Template code strips metadata");
    }
    @Test void actualTeaInsertionCopiesOneThenDebitsAndAutomationHasOwnHook()throws IOException{
        ClassNode tea=read(KC+"blockentity/kitchen/TeapotBlockEntity");MethodNode insert=method(tea,"addIngredient");
        assertTrue(calls(insert,"net/minecraft/world/item/ItemStack","copyWithCount"));assertTrue(calls(insert,"net/minecraft/world/item/ItemStack","shrink"));
        assertEquals("(Lnet/minecraft/world/item/ItemStack;)V",method(tea,"insertIngredient").desc);
        ClassNode handler=read(KC+"inventory/itemhandler/TeapotInputHandler");
        assertTrue(calls(method(handler,"insertItem"),tea.name,"insertIngredient"));
    }
    @Test void actualStockpotInsertionPaysPhysicalUnitAndHasRemovalLifecycle()throws IOException{
        ClassNode pot=read(KC+"blockentity/kitchen/StockpotBlockEntity");
        assertTrue(calls(method(pot,"addIngredient"),"net/minecraft/world/item/ItemStack","split"));
        assertEquals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Z",method(pot,"removeIngredient").desc);
        assertEquals("()I",method(pot,"getTakeoutCount").desc);
    }
    @Test void actualCreativeFillSkipsShrinkRatherThanProvingPaidReceipt()throws IOException{
        ClassNode fluid=read(KC+"util/FluidUtils");boolean creative=false,shrink=false;
        for(MethodNode method:fluid.methods)if(method.name.startsWith("lambda$emptyItem$")){
            creative|=calls(method,"net/minecraft/world/entity/player/Player","isCreative");
            shrink|=calls(method,"net/minecraft/world/item/ItemStack","shrink");
        }
        assertTrue(creative&&shrink,"Pinned native creative/debit branch drift; capture rule needs review");
    }
    @Test void compiledFinishNeverLooksUpAnActorAndUsesCapturedFacts()throws IOException{
        ClassNode provenance=read("com/bettercontent/bettercooking/drinks/NativeDrinkProvenance");boolean captured=false;
        for(MethodNode method:provenance.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){
            assertFalse(call.name.equals("getPlayerList")||call.name.equals("getPlayer")||call.owner.equals("net/minecraft/server/level/ServerPlayer"),"Finish unexpectedly actor-bound");
            captured|=call.owner.equals("com/bettercontent/bettercooking/drinks/NativeDrinkIngredients")&&call.name.equals("resolvedSnapshot");
        }
        assertTrue(captured,"Finish did not consume the actual insertion/default snapshot protocol");
    }
}
