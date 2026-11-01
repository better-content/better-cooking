package com.bettercontent.bettercooking.drinks.placed;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pinned mapped declaration/call controls; never a transformed selector claim. */
public class PlacedDrinkNativeBytecodeTest {
    private static final String KC="com/github/ysbbbbbb/kaleidoscopecookery/",DRINK=KC+"block/drink/",MODE="net/minecraft/server/level/ServerPlayerGameMode";
    record Call(String owner,String name,String descriptor){}
    record Method(String name,String descriptor,List<Call> calls,List<String> catches){}
    static List<Method> methods(String type){
        try(InputStream in=PlacedDrinkNativeBytecodeTest.class.getClassLoader().getResourceAsStream(type+".class")){
            assertNotNull(in,"Actual class missing: "+type);List<Method> result=new ArrayList<>();
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9){
                public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                    List<Call> calls=new ArrayList<>();List<String> catches=new ArrayList<>();result.add(new Method(n,d,calls,catches));
                    return new MethodVisitor(Opcodes.ASM9){
                        public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){calls.add(new Call(owner,name,desc));}
                        public void visitFieldInsn(int op,String owner,String name,String desc){calls.add(new Call(owner,name,desc));}
                        public void visitTryCatchBlock(Label start,Label end,Label handler,String type){catches.add(type==null?"finally":type);}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return result;
        }catch(IOException failure){throw new AssertionError(failure);}
    }
    private static Method method(String type,String name){return methods(type).stream().filter(m->m.name.equals(name)).findFirst().orElseThrow(()->new AssertionError(type+" does not DECLARE "+name));}
    private static long count(Method m,String owner,String name){return m.calls.stream().filter(c->c.owner.equals(owner)&&c.name.equals(name)).count();}
    private static int index(Method m,String owner,String name){for(int i=0;i<m.calls.size();i++)if(m.calls.get(i).owner.equals(owner)&&m.calls.get(i).name.equals(name))return i;throw new AssertionError(owner+"."+name);}
    @Test void actualWholeUseAndInitialPlacementHaveOneTypedBoundary(){
        var use=method("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase","use");
        assertEquals(1,count(use,"net/minecraft/world/level/block/Block","use"));
        var place=method("net/minecraft/world/item/BlockItem","place");
        assertEquals(1,count(place,"net/minecraft/world/level/block/Block","setPlacedBy"));
        assertTrue(index(place,"net/minecraft/world/level/block/Block","setPlacedBy")<index(place,"net/minecraft/world/item/ItemStack","shrink"));
    }
    @Test void nativeGameModeCapturesBeBeforeRemovalAndPassesLootAfterRemoval(){
        assertEquals(1,count(method(MODE,"tick"),MODE,"destroyBlock"));
        assertEquals(1,count(method(MODE,"destroyAndAck"),MODE,"destroyBlock"));
        assertEquals(2,methods(MODE).stream().mapToLong(m->count(m,MODE,"destroyBlock")).sum());
        var destroy=method(MODE,"destroyBlock");
        assertTrue(index(destroy,"net/minecraft/server/level/ServerLevel","getBlockEntity")<index(destroy,MODE,"removeBlock"));
        assertTrue(index(destroy,MODE,"removeBlock")<index(destroy,"net/minecraft/world/level/block/Block","playerDestroy"));
    }
    @Test void allFilledPickupOwnersAndCardinalitiesAreExact(){
        var tea=method(DRINK+"TeacupBlock","use");
        assertEquals(2,count(tea,KC+"util/ItemUtils","getItemToLivingEntity"));
        assertEquals(1,count(tea,"net/minecraft/world/level/block/Block","popResourceFromFace"));
        var milk=method(DRINK+"ClayPotMilkTeaBlock","use");
        assertEquals(1,count(milk,KC+"util/ItemUtils","getItemToLivingEntity"));
        assertEquals(1,count(milk,"net/minecraft/world/level/Level","removeBlock"));
    }
    @Test void emptyCupDebitsTeapotBeforeStateTransformationSoSnapshotMustPrecedeCall(){
        var empty=method(DRINK+"EmptyCupBlock","use");String teapot=KC+"item/TeapotItem";
        assertEquals(1,count(empty,teapot,"getPourOut"));assertEquals(1,count(empty,teapot,"pourOut"));
        assertTrue(index(empty,teapot,"getPourOut")<index(empty,teapot,"pourOut"));
        assertTrue(index(empty,teapot,"pourOut")<index(empty,"net/minecraft/world/level/Level","setBlockAndUpdate"));
    }
    @Test void noPhantomMilkLootOrRemovalTargets(){
        for(String type:List.of("TeacupBlock","ClayPotMilkTeaBlock"))assertTrue(methods(DRINK+type).stream().noneMatch(m->m.name.equals("onRemove")));
        assertTrue(methods(DRINK+"ClayPotMilkTeaBlock").stream().noneMatch(m->m.name.equals("getDrops")));
        method("net/minecraft/world/level/block/state/BlockBehaviour","onRemove");method("net/minecraft/world/level/block/state/BlockBehaviour","getDrops");method(DRINK+"TeacupBlock","getDrops");
    }
    @Test void nativeLootBuilderRetainsOptionalCapturedBlockEntity(){
        var drops=methods("net/minecraft/world/level/block/Block").stream().filter(m->m.name.equals("getDrops")&&m.descriptor.contains("Lnet/minecraft/world/entity/Entity;")&&m.descriptor.contains("Lnet/minecraft/world/item/ItemStack;")).findFirst().orElseThrow();
        assertEquals(1,count(drops,"net/minecraft/world/level/storage/loot/parameters/LootContextParams","BLOCK_ENTITY"));
        assertTrue(count(drops,"net/minecraft/world/level/storage/loot/LootParams$Builder","withOptionalParameter")>=1);
    }
    @Test void vanillaLazyFactorySupportsOldBlocksWithoutSavedBeTag(){
        var factory=method("net/minecraft/world/level/chunk/LevelChunk","createBlockEntity");
        assertEquals(1,count(factory,"net/minecraft/world/level/block/EntityBlock","newBlockEntity"));
        assertTrue(methods("net/minecraft/world/level/chunk/LevelChunk").stream().filter(m->m.name.equals("getBlockEntity")).anyMatch(m->count(m,"net/minecraft/world/level/chunk/LevelChunk","createBlockEntity")>0));
    }
    @Test void vanillaCatchesBeLoadFailuresSoOurPoisonCannotBeAThrownLoaderException(){
        assertTrue(methods("net/minecraft/world/level/block/entity/BlockEntity").stream().anyMatch(m->count(m,"net/minecraft/world/level/block/entity/BlockEntity","load")>0&&m.catches.contains("java/lang/Throwable")));
    }
    @Test void vanillaChunkSaveCanOmitThrowingBeSoPoisonMustBePersisted(){
        var save=method("net/minecraft/world/level/chunk/LevelChunk","getBlockEntityNbtForSaving");
        assertEquals(1,count(save,"net/minecraft/world/level/block/entity/BlockEntity","saveWithFullMetadata"));assertTrue(!save.catches.isEmpty());
        var promote=method("net/minecraft/world/level/chunk/LevelChunk","promotePendingBlockEntity");assertEquals(1,count(promote,"net/minecraft/world/level/block/entity/BlockEntity","loadStatic"));
    }
    public static void main(String[] args){var c=new PlacedDrinkNativeBytecodeTest();c.actualWholeUseAndInitialPlacementHaveOneTypedBoundary();c.nativeGameModeCapturesBeBeforeRemovalAndPassesLootAfterRemoval();c.allFilledPickupOwnersAndCardinalitiesAreExact();c.emptyCupDebitsTeapotBeforeStateTransformationSoSnapshotMustPrecedeCall();c.noPhantomMilkLootOrRemovalTargets();c.nativeLootBuilderRetainsOptionalCapturedBlockEntity();c.vanillaLazyFactorySupportsOldBlocksWithoutSavedBeTag();c.vanillaCatchesBeLoadFailuresSoOurPoisonCannotBeAThrownLoaderException();c.vanillaChunkSaveCanOmitThrowingBeSoPoisonMustBePersisted();System.out.println("PASS 9 actual mapped native-call controls; NOT AP/transformed acceptance");}
}
