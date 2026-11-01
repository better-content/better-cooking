package com.bettercontent.bettercooking.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparedFinishTypeTest {
    private final ResourceLocation id=new ResourceLocation("better_cooking","finish_type_control");
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private Ingredient paid(){return new PreparedBatchIngredient(new ResourceLocation("better_cooking","actual_preparation"));}
    @Test void roastingIsActualNativeCampfireSubclassNotSmeltingWithAChangedType(){
        var recipe=new PreparedRoastingRecipe(id,paid(),new ItemStack(Items.COOKED_BEEF),0,200);
        assertInstanceOf(CampfireCookingRecipe.class,recipe);
        assertSame(RecipeType.CAMPFIRE_COOKING,recipe.getType());assertEquals(200,recipe.getCookingTime());
    }
    @Test void bakingRemainsFurnaceCompatible(){
        var recipe=new PreparedBakingRecipe(id,paid(),new ItemStack(Items.BREAD),0,200);
        assertSame(RecipeType.SMELTING,recipe.getType());assertEquals(200,recipe.getCookingTime());
    }
    @Test void neitherSerializerCanAuthorizeAnOrdinaryRawIngredientAsPaidPreparation(){
        Ingredient raw=Ingredient.of(Items.BEEF);
        assertThrows(IllegalArgumentException.class,()->new PreparedRoastingRecipe(id,raw,new ItemStack(Items.COOKED_BEEF),0,200));
        assertThrows(IllegalArgumentException.class,()->new PreparedBakingRecipe(id,raw,new ItemStack(Items.COOKED_BEEF),0,200));
    }
    @Test void malformedYieldTimeAndExperienceAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new PreparedRoastingRecipe(id,paid(),new ItemStack(Items.BREAD,65),0,200));
        assertThrows(IllegalArgumentException.class,()->new PreparedRoastingRecipe(id,paid(),new ItemStack(Items.BREAD),Float.NaN,200));
        assertThrows(IllegalArgumentException.class,()->new PreparedBakingRecipe(id,paid(),new ItemStack(Items.BREAD),-1,200));
        assertThrows(IllegalArgumentException.class,()->new PreparedBakingRecipe(id,paid(),new ItemStack(Items.BREAD),0,0));
    }
}
