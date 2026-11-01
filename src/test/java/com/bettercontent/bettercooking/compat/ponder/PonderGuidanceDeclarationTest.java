package com.bettercontent.bettercooking.compat.ponder;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Static real artifact/API/bytecode/resource controls only. Does not initialize Ponder or simulate work. */
public final class PonderGuidanceDeclarationTest {
    private static final String OWN="com/bettercontent/bettercooking/compat/ponder/";
    private static ClassNode node(InputStream in) throws Exception {
        assertNotNull(in);try(in){var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}
    }
    private static ClassNode own(String name) throws Exception {
        return node(PonderGuidanceDeclarationTest.class.getClassLoader().getResourceAsStream(name+".class"));
    }
    private static Path pin(String property,String expected) throws Exception {
        String path=System.getProperty(property);assertNotNull(path,"Required explicit raw artifact binding: "+property);
        Path p=Path.of(path);assertEquals(expected,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));return p;
    }
    @Test void actualPinnedRaw91ExportsExactCanonicalPublicRegistration() throws Exception {
        Path raw=pin("better_cooking.rawPonderJar","86e6b64372aba6d9c56f2c35725ea26d8febf2c75eed9950566e7f2849443b34");
        try(ZipFile z=new ZipFile(raw.toFile())) {
            String base="net/createmod/ponder/";
            var plugin=node(z.getInputStream(z.getEntry(base+"api/registration/PonderPlugin.class")));
            assertTrue(plugin.methods.stream().anyMatch(m->m.name.equals("registerScenes")&&(m.access&Opcodes.ACC_PUBLIC)!=0));
            var helper=node(z.getInputStream(z.getEntry(base+"api/registration/PonderSceneRegistrationHelper.class")));
            assertTrue(helper.methods.stream().anyMatch(m->m.name.equals("addStoryBoard")&&m.desc.equals("(Ljava/lang/Object;Lnet/minecraft/resources/ResourceLocation;Lnet/createmod/ponder/api/scene/PonderStoryBoard;[Lnet/minecraft/resources/ResourceLocation;)Lnet/createmod/ponder/api/registration/StoryBoardEntry;")));
            var index=node(z.getInputStream(z.getEntry(base+"foundation/PonderIndex.class")));
            assertTrue(index.methods.stream().anyMatch(m->m.name.equals("addPlugin")&&m.desc.equals("(Lnet/createmod/ponder/api/registration/PonderPlugin;)V")&&(m.access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))==(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC)));
        }
    }
    @Test void optionalGuardPrecedesGuidanceInvokedynamicInsideClientOnlySubscriber() throws Exception {
        var client=own("com/bettercontent/bettercooking/client/KitchenClient");
        var subscriber=client.visibleAnnotations.stream().filter(a->a.desc.endsWith("Mod$EventBusSubscriber;")).findFirst().orElseThrow();
        var dist=(List<?>)subscriber.values.get(subscriber.values.indexOf("value")+1);
        assertEquals("CLIENT",((String[])dist.get(0))[1]);
        var setup=client.methods.stream().filter(m->m.name.equals("setup")).findFirst().orElseThrow();
        int loaded=-1,reference=-1,enqueue=-1;JumpInsnNode guard=null;
        for(int i=0;i<setup.instructions.size();i++) {
            var n=setup.instructions.get(i);
            if(n instanceof MethodInsnNode m&&m.owner.equals("net/minecraftforge/fml/ModList")&&m.name.equals("isLoaded"))loaded=i;
            if(n instanceof JumpInsnNode j&&loaded>=0&&reference<0&&j.getOpcode()==Opcodes.IFEQ)guard=j;
            if(n instanceof InvokeDynamicInsnNode d)for(var arg:d.bsmArgs)if(arg instanceof Handle h&&h.getOwner().equals(OWN+"CookingGuidanceClient")&&h.getName().equals("register"))reference=i;
            if(reference>=0&&n instanceof MethodInsnNode m&&m.name.equals("enqueueWork"))enqueue=i;
        }
        assertTrue(loaded>=0&&reference>loaded&&enqueue>reference);assertNotNull(guard);
        assertTrue(setup.instructions.indexOf(guard)<reference&&setup.instructions.indexOf(guard.label)>enqueue,"Missing ponder branch must jump past method-reference creation AND enqueue");
        var helper=own(OWN+"CookingGuidanceClient");
        assertTrue(helper.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("registered")&&f.getOpcode()==Opcodes.GETSTATIC));
    }
    @Test void fourCallbacksReferenceUpstreamAssetLocationsNotStringNamespaceOrPluginReload() throws Exception {
        var plugin=own(OWN+"CookingGuidancePlugin");Set<String> callbacks=new HashSet<>();Set<String> locations=new HashSet<>();
        for(var m:plugin.methods)for(var n:m.instructions) {
            if(n instanceof LdcInsnNode c&&c.cst instanceof String s)locations.add(s);
            if(n instanceof InvokeDynamicInsnNode d)for(var arg:d.bsmArgs)if(arg instanceof Handle h&&h.getOwner().equals(OWN+"CookingGuidanceScenes"))callbacks.add(h.getName());
            if(n instanceof MethodInsnNode c&&c.name.equals("addStoryBoard"))assertTrue(c.desc.startsWith("(Ljava/lang/Object;Lnet/minecraft/resources/ResourceLocation;"));
            if(n instanceof MethodInsnNode c)assertFalse(Set.of("reload","registerAll","clearRegistry","removeRecipes").contains(c.name));
        }
        assertEquals(Set.of("preparation","pot","stockpot","pastry"),callbacks);
        assertTrue(locations.containsAll(Set.of("kaleidoscope_cookery:pot/pot_introduction","kaleidoscope_cookery:stockpot/stockpot_introduction","ubesdelight:baking_mat_bamboo","ubesdelight:baking_mat_kelp","minecraft:furnace","minecraft:campfire")));
    }
    @Test void sceneInstructionsCannotCallPaidWorkCompletionOrMutateNativeBeOrSurvivalInventory() throws Exception {
        var scenes=own(OWN+"CookingGuidanceScenes");int visualBlocks=0;
        Set<String> forbidden=Set.of("finishPreparation","finishFromPrepared","modifyBlockEntity","modifyBlockEntityNBT","createEntity","createItemEntity","setStatus","takeOutProduct","addIngredient","addAllIngredients","shrink","split","grow","getServer","forceComplete");
        for(var m:scenes.methods)for(var n:m.instructions)if(n instanceof MethodInsnNode c) {
            assertFalse(forbidden.contains(c.name),c.owner+"."+c.name);
            assertFalse(c.owner.contains("CookingRuntime")||c.owner.contains("KitchenInventory")||c.owner.contains("ServerPlayer")||c.owner.equals("net/minecraft/client/Minecraft"));
            if(c.owner.equals("net/createmod/ponder/api/scene/WorldInstructions")) {
                assertTrue(Set.of("setBlock","showSection").contains(c.name));if(c.name.equals("setBlock"))visualBlocks++;
            }
        }
        assertEquals(8,visualBlocks,"Only explicit isolated scene geometry replacements, never BE work-state edits");
    }
    @Test void thirtyPanelDefaultsMatch34LanguageKeysAndExplainActiveVersusPassiveWork() throws Exception {
        var in=PonderGuidanceDeclarationTest.class.getClassLoader().getResourceAsStream("assets/better_cooking/lang/en_us.json");assertNotNull(in);
        com.google.gson.JsonObject language;try(in){language=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
        var previous=Map.of("item.better_cooking.prepared_batch","Prepared ingredients","item.better_cooking.prepared_named","Prepared %s","recipe.category.better_cooking.preparation","Kitchen preparation","container.better_cooking.kitchen","Kitchen preparation","better_cooking.action.cut","Cut","better_cooking.action.mix","Mix","better_cooking.action.knead","Knead","better_cooking.action.roll","Roll","better_cooking.action.assemble","Assemble","better_cooking.action.stir","Stir");
        for(var entry:previous.entrySet())assertEquals(entry.getValue(),language.get(entry.getKey()).getAsString());
        assertEquals(34,language.keySet().stream().filter(k->k.startsWith("better_cooking.ponder.")).count());
        Map<String,String> ids=Map.of("preparation","hands_on_preparation","pot","pot_stir_serve","stockpot","stockpot_paid_serving","pastry","pastry_typed_finish");
        var scenes=own(OWN+"CookingGuidanceScenes");int total=0;
        for(var m:scenes.methods)if(ids.containsKey(m.name)) {
            int panel=0;String prefix="better_cooking.ponder."+ids.get(m.name);
            for(var n:m.instructions)if(n instanceof MethodInsnNode c&&c.owner.equals(OWN+"CookingGuidanceScenes")) {
                if(c.name.equals("text")) {
                    assertTrue(n.getPrevious() instanceof LdcInsnNode);String text=(String)((LdcInsnNode)n.getPrevious()).cst;
                    assertEquals(text,language.get(prefix+".text_"+(++panel)).getAsString());total++;
                }
                if(c.name.equals("begin")) {
                    assertTrue(n.getPrevious() instanceof LdcInsnNode);
                    assertEquals(((LdcInsnNode)n.getPrevious()).cst,language.get(prefix+".header").getAsString());
                }
            }
            assertTrue(language.has(prefix+".header"));
        }
        assertEquals(30,total);
        assertTrue(language.get("better_cooking.ponder.hands_on_preparation.text_3").getAsString().contains("unfinished hands-on"));
        assertTrue(language.get("better_cooking.ponder.hands_on_preparation.text_9").getAsString().contains("passive heat and supported automation can continue"));
        assertTrue(language.get("better_cooking.ponder.pastry_typed_finish.text_7").getAsString().contains("does not require the original cook to remain online"));
        assertEquals("Serve remaining food with required containers before breaking.",language.get("better_cooking.ponder.stockpot_paid_serving.text_5").getAsString());
    }
    @Test void exactExistingKcSchematicBytesAreReferencedNotCopiedOrModified() throws Exception {
        Path raw=pin("better_cooking.rawKcJar","0eee5579b36746825d6586c65051d9e68fc84178a19dec4372fc9d64b8629d62");
        var assets=Map.of("pot/pot_introduction","f42e04eb661e932b93c3a7e145091e8e8a0ed74908faaf778eab1b161117b672","stockpot/stockpot_introduction","8651f78dd4b0f81b796d0d8ced9e84056d259f66908abaaa82a49e87920d9d00");
        try(ZipFile z=new ZipFile(raw.toFile())) {
            for(var e:assets.entrySet()) {
                try(var in=z.getInputStream(z.getEntry("assets/kaleidoscope_cookery/ponder/"+e.getKey()+".nbt"))) {
                    assertEquals(e.getValue(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length==2){System.setProperty("better_cooking.rawPonderJar",args[0]);System.setProperty("better_cooking.rawKcJar",args[1]);}
        var t=new PonderGuidanceDeclarationTest();t.actualPinnedRaw91ExportsExactCanonicalPublicRegistration();t.optionalGuardPrecedesGuidanceInvokedynamicInsideClientOnlySubscriber();t.fourCallbacksReferenceUpstreamAssetLocationsNotStringNamespaceOrPluginReload();t.sceneInstructionsCannotCallPaidWorkCompletionOrMutateNativeBeOrSurvivalInventory();t.thirtyPanelDefaultsMatch34LanguageKeysAndExplainActiveVersusPassiveWork();t.exactExistingKcSchematicBytesAreReferencedNotCopiedOrModified();
        System.out.println("PonderGuidanceDeclarationTest: 6 PASS (actual raw APIs/assets, compiled guard/simulation and language; Ponder/render/native NOT RUN)");
    }
}
