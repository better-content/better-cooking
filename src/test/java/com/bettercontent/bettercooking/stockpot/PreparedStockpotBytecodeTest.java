package com.bettercontent.bettercooking.stockpot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Actual raw/mapped JAR + compiled-source inspection. Never boots Minecraft or pretends to execute a mixin. */
public final class PreparedStockpotBytecodeTest {
    private static final String ROOT="com/github/ysbbbbbb/kaleidoscopecookery/";
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    private static ClassNode read(byte[] bytes) { ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);return n; }
    private static ClassNode entry(JarFile jar,String name)throws Exception {try(var in=jar.getInputStream(jar.getJarEntry(name+".class"))){return read(in.readAllBytes());}}
    private static Object value(AnnotationNode annotation,String key) {
        if(annotation.values==null)return null; // Default-valued @Shadow has no explicit element array.
        for(int i=0;i<annotation.values.size();i+=2) if(annotation.values.get(i).equals(key))return annotation.values.get(i+1);
        return null;
    }
    private static void selectors(Path classes,String mixin,ClassNode target)throws Exception {
        ClassNode own=read(Files.readAllBytes(classes.resolve("com/bettercontent/bettercooking/mixin/"+mixin+".class")));
        for(MethodNode method:own.methods) {
            List<AnnotationNode> annotations=new ArrayList<>();
            if(method.visibleAnnotations!=null)annotations.addAll(method.visibleAnnotations);
            if(method.invisibleAnnotations!=null)annotations.addAll(method.invisibleAnnotations);
            for(AnnotationNode annotation:annotations) {
                if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                    @SuppressWarnings("unchecked") List<String> names=(List<String>)value(annotation,"method");
                    int count=0;
                    for(String selector:names) for(MethodNode candidate:target.methods) if(selector.equals(candidate.name+candidate.desc))count++;
                    int require=(Integer)value(annotation,"require");
                    check(count==require,"exact selector count "+mixin+"."+method.name+" target "+target.name+": "+count+" vs "+require);
                    check(Boolean.FALSE.equals(value(annotation,"remap")),"explicit proven aliases/no implicit mapping");
                }
                if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;")) {
                    String name=(String)value(annotation,"value");
                    check(target.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(method.desc)),"exact native invoker "+name+method.desc);
                }
            }
        }
    }
    private static void removalRedirects(Path classes,ClassNode level)throws Exception {
        ClassNode own=read(Files.readAllBytes(classes.resolve("com/bettercontent/bettercooking/mixin/PreparedStockpotRemovalGuardMixin.class")));
        int total=0;
        for(MethodNode handler:own.methods) {
            List<AnnotationNode> annotations=new ArrayList<>();
            if(handler.visibleAnnotations!=null)annotations.addAll(handler.visibleAnnotations);
            if(handler.invisibleAnnotations!=null)annotations.addAll(handler.invisibleAnnotations);
            for(AnnotationNode annotation:annotations) {
                if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Group;")) {
                    check(Integer.valueOf(1).equals(value(annotation,"min"))&&Integer.valueOf(1).equals(value(annotation,"max")),"strict redirect namespace group");
                }
                if(!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"))continue;
                @SuppressWarnings("unchecked") List<String> methods=(List<String>)value(annotation,"method");
                String target=(String)value((AnnotationNode)value(annotation,"at"),"target");
                int matches=0;
                for(MethodNode nativeMethod:level.methods)if(methods.contains(nativeMethod.name+nativeMethod.desc))
                    for(AbstractInsnNode insn:nativeMethod.instructions)if(insn instanceof MethodInsnNode call
                        &&target.equals("L"+call.owner+";"+call.name+call.desc)) {
                            matches++;
                            boolean nullTest=false,falseReturn=false;
                            for(AbstractInsnNode next=call.getNext();next!=null;next=next.getNext()) {
                                if(!nullTest&&next instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFNONNULL) nullTest=true;
                                else if(nullTest&&next.getOpcode()==Opcodes.ICONST_0) {
                                    AbstractInsnNode after=next.getNext();while(after!=null&&after.getOpcode()<0)after=after.getNext();
                                    if(after!=null&&after.getOpcode()==Opcodes.IRETURN) {falseReturn=true;break;}
                                }
                            }
                            check(nullTest&&falseReturn,"actual native null chunk return aborts Level mutation with false");
                        }
                check(matches<=1,"at most one exact chunk mutation call per namespace redirect");total+=matches;
            }
        }
        check(total==1,"exactly ONE raw-or-mapped pre-mutation redirect in actual Level");
    }
    private static void nativeRemovalPaths(JarFile jar)throws Exception {
        ClassNode level=entry(jar,"net/minecraft/world/level/Level");
        boolean delegate=false,dropBeforeMutation=false;
        for(MethodNode method:level.methods) {
            boolean destroy=method.desc.equals("(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z");
            int index=0,drop=-1,mutation=-1;
            for(AbstractInsnNode insn:method.instructions) {
                if(insn instanceof MethodInsnNode call) {
                    if(method.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")
                        &&call.owner.equals(level.name)&&call.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"))delegate=true;
                    if(destroy&&call.owner.equals("net/minecraft/world/level/block/Block")&&call.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"))drop=index;
                    if(destroy&&call.owner.equals(level.name)&&call.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"))mutation=index;
                }
                index++;
            }
            if(destroy)dropBeforeMutation=drop>=0&&mutation>drop;
        }
        check(delegate,"actual Level three-argument replacement delegates to guarded four-argument mutation");
        check(dropBeforeMutation,"actual actorless destroy drops BEFORE mutation; blocked loot must be EMPTY");
        ClassNode forge=entry(jar,"net/minecraftforge/common/extensions/IForgeBlock");boolean explosionReplacement=false;
        for(MethodNode method:forge.methods)if(method.name.equals("onBlockExploded"))for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof MethodInsnNode call&&call.owner.equals(level.name)&&call.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))explosionReplacement=true;
        check(explosionReplacement,"actual default Forge explosion callback uses guarded Level replacement");
        ClassNode explosion=entry(jar,"net/minecraft/world/level/Explosion");boolean explosionOrder=false;
        for(MethodNode method:explosion.methods) {
            int index=0,drop=-1,callback=-1;
            for(AbstractInsnNode insn:method.instructions) {
                if(insn instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/level/block/state/BlockState")) {
                    if(call.desc.equals("(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;"))drop=index;
                    if(call.name.equals("onBlockExploded"))callback=index;
                }
                index++;
            }
            explosionOrder|=drop>=0&&callback>drop;
        }
        check(explosionOrder,"actual explosion queries loot BEFORE callback/removal; no pot/lid duplicate on refusal");
    }
    private static boolean legalAlias(int actualTargetAccess,List<String> aliases) {
        return aliases.isEmpty()||(actualTargetAccess&Opcodes.ACC_PRIVATE)!=0;
    }
    public static void main(String[] args)throws Exception {
        Path classes=Path.of(args[0]);
        for(int i=1;i<=2;i++) try(JarFile jar=new JarFile(args[i])) {
            ClassNode record=entry(jar,ROOT+"crafting/recipe/StockpotRecipe");
            check((record.access&Opcodes.ACC_FINAL)!=0 && record.superName.equals("java/lang/Record"),"pinned native final record");
            selectors(classes,"PreparedStockpotRecipeMixin",record);
            ClassNode entity=entry(jar,ROOT+"blockentity/kitchen/StockpotBlockEntity");
            selectors(classes,"PreparedStockpotBlockEntityMixin",entity);
            selectors(classes,"PreparedStockpotRecipeItemMixin",entry(jar,ROOT+"item/RecipeItem"));
            selectors(classes,"PreparedStockpotDropsMixin",entry(jar,ROOT+"block/kitchen/StockpotBlock"));
            check(entity.fields.stream().anyMatch(f->f.name.equals("MAX_TAKEOUT_COUNT")&&Integer.valueOf(9).equals(f.value)),"native capacity still nine");
            ClassNode block=entry(jar,ROOT+"block/kitchen/StockpotBlock");
            List<String> dispatch=new ArrayList<>();
            for(MethodNode method:block.methods) for(AbstractInsnNode insn:method.instructions)
                if(insn instanceof MethodInsnNode call && call.owner.equals(ROOT+"api/blockentity/IStockpot"))dispatch.add(call.name);
            check(dispatch.contains("addSoupBase")&&dispatch.contains("addIngredient"),"actual player block dispatch reaches native ingredient method");
            check(dispatch.indexOf("addSoupBase")<dispatch.indexOf("addIngredient"),"base attempt precedes ingredient attempt; shim must refuse batch as base");
            check(block.methods.stream().noneMatch(m->m.name.equals("onBlockExploded")),"native stockpot has no alternate explosion replacement override");
            System.out.println("PASS actual target selectors/dispatch/capacity: "+args[i]);
        }
        for(int i=3;i<args.length;i++)try(JarFile jar=new JarFile(args[i])) {
            removalRedirects(classes,entry(jar,"net/minecraft/world/level/Level"));
            nativeRemovalPaths(jar);
            ClassNode gameMode=entry(jar,"net/minecraft/server/level/ServerPlayerGameMode");
            selectors(classes,"PreparedStockpotPlayerBreakGuardMixin",gameMode);
            ClassNode ownGuard=read(Files.readAllBytes(classes.resolve("com/bettercontent/bettercooking/mixin/PreparedStockpotPlayerBreakGuardMixin.class")));
            List<AnnotationNode> classAnnotations=new ArrayList<>();
            if(ownGuard.visibleAnnotations!=null)classAnnotations.addAll(ownGuard.visibleAnnotations);
            if(ownGuard.invisibleAnnotations!=null)classAnnotations.addAll(ownGuard.invisibleAnnotations);
            for(AnnotationNode annotation:classAnnotations)if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                check(!Boolean.FALSE.equals(value(annotation,"remap")),"vanilla GameMode class requires AP remapping");
            for(FieldNode field:ownGuard.fields) {
                List<AnnotationNode> annotations=new ArrayList<>();
                if(field.visibleAnnotations!=null)annotations.addAll(field.visibleAnnotations);
                if(field.invisibleAnnotations!=null)annotations.addAll(field.invisibleAnnotations);
                for(AnnotationNode annotation:annotations)if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
                    @SuppressWarnings("unchecked") List<String> aliases=(List<String>)value(annotation,"aliases");
                    check(aliases==null||aliases.isEmpty(),"NO aliases on actual nonprivate GameMode targets");
                    check(!Boolean.FALSE.equals(value(annotation,"remap")),"ordinary vanilla shadow requires AP/refmap");
                    String srg=Map.of("level","f_9244_","player","f_9245_").get(field.name);
                    check(srg!=null,"only exact reviewed vanilla shadows");
                    var candidates=gameMode.fields.stream().filter(f->(f.name.equals(field.name)||f.name.equals(srg))&&f.desc.equals(field.desc)).toList();
                    check(candidates.size()==1,"exact typed mapped/raw target, NOT runtime alias resolution");
                    FieldNode target=candidates.get(0);
                    check((target.access&Opcodes.ACC_PROTECTED)!=0&&(target.access&Opcodes.ACC_PRIVATE)==0,"actual target protected/nonprivate in both namespaces");
                    check(field.name.equals("player")==((target.access&Opcodes.ACC_FINAL)!=0),"actual player final / level nonfinal");
                    check(!legalAlias(target.access,List.of(srg)),"adverse: original nonprivate-target alias MUST fail");
                    FieldNode privateLocal=new FieldNode(Opcodes.ACC_PRIVATE,field.name,field.desc,null,null);
                    check((privateLocal.access&Opcodes.ACC_PRIVATE)!=0&&!legalAlias(target.access,List.of(srg)),"adverse: private local declaration cannot legalize protected TARGET alias");
                }
            }
            for(MethodNode method:gameMode.methods)if(Set.of("destroyBlock","m_9280_").contains(method.name)&&method.desc.equals("(Lnet/minecraft/core/BlockPos;)Z")) {
                int index=0,mine=-1,remove=-1;
                for(AbstractInsnNode insn:method.instructions) {
                    if(insn instanceof MethodInsnNode call) {
                        if(call.owner.equals("net/minecraft/world/item/ItemStack")&&call.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))mine=index;
                        if(mine>=0&&call.name.equals("removeBlock"))remove=index;
                    }
                    index++;
                }
                check(mine>=0&&remove>mine,"actual GameMode tool debit BEFORE survival removal requires HEAD guard");
            }
            System.out.println("PASS actual pre-mutation/player-prepayment guards: "+args[i]);
        }
        ClassNode backend=read(Files.readAllBytes(classes.resolve("com/bettercontent/bettercooking/mixin/PreparedStockpotBlockEntityMixin.class")));
        List<MethodInsnNode> calls=new ArrayList<>();boolean pureFields=true,pureCalls=true;
        for(MethodNode method:backend.methods)for(AbstractInsnNode insn:method.instructions) {
            if(insn instanceof MethodInsnNode call)calls.add(call);
            if(method.name.equals("cooking$preparedStockpotBreakDrops")) {
                pureFields&=insn.getOpcode()!=Opcodes.PUTFIELD&&insn.getOpcode()!=Opcodes.PUTSTATIC;
                if(insn instanceof MethodInsnNode call)pureCalls&=!Set.of("split","shrink","clear","setCount","nextInt","nextFloat","finishPreparation").contains(call.name);
            }
        }
        check(pureFields,"pure break plan has no owner/static field writes");
        check(pureCalls,"break snapshot does not debit/mint work/roll RNG");
        check(calls.stream().noneMatch(c->c.owner.startsWith("java/lang/reflect")),"no reflection");
        check(calls.stream().noneMatch(c->c.name.equals("finishPreparation")),"backend cannot manufacture paid work");
        check(calls.stream().anyMatch(c->c.name.equals("split")),"actual physical split exists");
        System.out.println("PASS "+checks+" bytecode controls; native_executed=false, mixin_transform_executed=false");
    }
}
