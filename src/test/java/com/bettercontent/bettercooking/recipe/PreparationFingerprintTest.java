package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.policy.CulinaryRoute;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual executable definitions; no station, registry thaw, fake player, or mutable test reset. */
class PreparationFingerprintTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private PreparationRecipe recipe(String surface,String finish,String work,int servings,int ticks,int count,
                                     ItemStack result,CulinaryRoute.Stage stage) {
        return new PreparationRecipe(new ResourceLocation("better_cooking","fingerprint_control"),
            List.of(new PreparationRecipe.Input(Ingredient.of(Items.CARROT),count)),result,
            surface,finish,servings,work,List.of(stage),ticks);
    }
    private CulinaryRoute.Stage stage(String action,int ticks,int repetitions,String tool) {
        return new CulinaryRoute.Stage(action,ticks,repetitions,tool);
    }
    private PreparationRecipe baseline() {
        return recipe("board","stockpot","substantial",1,200,1,new ItemStack(Items.MUSHROOM_STEW),
            stage("mix",30,2,"forge:tools/knives"));
    }
    @Test void identicalDefinitionsHaveOneBoundedPersistentIdentity() {
        assertEquals(baseline().fingerprint(),baseline().fingerprint());
        assertTrue(baseline().fingerprint().matches("sha256:[0-9a-f]{64}"));
    }
    @Test void everyExecutablePaymentAndWorkChangeInvalidatesPaidDefinition() {
        String original=baseline().fingerprint();
        var normal=stage("mix",30,2,"forge:tools/knives");
        assertNotEquals(original,recipe("baking_mat","stockpot","substantial",1,200,1,new ItemStack(Items.MUSHROOM_STEW),normal).fingerprint());
        assertNotEquals(original,recipe("board","steamer","substantial",1,200,1,new ItemStack(Items.MUSHROOM_STEW),normal).fingerprint());
        assertNotEquals(original,recipe("board","stockpot","elaborate",1,200,1,new ItemStack(Items.MUSHROOM_STEW),normal).fingerprint());
        assertNotEquals(original,recipe("board","stockpot","substantial",2,200,1,new ItemStack(Items.MUSHROOM_STEW),normal).fingerprint());
        assertNotEquals(original,recipe("board","stockpot","substantial",1,201,1,new ItemStack(Items.MUSHROOM_STEW),normal).fingerprint());
        assertNotEquals(original,recipe("board","stockpot","substantial",1,200,2,new ItemStack(Items.MUSHROOM_STEW),normal).fingerprint());
        assertNotEquals(original,recipe("board","stockpot","substantial",1,200,1,new ItemStack(Items.MUSHROOM_STEW,2),normal).fingerprint());
        for (var changed:List.of(stage("assemble",30,2,"forge:tools/knives"),stage("mix",31,2,"forge:tools/knives"),
                stage("mix",30,3,"forge:tools/knives"),stage("mix",30,2,"better_cooking:mixing_tools")))
            assertNotEquals(original,recipe("board","stockpot","substantial",1,200,1,new ItemStack(Items.MUSHROOM_STEW),changed).fingerprint());
    }
    @Test void fullResultVariantIsBoundNotOnlyTheItemAndCount() {
        ItemStack variant=new ItemStack(Items.MUSHROOM_STEW);variant.getOrCreateTag().putString("source_variant","retained");
        assertNotEquals(baseline().fingerprint(),recipe("board","stockpot","substantial",1,200,1,variant,
            stage("mix",30,2,"forge:tools/knives")).fingerprint());
    }
}
