package com.bettercontent.bettercooking.acquisition;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.util.*;

/** Reads the real mapped provider class. No reflection or fake provider implementation. */
public final class CropSeedAuthorityBytecodeTest {
    private static List<String> instructions(String owner,String method)throws Exception {
        List<String> instructions=new ArrayList<>();int[] methods={0};
        try(var stream=CropSeedAuthorityBytecodeTest.class.getResourceAsStream("/"+owner+".class")){
            if(stream==null)throw new AssertionError("Missing explicit ninth provider role "+owner);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                    if(!n.equals(method))return null;methods[0]++;
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int op,String o,String n,String d,boolean itf){instructions.add(o+";"+n+d);}
                        @Override public void visitInsn(int op){if(op==Opcodes.ARETURN||op==Opcodes.IRETURN)instructions.add("RETURN");}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        if(methods[0]!=1)throw new AssertionError("Expected exactly one declared native provider method "+method+", got "+methods[0]);return instructions;
    }
    private static int find(List<String> code,String fragment){for(int i=0;i<code.size();i++)if(code.get(i).contains(fragment))return i;throw new AssertionError("Required typed provider operation missing: "+fragment);}
    @Test void nonprimaryAliasSuppressionPrecedesAnyRollAndReturnsBeforeIt()throws Exception{
        var code=instructions("com/bettercontent/betterbumblezonecrops/CultivarLootModifier","doApply");
        int remove=find(code,";removeIf("),guard=find(code,";aliasHarvestPosition("),random=find(code,"net/minecraft/util/RandomSource;nextInt(I)I");
        if(!(remove<guard&&guard<random))throw new AssertionError("Alias propagule suppression/guard must precede native random quota");
        if(!code.subList(guard,random).contains("RETURN"))throw new AssertionError("Nonprimary guard must return before seedCount/RNG/refund, not merely calculate an anchor");
    }
    @Test void newOutputDecisionReusesExistingQuotaHelperWithoutAnAdditionalDraw()throws Exception{
        var code=instructions("com/bettercontent/betterbumblezonecrops/CultivarLootModifier","allowsNewSeedProduction");
        if(code.stream().filter(c->c.contains(";seedCount(ZZZZI)I")).count()!=1)throw new AssertionError("New-output gate must delegate the existing provider authority exactly once");
        if(code.stream().anyMatch(c->c.contains("Random")||c.contains("nextInt")||c.contains("nextFloat")))throw new AssertionError("New-output gate must not add a probability draw");
    }
    public static void main(String[] args)throws Exception{var t=new CropSeedAuthorityBytecodeTest();t.nonprimaryAliasSuppressionPrecedesAnyRollAndReturnsBeforeIt();t.newOutputDecisionReusesExistingQuotaHelperWithoutAnAdditionalDraw();System.out.println("Provider actual mapped-bytecode seed gate controls PASS2 (NOT native world acceptance)");}
}
