package com.bettercontent.bettercooking.fixture;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/** Nonshipping connected-command live public getter facts only. Never assembles/serves or qualifies a recipe. */
public final class NativeCarrierCollector {
    private NativeCarrierCollector() {}
    public static void append(MinecraftServer server,JsonObject root){
        if(!Boolean.getBoolean("better_cooking.discoveryOnly"))throw new IllegalStateException("Discovery-only flag required");
        if(!server.isSameThread())throw new IllegalStateException("Carrier collection requires server thread");
        if(server.getPlayerList().getPlayers().stream().noneMatch(player->!(player instanceof FakePlayer)&&player.connection!=null))throw new IllegalStateException("Carrier snapshot requires actual connected player command context");
        if(root.has("native_fd_carriers"))throw new IllegalArgumentException("Native FD carrier rows already appended");
        JsonObject section=new JsonObject();section.addProperty("schema_version",1);section.addProperty("authority","FD pinned 7d13dbeb8ed1e7cd22daf35ddf3df4c411392a65a603b11145f84d6bf2443e9d CookingPotRecipe public getters");
        section.addProperty("observation_scope","live RecipeManager/RegistryAccess public getter snapshot, not assemble/serve/intake");
        section.addProperty("carrier_resolution_basis","CookingPotRecipe constructor uses explicit nonempty container else actual output.getCraftingRemainingItem; getOutputContainer returns that resolved field; CookingPotBlockEntity.processCooking copies getter into mealContainerStack");
        section.addProperty("serving_basis","native useHeldItemOnMeal shrinks exactly ONE held carrier and splits ONE meal; useStoredContainersOnMeal shrinks carrier count by number of meal items split; not one carrier per whole recipe batch and not getter.count carriers per output");
        section.addProperty("container_matching_basis","isContainerValid checks nonempty mealContainerStack and ItemStack.isSameItem; serialized NBT/count are observations, not claimed required matcher NBT/count");
        section.addProperty("qualification","unqualified_getter_facts_only");
        root.add("native_fd_carriers",section);
        try{
            var type=ModRecipeTypes.COOKING.get();section.addProperty("recipe_type_id",String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(type)));
            List<? extends Recipe<?>> recipes=server.getRecipeManager().getAllRecipesFor(type);
            List<Recipe<?>> ordered=new ArrayList<>(recipes);ordered.sort(Comparator.comparing(recipe->recipe.getId().toString()));
            JsonArray rows=new JsonArray();
            for(Recipe<?> recipe:ordered){
                JsonObject row=new JsonObject();JsonArray errors=new JsonArray();row.addProperty("recipe_id",recipe.getId().toString());row.addProperty("recipe_class",recipe.getClass().getName());
                row.addProperty("dynamic_nbt_status","unknown_not_assembled");row.addProperty("chance_status","unknown_not_executed");row.addProperty("observed_serve",false);
                row.addProperty("unsupported_status",recipe.getClass()==CookingPotRecipe.class?"none_for_getter_snapshot_only":"unreviewed_subclass_or_nonstandard_recipe");
                try{row.addProperty("serializer_id",Objects.requireNonNull(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()),"Unregistered serializer").toString());}catch(Throwable failure){row.add("serializer_id",JsonNull.INSTANCE);error(errors,"serializer_id",failure);}
                try{row.add("result",stack(Objects.requireNonNull(recipe.getResultItem(server.registryAccess()),"Null native result getter")));}catch(Throwable failure){row.add("result",JsonNull.INSTANCE);error(errors,"result_getter",failure);}
                if(recipe instanceof CookingPotRecipe cooking){
                    try{
                        ItemStack resolved=Objects.requireNonNull(cooking.getOutputContainer(),"Null native output-container getter");row.add("resolved_container",stack(resolved));
                        row.add("carrier_debit_per_output_item",JsonNull.INSTANCE);row.addProperty("source_carrier_units_per_output_when_native_valid",1);row.addProperty("carrier_debit_basis","source-proven conditional native per-item rule only; no observed debit/free extraction, including empty getter; getter count retained separately");
                        row.addProperty("resolved_container_authority","actual live CookingPotRecipe.getOutputContainer; constructor-resolved remainder fallback, not raw JSON container field");
                    }catch(Throwable failure){row.add("resolved_container",JsonNull.INSTANCE);row.add("carrier_debit_per_output_item",JsonNull.INSTANCE);error(errors,"resolved_container_getter",failure);}
                }else{row.add("resolved_container",JsonNull.INSTANCE);row.add("carrier_debit_per_output_item",JsonNull.INSTANCE);error(errors,"resolved_container_getter",new IllegalStateException("Native FD type has unsupported non-CookingPotRecipe class"));}
                row.add("errors",errors);row.addProperty("status",errors.size()==0?"observed_public_getters_only":"error_unresolved");rows.add(row);
            }
            section.add("rows",rows);section.addProperty("recipe_count",rows.size());section.addProperty("status","observed_public_getters_only");
        }catch(Throwable failure){section.add("rows",JsonNull.INSTANCE);section.add("recipe_count",JsonNull.INSTANCE);JsonArray errors=new JsonArray();error(errors,"live_recipe_enumeration",failure);section.add("errors",errors);section.addProperty("status","error_unresolved");}
    }
    private static JsonObject stack(ItemStack original){
        ItemStack actual=original.copy();JsonObject result=new JsonObject();result.addProperty("empty",actual.isEmpty());
        result.addProperty("item",Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(actual.getItem()),"Unregistered stack item").toString());result.addProperty("count",actual.getCount());
        CompoundTag saved=actual.save(new CompoundTag());result.addProperty("stack_nbt_snbt",saved.toString());return result;
    }
    private static void error(JsonArray errors,String stage,Throwable failure){JsonObject error=new JsonObject();error.addProperty("stage",stage);error.addProperty("exception",failure.getClass().getName());if(failure.getMessage()==null)error.add("message",JsonNull.INSTANCE);else error.addProperty("message",failure.getMessage());errors.add(error);}
}
