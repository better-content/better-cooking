package com.bettercontent.bettercooking.fixture;

import com.bettercontent.betterspicyfarmers.api.SpiceApi;
import com.bettercontent.betterspicyfarmers.inference.RecipeInference;
import com.bettercontent.bettersurvivalphysiology.metabolism.ConsumableProfiles;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.*;
import com.google.gson.*;
import com.illusivesoulworks.diet.api.DietApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/** Nonshipping live discovery only. No consumption, recipe execution, fake player, or acceptance claim. */
public final class NativeCatalogueCollector {
    public static final String SCHEMA = "better_cooking:native_catalogue.v2";
    private static final String IDENTITY_CONTRACT = "better_cooking:recipe_identity_observation.v2";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private NativeCatalogueCollector() {}

    public static void collect(MinecraftServer server, @Nullable ServerPlayer player, Path output) throws IOException {
        if (!Boolean.getBoolean("better_cooking.discoveryOnly"))
            throw new IllegalStateException("Native catalogue requires inert -Dbetter_cooking.discoveryOnly=true");
        if (!server.isSameThread()) throw new IllegalStateException("Collect on the server thread");
        if (!output.isAbsolute()) throw new IllegalArgumentException("Explicit absolute output path required");
        if (player != null && (player instanceof FakePlayer || player.connection == null || player.hasDisconnected()
                || server.getPlayerList().getPlayer(player.getUUID()) != player))
            throw new IllegalArgumentException("Diet context must be an actual connected server player, never a fixture fake");
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA); root.addProperty("status", "observed_unqualified");
        // Observation candidate only: external binding/consumer validation, NEVER graph/native approval.
        root.addProperty("importable",true);
        root.addProperty("recipe_stack_nbt_basis","item_getTag_snbt_only_full_saved_stack_and_ForgeCaps_not_observed");
        root.addProperty("created_utc", Instant.now().toString());
        root.addProperty("qualification", false); root.addProperty("discovery_only", true);
        root.addProperty("food_tags_orientation", "tag_to_items");
        JsonObject context = new JsonObject();
        context.addProperty("player", player == null ? "none" : player.getUUID().toString());
        context.addProperty("food_properties", "Item.getFoodProperties(defaultStack, nullablePlayer); native discovery-only");
        context.addProperty("nutrients", player == null ? "unresolved: DietApi requires player.level()" : "DietApi potential gains; actual connected player");
        context.addProperty("spice", "SpiceApi.resolveUnits(nullablePlayer, defaultStack); physical-item units");
        var spiceStatus = RecipeInference.status();
        context.addProperty("spice_inference_disabled",spiceStatus.derivedDisabled());
        context.addProperty("spice_inference_recipe_count",spiceStatus.recipeCount());
        context.addProperty("spice_inference_supported_recipe_count",spiceStatus.supportedRecipeCount());
        context.add("spice_inference_failures",JSON.toJsonTree(spiceStatus.resultFailures()));
        root.add("context", context);
        JsonArray registered = new JsonArray(), foods = new JsonArray(), recipes = new JsonArray(), gaps = new JsonArray(), mods = new JsonArray();
        JsonObject blocks = new JsonObject();
        JsonObject resourceInputs = new JsonObject();
        resourceInputs.addProperty("identity_binding","external_root_receipt_required");
        resourceInputs.addProperty("context","Live server ResourceManager/RecipeManager after active reload");
        resourceInputs.add("resource_manager_pack_ids",JSON.toJsonTree(server.getResourceManager().listPacks().map(pack -> pack.packId()).toList()));
        resourceInputs.add("selected_pack_ids",JSON.toJsonTree(server.getPackRepository().getSelectedIds()));
        root.add("resource_inputs",resourceInputs);
        gap(gaps,"resource_inputs","*","input_sha256","Root must bind exact mod/resource input hashes separately; pack IDs are live provenance, not cryptographic identity");
        Map<String,SortedSet<String>> itemTags = new TreeMap<>(), foodTags = new TreeMap<>();
        for (var mod : ModList.get().getMods().stream().sorted(Comparator.comparing(m -> m.getModId())).toList()) {
            JsonObject row = new JsonObject(); row.addProperty("id",mod.getModId()); row.addProperty("version",mod.getVersion().toString()); mods.add(row);
        }
        for (var id : ForgeRegistries.ITEMS.getKeys().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            String name = id.toString(); registered.add(name);
            var item = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(id));
            var stack = item.getDefaultInstance();
            item.builtInRegistryHolder().tags().forEach(tag -> itemTags.computeIfAbsent(tag.location().toString(), ignored -> new TreeSet<>()).add(name));
            JsonObject block = null;
            if (item instanceof BlockItem placed) {
                block = new JsonObject(); block.addProperty("block_id",Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(placed.getBlock())).toString());
                block.addProperty("block_class",placed.getBlock().getClass().getName());
                if (placed.getBlock() instanceof FoodBiteBlock bites) {
                    block.addProperty("native_max_bites",bites.getMaxBites());
                    block.addProperty("native_bites_property",bites.getBites().getName());
                    block.add("native_bites_values",JSON.toJsonTree(bites.getBites().getPossibleValues()));
                    FoodProperties perBite=((com.bettercontent.bettercooking.mixin.FoodBiteAccess)bites).cooking$nativeBiteProperties();
                    block.add("native_per_bite_food",food(perBite,gaps,name));
                    block.addProperty("servings",bites.getMaxBites());
                }
                blocks.add(name,block);
            }
            FoodProperties properties;
            try { properties = item.getFoodProperties(stack,player); }
            catch (RuntimeException failure) { gap(gaps,"food",name,"food_properties",failure.toString()); continue; }
            if (properties == null) {
                if (item.isEdible()) gap(gaps,"food",name,"food_properties","Native edible item returned null in this discovery context");
                continue;
            }
            JsonObject row = food(properties,gaps,name); row.addProperty("id",name);
            row.addProperty("item_class",item.getClass().getName());
            if (block != null) for (var field : block.entrySet()) row.add(field.getKey(),field.getValue().deepCopy());
            item.builtInRegistryHolder().tags().forEach(tag -> foodTags.computeIfAbsent(tag.location().toString(), ignored -> new TreeSet<>()).add(name));
            if (player == null) {
                gap(gaps,"food",name,"nutrients","Pinned DietApiImpl.getGroups dereferences player.level(); no null query or fake-player substitution");
                gap(gaps,"food",name,"sugar","Capped serving sugar requires actual Diet potential gains; not replaced by base sugar or zero");
            } else {
                try {
                    Map<String,Double> gains = new TreeMap<>();
                    DietApi.getInstance().get(player,stack).get().forEach((group,gain) -> gains.put(group.getName(),gain.doubleValue()));
                    row.add("nutrients",JSON.toJsonTree(gains));
                    row.addProperty("sugar",ConsumableProfiles.servingSugar(stack,gains));
                } catch (RuntimeException failure) {
                    gap(gaps,"food",name,"nutrients",failure.toString()); gap(gaps,"food",name,"sugar","Diet potential gains unavailable");
                }
            }
            try { row.addProperty("alcohol",ConsumableProfiles.alcohol(stack)); }
            catch (RuntimeException failure) { gap(gaps,"food",name,"alcohol",failure.toString()); }
            if (spiceStatus.derivedDisabled() || spiceStatus.recipeCount() == 0) {
                gap(gaps,"food",name,"spice","Native Spice inference disabled or uninitialized; conservative API zero is not a resolved dose");
            } else {
                try { row.addProperty("spice",SpiceApi.resolveUnits(player,stack)); }
                catch (RuntimeException failure) { gap(gaps,"food",name,"spice",failure.toString()); }
            }
            foods.add(row);
        }
        var typeObjects = new NativeRecipeIdentityTable<RecipeType<?>,ResourceLocation>("t",List.of(
                new NativeRecipeIdentityTable.Probe<>("builtin",BuiltInRegistries.RECIPE_TYPE::getKey,
                        (actual,key) -> BuiltInRegistries.RECIPE_TYPE.get(key) == actual),
                new NativeRecipeIdentityTable.Probe<>("forge",ForgeRegistries.RECIPE_TYPES::getKey,
                        (actual,key) -> ForgeRegistries.RECIPE_TYPES.getValue(key) == actual)),ResourceLocation::toString);
        var serializerObjects = new NativeRecipeIdentityTable<RecipeSerializer<?>,ResourceLocation>("s",List.of(
                new NativeRecipeIdentityTable.Probe<>("forge",ForgeRegistries.RECIPE_SERIALIZERS::getKey,
                        (actual,key) -> ForgeRegistries.RECIPE_SERIALIZERS.getValue(key) == actual)),ResourceLocation::toString);
        // Snapshot each actual recipe ID once; sorted unique IDs define first-reference ordering.
        record RecipeEntry(ResourceLocation id, Recipe<?> recipe) {}
        var entries = server.getRecipeManager().getRecipes().stream()
                .map(recipe -> new RecipeEntry(Objects.requireNonNull(recipe.getId()),recipe))
                .sorted(Comparator.comparing(entry -> entry.id().toString())).toList();
        for (RecipeEntry entry : entries) {
            Recipe<?> recipe = entry.recipe();
            String id = entry.id().toString(); JsonObject row = new JsonObject();
            // Each virtual getter ONCE; only actual object-reference identity, never source constants.
            var actualType = recipe.getType();
            var actualSerializer = recipe.getSerializer();
            row.addProperty("id",id); row.addProperty("recipe_class",recipe.getClass().getName());
            row.add("native_recipe_type",typeObjects.observe(id,actualType,gaps));
            row.add("native_serializer",serializerObjects.observe(id,actualSerializer,gaps));
            try {
                JsonArray ingredients = new JsonArray();
                // Preserve custom Ingredient.toJson exactly; alternatives are not counted slots.
                for (var ingredient : recipe.getIngredients()) ingredients.add(ingredient.toJson());
                row.add("ingredients",ingredients);
            } catch (RuntimeException failure) { gap(gaps,"recipe",id,"ingredients",failure.toString()); }
            try { row.add("result",stack(recipe.getResultItem(server.registryAccess()))); }
            catch (RuntimeException failure) { gap(gaps,"recipe",id,"result",failure.toString()); }
            try { typed(recipe,row,gaps); }
            catch (RuntimeException failure) { gap(gaps,"recipe",id,"typed_fields",failure.toString()); }
            var resource = server.getResourceManager().getResource(new ResourceLocation(entry.id().getNamespace(),"recipes/"+entry.id().getPath()+".json"));
            if (resource.isPresent()) {
                try (var reader = resource.get().openAsReader()) {
                    row.add("resource_json",JsonParser.parseReader(reader));
                    row.addProperty("resource_json_context","active ResourceManager input; typed runtime fields remain distinct");
                } catch (RuntimeException | IOException failure) { gap(gaps,"recipe",id,"resource_json",failure.toString()); }
            }
            recipes.add(row);
        }
        JsonObject identity = new JsonObject();
        identity.addProperty("contract",IDENTITY_CONTRACT);
        identity.addProperty("scope","one_collect_invocation_only");
        identity.addProperty("invocation_nonce",UUID.randomUUID().toString());
        identity.add("type_objects",typeObjects.nodes());
        identity.add("serializer_objects",serializerObjects.nodes());
        root.add("recipe_identity",identity);
        root.add("registered_items",registered); root.add("block_items",blocks);
        root.add("item_tags",JSON.toJsonTree(itemTags)); root.add("food_tags",JSON.toJsonTree(foodTags));
        root.add("mods",mods); root.add("foods",foods); root.add("recipes",recipes); root.add("gaps",gaps);
        NativeDrinkCollector.append(root,server,player);
        if(player!=null) {
            NativeCarrierCollector.append(server,root);
            NativeFeastCollector.append(server,player,root);
        } else {
            JsonObject unobserved=new JsonObject();
            unobserved.addProperty("status","unobserved");
            unobserved.addProperty("reason","No connected-player discovery context; no carrier facts asserted");
            unobserved.add("rows",JsonNull.INSTANCE);root.add("native_fd_carriers",unobserved);
            root.add("native_fd_feasts",unobserved.deepCopy());
            gaps.add("unobserved-native-fd-carriers:no-connected-player");
            gaps.add("unobserved-native-fd-feasts:no-connected-player");
        }
        byte[] payload = NativeRecipeIdentityTable.encode(root,JSON);
        Files.createDirectories(output.getParent());
        Files.write(output,payload,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
    }

    private static JsonObject food(FoodProperties properties, JsonArray gaps, String id) {
        JsonObject row = new JsonObject(); row.addProperty("hunger",properties.getNutrition());
        row.addProperty("saturation_modifier",properties.getSaturationModifier()); row.addProperty("meat",properties.isMeat());
        row.addProperty("always_eat",properties.canAlwaysEat()); row.addProperty("fast",properties.isFastFood());
        JsonArray effects = new JsonArray();
        try {
            for (var pair : properties.getEffects()) {
                var instance = pair.getFirst(); JsonObject effect = new JsonObject();
                effect.addProperty("id",Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect())).toString());
                effect.addProperty("duration",instance.getDuration()); effect.addProperty("amplifier",instance.getAmplifier());
                effect.addProperty("chance",pair.getSecond()); effects.add(effect);
            }
            row.add("effects",effects);
        } catch (RuntimeException failure) { gap(gaps,"food",id,"effects",failure.toString()); }
        return row;
    }

    private static JsonObject stack(ItemStack stack) {
        JsonObject row = new JsonObject(); row.addProperty("id",Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString());
        row.addProperty("count",stack.getCount()); if (stack.hasTag()) row.addProperty("nbt",stack.getTag().toString()); return row;
    }

    private static void typed(Recipe<?> recipe, JsonObject row, JsonArray gaps) {
        if (recipe instanceof AbstractCookingRecipe cooking) { row.addProperty("cook_ticks",cooking.getCookingTime()); row.addProperty("experience",cooking.getExperience()); }
        if (recipe instanceof CookingPotRecipe pot) { row.addProperty("cook_ticks",pot.getCookTime()); row.add("container",stack(pot.getOutputContainer())); row.addProperty("experience",pot.getExperience()); }
        if (recipe instanceof CuttingBoardRecipe cutting) {
            row.add("tool",cutting.getTool().toJson()); JsonArray results = new JsonArray();
            for (var result : cutting.getRollableResults()) {
                JsonObject output = stack(result.getStack()); output.addProperty("chance",result.getChance()); results.add(output);
            }
            row.add("results",results);
        }
        if (recipe instanceof com.bettercontent.bettercooking.cutting.NativeCuttingRecipe cutting) {
            row.add("tool",cutting.plan().tool().toJson());row.addProperty("cut_count",cutting.plan().cuts());
            row.add("native_ingredient",cutting.plan().ingredient().toJson());row.addProperty("results_semantics","ordered_rollable_outputs_not_guaranteed_preview");
            JsonArray results=new JsonArray();for(var candidate:cutting.plan().outputs()) {
                JsonObject result=stack(candidate.stack());result.addProperty("chance",candidate.chance());results.add(result);
            }
            row.add("results",results);
        } else if (recipe instanceof ChoppingBoardRecipe board) {
            row.addProperty("cut_count",board.getCutCount()); row.add("native_ingredient",board.getIngredient().toJson());
            JsonArray results = new JsonArray(); for (var result : board.getResults()) results.add(stack(result)); row.add("results",results);
        }
        if (recipe instanceof PotRecipe pot) { row.addProperty("cook_ticks",pot.time()); row.addProperty("stir_fry_count",pot.stirFryCount()); row.add("carrier",pot.carrier().toJson()); }
        if (recipe instanceof FlexPotRecipe pot) { row.addProperty("cook_ticks",pot.time()); row.addProperty("stir_fry_count",pot.stirFryCount()); row.add("carrier",pot.carrier().toJson()); }
        if (recipe instanceof StockpotRecipe pot) { row.addProperty("cook_ticks",pot.time()); row.addProperty("soup_base",pot.soupBase().toString()); row.add("carrier",pot.carrier().toJson()); }
        if (recipe instanceof FlexStockpotRecipe pot) { row.addProperty("cook_ticks",pot.time()); row.addProperty("soup_base",pot.soupBase().toString()); row.add("carrier",pot.carrier().toJson()); }
        if (recipe instanceof SteamerRecipe steamer) row.addProperty("cook_ticks",steamer.getCookTick());
        if (recipe instanceof BambooTrayRecipe tray) { row.addProperty("cook_ticks",tray.getDuration()); row.addProperty("subtype",tray.getSubtype().toString()); }
        if (recipe instanceof TeapotRecipe teapot) {
            row.addProperty("cook_ticks",teapot.time()); row.addProperty("tea_fluid",teapot.teaFluid().toString());
            row.add("native_ingredient",teapot.ingredient().toJson()); row.addProperty("ingredient_count",teapot.ingredientCount());
            row.addProperty("native_output_count_constant",TeapotRecipe.OUTPUT_COUNT);
        }
        if (recipe instanceof MillstoneRecipe millstone) {
            row.add("native_ingredient",millstone.ingredient().toJson()); JsonArray results = new JsonArray();
            for (var result : millstone.results()) {
                JsonObject output = stack(result.stack()); output.addProperty("chance",result.chance()); results.add(output);
            }
            row.add("results",results);
        }
    }

    private static void gap(JsonArray gaps, String scope, String id, String field, String reason) {
        JsonObject gap = new JsonObject(); gap.addProperty("scope",scope); gap.addProperty("id",id);
        gap.addProperty("field",field); gap.addProperty("reason",reason); gaps.add(gap);
    }
}
