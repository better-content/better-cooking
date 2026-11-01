package com.bettercontent.bettercooking.acquisition;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.IOException;
import java.util.*;

/** Actual pinned MAPPED class resources; never initializes Minecraft, follows inherited phantoms or uses reflection. */
public final class CropNativeCallsiteTest {
    private static final String CROP="com/github/ysbbbbbb/kaleidoscopecookery/block/crop/";
    private static final String STRAW="com/github/ysbbbbbb/kaleidoscopecookery/block/misc/StrawBlocks";
    private static final String BLOCK="net/minecraft/world/level/block/Block";
    private static final String USE="use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";
    private static final String FALL="fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;F)V";
    private static final String POP="popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V";
    private record Code(Set<String> declarations, Map<String,List<String>> calls, Map<String,Integer> produced) {}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Code code(String name)throws IOException{
        Set<String> methods=new HashSet<>();Map<String,List<String>> calls=new HashMap<>();Map<String,Integer> produced=new LinkedHashMap<>();
        try(var stream=CropNativeCallsiteTest.class.getResourceAsStream("/"+name+".class")){
            if(stream==null)throw new AssertionError("Missing pinned actual mapped class "+name);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] errors){
                    String selector=method+desc;methods.add(selector);List<String> invoked=new ArrayList<>();calls.put(selector,invoked);
                    return new MethodVisitor(Opcodes.ASM9){String field;int literal=-1;
                        @Override public void visitFieldInsn(int op,String owner,String fieldName,String descriptor){if(op==Opcodes.GETSTATIC&&owner.endsWith("/init/ModItems"))field=fieldName;}
                        @Override public void visitInsn(int op){if(op>=Opcodes.ICONST_0&&op<=Opcodes.ICONST_5)literal=op-Opcodes.ICONST_0;}
                        @Override public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
                            invoked.add(owner+";"+method+descriptor);
                            if(selector.equals(FALL)&&method.equals("<init>")&&owner.equals("net/minecraft/world/item/ItemStack")&&descriptor.equals("(Lnet/minecraft/world/level/ItemLike;I)V")&&field!=null)produced.put(field,literal);
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return new Code(methods,calls,produced);
    }
    private static long count(Code code,String selector,String owner){
        check(code.declarations().contains(selector),"Phantom inherited/changed method "+selector);
        return code.calls().get(selector).stream().filter(call->call.equals(owner+";"+POP)).count();
    }
    @Test void exactNativeProductionOwnersCountsAndNegativePassMethods()throws IOException{
        check(count(code(CROP+"BaseCropBlock"),USE,BLOCK)==1,"BaseCrop native production count/owner drift");
        Code straw=code(STRAW);check(count(straw,FALL,STRAW)==2,"Straw inherited static call's bytecode owner is STRAW, not Block");
        check(count(straw,FALL,BLOCK)==0,"Wrong declaring owner must not match Straw invocation");
        check(straw.produced().equals(Map.of("RICE_PANICLE",5,"RICE_SEED",4)),"Native Straw production ratios drift "+straw.produced());
        for(String pass:List.of("RiceCropBlock","LettuceCropBlock")){
            Code nativeClass=code(CROP+pass);check(count(nativeClass,USE,BLOCK)==0,"PASS-only native use must have no phantom harvest: "+pass);
            check(nativeClass.calls().get(USE).isEmpty(),"PASS-only method unexpectedly calls production: "+pass);
        }
    }
    @Test void exactMixinSelectorsMatchDeclaredMethodsAndInvocationOwners()throws IOException{
        for(var spec:List.of(List.of("CropHarvestMixin",CROP+"BaseCropBlock",USE,BLOCK,"1"),List.of("StrawHarvestMixin",STRAW,FALL,STRAW,"2"))){
            Set<String> targets=new HashSet<>(),selectors=new HashSet<>(),invokes=new HashSet<>(),kinds=new HashSet<>();int[] index={-1},require={-1};
            String mixin="com/bettercontent/bettercooking/mixin/"+spec.get(0);
            try(var stream=CropNativeCallsiteTest.class.getResourceAsStream("/"+mixin+".class")){
                if(stream==null)throw new AssertionError("Uncompiled production mixin "+mixin);
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible){
                        if(!desc.endsWith("/Mixin;"))return null;
                        return new AnnotationVisitor(Opcodes.ASM9){@Override public AnnotationVisitor visitArray(String key){return new AnnotationVisitor(Opcodes.ASM9){@Override public void visit(String k,Object v){if(v instanceof Type t)targets.add(t.getInternalName());}};}};
                    }
                    @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){return new MethodVisitor(Opcodes.ASM9){
                        @Override public AnnotationVisitor visitAnnotation(String annotation,boolean visible){
                            if(!annotation.endsWith("/ModifyArg;")&&!annotation.endsWith("/Redirect;"))return null;
                            kinds.add(annotation.endsWith("/Redirect;")?"Redirect":"ModifyArg");
                            if(annotation.endsWith("/Redirect;"))check(desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;"+USE.substring(USE.indexOf('(')+1,USE.indexOf(')'))+")V"),"Redirect must capture exact native invocation AND all enclosing use arguments");
                            return new AnnotationVisitor(Opcodes.ASM9){
                                @Override public void visit(String k,Object v){if(k.equals("index"))index[0]=(Integer)v;if(k.equals("require"))require[0]=(Integer)v;}
                                @Override public AnnotationVisitor visitArray(String k){if(!k.equals("method"))return null;return new AnnotationVisitor(Opcodes.ASM9){@Override public void visit(String k,Object v){selectors.add(v.toString());}};}
                                @Override public AnnotationVisitor visitAnnotation(String k,String d){if(!k.equals("at"))return null;return new AnnotationVisitor(Opcodes.ASM9){@Override public void visit(String key,Object v){if(key.equals("target"))invokes.add(v.toString());}};}
                            };
                        }
                    };}
                },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
            check(targets.equals(Set.of(spec.get(1))),"Wrong actual mixin class target "+targets);
            check(selectors.equals(Set.of(spec.get(2))),"Selector lacks exact declared descriptor "+selectors);
            check(invokes.equals(Set.of("L"+spec.get(3)+";"+POP)),"Invocation owner/descriptor drift "+invokes);
            boolean cropRedirect=spec.get(0).equals("CropHarvestMixin");
            check(kinds.equals(Set.of(cropRedirect?"Redirect":"ModifyArg")),"Native crop boundary must wrap actual operation; straw only modifies new output");
            check(index[0]==(cropRedirect?-1:2)&&require[0]==Integer.parseInt(spec.get(4)),"Non-strict/new-output operation injection");
            check(count(code(spec.get(1)),spec.get(2),spec.get(3))==require[0],"Injection cardinality != actual bytecode");
        }
    }
    @Test void actualCropOperationHasGuaranteedFinallyCleanup()throws IOException{
        int[] begin={0},end={0},vanilla={0},finallyRegions={0};
        try(var stream=CropNativeCallsiteTest.class.getResourceAsStream("/com/bettercontent/bettercooking/mixin/CropHarvestMixin.class")){
            if(stream==null)throw new AssertionError("Missing actual crop redirect bytecode");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){if(!n.equals("betterCooking$newCropOutput"))return null;return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitTryCatchBlock(Label start,Label stop,Label handler,String type){if(type==null)finallyRegions[0]++;}
                    @Override public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
                        if(owner.equals("com/bettercontent/betterbumblezonecrops/CultivarHarvestBoundary")){if(method.equals("begin"))begin[0]++;if(method.equals("end"))end[0]++;}
                        if(owner.equals(BLOCK)&&method.equals("popResource"))vanilla[0]++;
                    }
                };}
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        check(begin[0]==1&&end[0]==2&&finallyRegions[0]>=1&&vanilla[0]==2,"One source boundary, normal+exception cleanup, SAME native operation in discovery/live paths required");
    }
    public static void main(String[] args)throws IOException{var t=new CropNativeCallsiteTest();t.exactNativeProductionOwnersCountsAndNegativePassMethods();t.exactMixinSelectorsMatchDeclaredMethodsAndInvocationOwners();t.actualCropOperationHasGuaranteedFinallyCleanup();System.out.println("Crop actual mapped-bytecode controls PASS3; positive3 outputs, Rice/Lettuce/wrong-owner negatives, exact original operation/finally (NOT transformed/native acceptance)");}
}
