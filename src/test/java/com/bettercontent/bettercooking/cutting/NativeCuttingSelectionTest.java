package com.bettercontent.bettercooking.cutting;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real codec and SHA-bound actual mapped/raw/API declarations; NOT native/client/mixin execution. */
public final class NativeCuttingSelectionTest {
    private static ClassNode cls(String name) throws Exception {
        try(InputStream in=NativeCuttingSelectionTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")) {
            assertNotNull(in,name);var node=new ClassNode();new ClassReader(in).accept(node,0);return node;
        }
    }
    private static boolean calls(ClassNode type,String owner,String name) {
        for(var method:type.methods) for(var instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;
        return false;
    }
    @Test void choiceCodecIsExactVersionedAndRejectsInvalidTypes() {
        var choice=new NativeCuttingSelection.Choice(new ResourceLocation("farmersdelight:cutting/beef"),new ResourceLocation("minecraft:beef"));
        assertEquals(choice,NativeCuttingSelection.Choice.load(choice.save()));
        CompoundTag bad=choice.save();bad.putInt("recipe",1);
        assertThrows(IllegalArgumentException.class,()->NativeCuttingSelection.Choice.load(bad));
        CompoundTag version=choice.save();version.putInt("v",2);
        assertThrows(IllegalArgumentException.class,()->NativeCuttingSelection.Choice.load(version));
    }
    @Test void chanceLabelsArePossibleNotRoundedGuaranteedRolls() {
        assertEquals("20%",NativeCuttingSelection.chanceLabel(.2));
        assertEquals("0%",NativeCuttingSelection.chanceLabel(0));
        assertEquals("100%",NativeCuttingSelection.chanceLabel(1));
        assertEquals("0.05%",NativeCuttingSelection.chanceLabel(.0005));
    }
    @Test void actualRawAndMappedLookupAreExactlyOneAndGroupedStrictly() throws Exception {
        String target="com/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/ChoppingBoardBlockEntity";
        String raw=System.getProperty("better_cooking.rawKcJar");assertNotNull(raw,"Required rawKcJar binding must never skip");
        Path path=Path.of(raw);
        assertEquals("0eee5579b36746825d6586c65051d9e68fc84178a19dec4372fc9d64b8629d62",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(path))));
        ClassNode shipped=new ClassNode();try(ZipFile zip=new ZipFile(path.toFile());InputStream in=zip.getInputStream(zip.getEntry(target+".class"))){new ClassReader(in).accept(shipped,0);}
        for(var pair:List.of(new Object[]{cls(target.replace('/','.')),"getRecipeFor"},new Object[]{shipped,"m_44015_"})) {
            var type=(ClassNode)pair[0];int count=0;
            for(var method:type.methods)if(method.name.equals("onPutItem"))for(var instruction:method.instructions)
                if(instruction instanceof MethodInsnNode c&&c.owner.equals("net/minecraft/world/item/crafting/RecipeManager")&&c.name.equals(pair[1])&&c.desc.equals("(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))count++;
            assertEquals(1,count);
        }
        var mix=cls("com.bettercontent.bettercooking.mixin.MixinNativeCuttingOutput");int grouped=0;
        for(var m:mix.methods)if(m.name.equals("cooking$mappedLookup")||m.name.equals("cooking$rawLookup")) {
            var annotations=new java.util.ArrayList<AnnotationNode>();if(m.invisibleAnnotations!=null)annotations.addAll(m.invisibleAnnotations);if(m.visibleAnnotations!=null)annotations.addAll(m.visibleAnnotations);
            var group=annotations.stream().filter(a->a.desc.endsWith("/Group;")).findFirst().orElseThrow();
            assertTrue(group.values.contains("cutting_actual_lookup"));assertEquals(1,group.values.get(group.values.indexOf("min")+1));assertEquals(1,group.values.get(group.values.indexOf("max")+1));grouped++;
        }
        assertEquals(2,grouped);
    }
    @Test void serverSelectionUsesRealMenuCursorAndValidityNotWorkPayment() throws Exception {
        var menu=cls("com.bettercontent.bettercooking.kitchen.KitchenMenu");
        var method=menu.methods.stream().filter(m->m.name.equals("chooseNativeCut")).findFirst().orElseThrow();
        var just=new ClassNode();just.methods.add(method);
        assertTrue(calls(just,"com/bettercontent/bettercooking/kitchen/KitchenInventory","valid"));
        assertTrue(calls(just,"com/bettercontent/bettercooking/kitchen/KitchenInventory","occupied"));
        assertTrue(calls(just,"com/bettercontent/bettercooking/kitchen/KitchenMenu","getCarried"));
        assertFalse(calls(just,"com/bettercontent/bettercooking/kitchen/KitchenInventory","begin"));
        var choose=cls("com.bettercontent.bettercooking.kitchen.KitchenNetwork$ChooseCut");
        // Handler is enqueued; its actual lambda calls the same guarded server method.
        assertTrue(calls(choose,"com/bettercontent/bettercooking/kitchen/KitchenMenu","chooseNativeCut"));
    }
    @Test void emiUsesActualChanceApiAndSendsOnlyGuardedChoice() throws Exception {
        var emi=cls("com.bettercontent.bettercooking.cutting.NativeCuttingEmiRecipe");
        assertTrue(calls(emi,"dev/emi/emi/api/stack/EmiStack","setChance"));
        assertTrue(emi.methods.stream().anyMatch(m->m.name.equals("getCatalysts")),"Reusable knives are catalysts, not consumed recipe inputs");
        assertTrue(calls(emi,"com/bettercontent/bettercooking/kitchen/KitchenNetwork","chooseCut"));
        assertFalse(calls(emi,"com/bettercontent/bettercooking/cutting/CuttingPlan","select"));
        assertFalse(calls(emi,"com/bettercontent/bettercooking/CookingRuntime","complete"));
        assertTrue(calls(cls("com.bettercontent.bettercooking.compat.emi.CookingEmiPlugin"),"dev/emi/emi/api/EmiRegistry","removeRecipes"));
        var ui=cls("com.bettercontent.bettercooking.client.KitchenScreen");
        assertTrue(ui.methods.stream().flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false)).anyMatch(i->i instanceof LdcInsnNode c&&"Next cut (input on cursor)".equals(c.cst)));
    }
    public static void main(String[] args) throws Exception {
        if(args.length==1)System.setProperty("better_cooking.rawKcJar",args[0]);
        var t=new NativeCuttingSelectionTest();t.choiceCodecIsExactVersionedAndRejectsInvalidTypes();t.chanceLabelsArePossibleNotRoundedGuaranteedRolls();t.actualRawAndMappedLookupAreExactlyOneAndGroupedStrictly();t.serverSelectionUsesRealMenuCursorAndValidityNotWorkPayment();t.emiUsesActualChanceApiAndSendsOnlyGuardedChoice();
        System.out.println("NativeCuttingSelectionTest: 5 PASS (actual codec/raw+mapped/EMI+menu declarations only; native/UI NOT RUN)");
    }
}
