package com.bettercontent.bettercooking.fixture;

import com.bettercontent.bettercooking.drinks.NativeDrinkRoutes;
import com.bettercontent.bettercooking.drinks.NativeDrinkCapture;
import com.bettercontent.bettercooking.drinks.NativeDrinkFactBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.bettercontent.betterspicyfarmers.api.SpiceApi;
import com.bettercontent.betterspicyfarmers.inference.RecipeInference;
import com.bettercontent.bettersurvivalphysiology.metabolism.ConsumableProfiles;
import com.github.ysbbbbbb.kaleidoscopecookery.init.registry.TeacupRegistry;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Nonshipping, real connected-player native GETTER discovery. Never consumes/executes a recipe.
 * Source-only finish declarations are nested and MUST NOT be called observed Finish evidence. */
public final class NativeDrinkCollector {
    private static final Gson JSON=new Gson();
    private NativeDrinkCollector() {}
    public static void append(JsonObject root,MinecraftServer server,ServerPlayer player) {
        if(!Boolean.getBoolean("better_cooking.discoveryOnly"))throw new IllegalStateException("Discovery-only flag required");
        if(!server.isSameThread())throw new IllegalStateException("Native drink collection requires server thread");
        if(player==null||player instanceof FakePlayer||player.connection==null||server.getPlayerList().getPlayer(player.getUUID())!=player)
            throw new IllegalArgumentException("Native drink Diet context requires actual connected player");
        if(root.has("native_consumables")||root.has("native_drink_ingredients"))throw new IllegalArgumentException("Native drink rows already appended");
        JsonArray rows=new JsonArray();var status=RecipeInference.status();
        for(String name:NativeDrinkRoutes.IDS.stream().sorted().toList()) {
            JsonObject row=new JsonObject(),gaps=new JsonObject(),intake=new JsonObject();row.addProperty("id",name);
            var id=new ResourceLocation(name);
            if(!ForgeRegistries.ITEMS.containsKey(id)) {gaps.addProperty("item","Missing exact pinned native ID");row.add("gaps",gaps);rows.add(row);continue;}
            var item=Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(id));var stack=item.getDefaultInstance();
            row.addProperty("item_class",item.getClass().getName());row.addProperty("is_edible",item.isEdible());
            var properties=item.getFoodProperties(stack,player);
            if(properties==null)row.add("food_properties",JsonNull.INSTANCE);
            else {
                JsonObject food=new JsonObject();food.addProperty("hunger",properties.getNutrition());food.addProperty("saturation_modifier",properties.getSaturationModifier());
                row.add("food_properties",food);gaps.addProperty("source_drift","Pinned native nonedible BlockItem now returned FoodProperties");
            }
            row.addProperty("use_animation",stack.getUseAnimation().name().toLowerCase(Locale.ROOT));
            row.addProperty("use_duration_ticks",stack.getUseDuration());
            String evidence=name.equals(NativeDrinkRoutes.MILK_TEA)?"kc1.6.0:ClayPotMilkTeaItem#finishUsingItem":"kc1.6.0:TeacupItem#finishUsingItem";
            row.addProperty("remainder",name.equals(NativeDrinkRoutes.MILK_TEA)?"minecraft:flower_pot":"kaleidoscope_cookery:empty_cup");
            row.addProperty("remainder_basis","source_only:"+evidence+"; NOT observed return receipt");
            JsonObject source=new JsonObject();source.addProperty("source_evidence",evidence);
            source.addProperty("food_data_eat_call",false);source.addProperty("observed_finish",false);
            source.addProperty("native_consumption","one whole item at native32tick completion; no placed intake");row.add("source_only",source);
            var gains=ConsumableProfiles.dietGains(player,stack);
            if(gains.isPresent()) {
                row.add("nutrients",JSON.toJsonTree(new TreeMap<>(gains.get())));
                intake.addProperty("sugar",ConsumableProfiles.servingSugar(stack,gains.get()));
            } else {
                row.add("nutrients",JsonNull.INSTANCE);intake.add("sugar",JsonNull.INSTANCE);
                gaps.addProperty("nutrients","Diet potential gains unavailable; not substituted with empty map or zero");
            }
            intake.addProperty("alcohol",ConsumableProfiles.alcohol(stack));
            if(status.derivedDisabled()||status.recipeCount()==0) {
                intake.add("spice",JsonNull.INSTANCE);gaps.addProperty("spice","Spice inference unavailable; conservative zero is not a resolved dose");
            } else intake.addProperty("spice",SpiceApi.resolveUnits(player,stack));
            // PolicyRepository.nativeIntake consumes these exact FLAT fields.
            for(var field:intake.entrySet())row.add(field.getKey(),field.getValue());
            JsonArray declarations=new JsonArray();var tea=TeacupRegistry.TEACUP_DATA_MAP.get(id);
            if(tea!=null)for(var pair:tea.getEffects()) {
                var effect=pair.getFirst().get();JsonObject declaration=new JsonObject();
                declaration.addProperty("id",Objects.requireNonNull(ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect())).toString());
                declaration.addProperty("duration",effect.getDuration());declaration.addProperty("amplifier",effect.getAmplifier());
                declaration.addProperty("probability",pair.getSecond());declaration.addProperty("basis","native registry declaration, NOT selected/awarded Finish effect");declarations.add(declaration);
            }
            row.add("native_effect_declarations",declarations);
            if(name.equals(NativeDrinkRoutes.MILK_TEA))source.addProperty("native_utility","remove every active harmful effect; no addEffect dose");
            row.add("gaps",gaps);rows.add(row);
        }
        root.add("native_consumables",rows);
        ingredients(root,player);
        JsonObject context=new JsonObject();context.addProperty("player",player.getUUID().toString());context.addProperty("status","discovery_getters_only");
        context.addProperty("qualification",false);context.addProperty("observed_finish",false);context.addProperty("spice_inference_disabled",status.derivedDisabled());
        context.addProperty("spice_recipe_count",status.recipeCount());root.add("native_consumables_context",context);
    }
    private static void ingredients(JsonObject root,ServerPlayer player){
        SortedSet<ResourceLocation> ids=new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        for(String bag:List.of("barley_tea_bag","biluochun_tea_bag","butter_tea_bag","oolong_tea_bag","sakura_fubuki_tea_bag","tieguanyin_tea_bag"))
            ids.add(new ResourceLocation("kaleidoscope_cookery",bag));
        ResourceLocation flowerId=new ResourceLocation("kaleidoscope_cookery","flower_tea_ingredients");
        var flower=TagKey.create(Registries.ITEM,flowerId);JsonArray liveFlowers=new JsonArray();
        for(var item:Objects.requireNonNull(ForgeRegistries.ITEMS.tags()).getTag(flower)){
            ResourceLocation id=Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item));ids.add(id);liveFlowers.add(id.toString());
        }
        ids.add(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(Items.WATER_BUCKET)));ids.add(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(Items.MILK_BUCKET)));
        JsonArray rows=new JsonArray();var inference=RecipeInference.status();
        for(ResourceLocation id:ids){
            JsonObject row=new JsonObject(),gaps=new JsonObject();row.addProperty("id",id.toString());
            if(!ForgeRegistries.ITEMS.containsKey(id)){gaps.addProperty("item","Missing explicit native ingredient");row.add("gaps",gaps);rows.add(row);continue;}
            var item=ForgeRegistries.ITEMS.getValue(id);ItemStack actual=item.getDefaultInstance().copyWithCount(1);
            // Explicit observed INPUT TEMPLATE, not paid material and not an untagged default claim.
            if(actual.is(Items.WATER_BUCKET))actual=dev.ghen.thirst.content.purity.WaterPurity.addPurity(actual,3);
            row.addProperty("observation_kind","observed_template");
            row.addProperty("template_basis",actual.is(Items.WATER_BUCKET)?"provider_purity3_variant":"native_default");
            row.addProperty("input_template",actual.save(new CompoundTag()).toString());row.addProperty("item_class",item.getClass().getName());row.addProperty("is_edible",item.isEdible());
            var food=item.getFoodProperties(actual,player);
            if(food==null)row.add("food_properties",JsonNull.INSTANCE);else{
                JsonObject properties=new JsonObject();properties.addProperty("hunger",food.getNutrition());properties.addProperty("saturation_modifier",food.getSaturationModifier());row.add("food_properties",properties);
            }
            var gains=ConsumableProfiles.dietGains(player,actual);
            if(gains.isPresent()){row.add("nutrients",JSON.toJsonTree(new TreeMap<>(gains.get())));row.addProperty("sugar",ConsumableProfiles.servingSugar(actual,gains.get()));}
            else{row.add("nutrients",JsonNull.INSTANCE);row.add("sugar",JsonNull.INSTANCE);gaps.addProperty("nutrients","Actual Diet potential gains unavailable");}
            row.addProperty("alcohol",ConsumableProfiles.alcohol(actual));
            if(inference.derivedDisabled()||inference.recipeCount()==0){row.add("spice",JsonNull.INSTANCE);gaps.addProperty("spice","Actual inference unavailable; no zero substitution");}
            else row.addProperty("spice",SpiceApi.resolveUnits(player,actual));
            JsonObject paid=new JsonObject();
            try{
                CompoundTag intake=NativeDrinkCapture.intake(player,actual,actual.is(Items.MILK_BUCKET));
                paid.addProperty("complete",true);JsonObject nutrients=new JsonObject();
                for(String group:intake.getCompound("nutrients").getAllKeys())nutrients.addProperty(group,intake.getCompound("nutrients").getDouble(group));
                paid.add("nutrients",nutrients);for(String field:List.of("sugar","alcohol","spice"))paid.addProperty(field,intake.getDouble(field));
            }catch(RuntimeException unknown){
                paid.addProperty("complete",false);for(String field:List.of("nutrients","sugar","alcohol","spice"))paid.add(field,JsonNull.INSTANCE);
                paid.addProperty("reason",unknown.getMessage()==null?unknown.getClass().getSimpleName():unknown.getMessage());
            }
            row.add("paid_intake",paid);row.add("gaps",gaps);rows.add(row);
        }
        root.add("native_drink_ingredients",rows);JsonObject context=new JsonObject();
        context.addProperty("schema",NativeDrinkFactBinding.SCHEMA);context.addProperty("status","observed_getters_unbound_runtime");
        context.addProperty("qualification",false);context.addProperty("auto_install",false);context.addProperty("flower_ingredient_tag",flowerId.toString());
        context.add("actual_live_flower_members",liveFlowers);context.addProperty("runtime_input_identity","UNBOUND: Root actual provider/resource byte attestation required; pack IDs are not hashes");
        context.addProperty("paid_intake_basis","actual read-only whole-item API; exact actual milk uses approved typed beverage profile, not raw Diet fallback");
        root.add("native_drink_ingredients_context",context);
    }
}
