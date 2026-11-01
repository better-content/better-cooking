package com.bettercontent.bettercooking.fixture;
import com.google.gson.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import vectorwing.farmersdelight.common.block.FeastBlock;

/** Fixture-only public getter observations on typed FD FeastBlock items, never world placement/serving/intake. */
public final class NativeFeastCollector {
    private NativeFeastCollector() {}
    public static void append(MinecraftServer server,ServerPlayer player,JsonObject root){
        if(!Boolean.getBoolean("better_cooking.discoveryOnly")||!server.isSameThread())throw new IllegalStateException("Connected discovery server-thread snapshot required");
        if(player==null||player instanceof FakePlayer||player.connection==null||server.getPlayerList().getPlayer(player.getUUID())!=player)throw new IllegalArgumentException("Actual connected player required for serving FoodProperties context");
        if(root.has("native_fd_feasts"))throw new IllegalArgumentException("Native feast rows already appended");
        JsonObject section=new JsonObject();section.addProperty("schema_version",1);section.addProperty("authority","FD7d13 FeastBlock public virtual getters on live registered BlockItems");section.addProperty("status","getter_observations_only_unqualified");section.addProperty("observation_scope","default state only; no placement/eat/serving/debit/carrier/drop/payment acceptance");section.addProperty("player_uuid",player.getUUID().toString());JsonArray rows=new JsonArray();section.add("rows",rows);root.add("native_fd_feasts",section);
        for(Item item:ForgeRegistries.ITEMS.getValues().stream().sorted(Comparator.comparing(value->String.valueOf(ForgeRegistries.ITEMS.getKey(value)))).toList()){
            if(!(item instanceof BlockItem blockItem)||!(blockItem.getBlock() instanceof FeastBlock feast))continue;
            JsonObject row=new JsonObject();JsonArray errors=new JsonArray();row.addProperty("block_item_id",String.valueOf(ForgeRegistries.ITEMS.getKey(item)));row.addProperty("block_id",String.valueOf(ForgeRegistries.BLOCKS.getKey(feast)));row.addProperty("block_class",feast.getClass().getName());row.addProperty("item_class",item.getClass().getName());
            var state=feast.defaultBlockState();row.addProperty("default_block_state",state.toString());row.addProperty("default_block_state_nbt_snbt",NbtUtils.writeBlockState(state).toString());
            try{var property=Objects.requireNonNull(feast.getServingsProperty(),"Null public servings property");row.addProperty("servings_property",property.getName());JsonArray domain=new JsonArray();property.getPossibleValues().stream().sorted().forEach(domain::add);row.add("servings_property_domain",domain);row.addProperty("default_remaining",state.getValue(property));}catch(Throwable failure){row.add("servings_property",JsonNull.INSTANCE);row.add("servings_property_domain",JsonNull.INSTANCE);row.add("default_remaining",JsonNull.INSTANCE);error(errors,"servings_property/default",failure);}
            try{row.addProperty("max_servings",feast.getMaxServings());}catch(Throwable failure){row.add("max_servings",JsonNull.INSTANCE);error(errors,"max_servings_getter",failure);}
            try{
                ItemStack serving=Objects.requireNonNull(feast.getServingItem(state),"Null public serving item").copy();JsonObject stack=new JsonObject();stack.addProperty("empty",serving.isEmpty());stack.addProperty("item",String.valueOf(ForgeRegistries.ITEMS.getKey(serving.getItem())));stack.addProperty("count",serving.getCount());stack.addProperty("stack_nbt_snbt",serving.save(new CompoundTag()).toString());row.add("default_state_serving_item",stack);
                try{
                    var food=serving.getItem().getFoodProperties(serving,player);
                    if(food==null){row.add("serving_food_properties",JsonNull.INSTANCE);row.addProperty("food_properties_status","native_null");}
                    else{JsonObject properties=new JsonObject();properties.addProperty("nutrition",food.getNutrition());properties.addProperty("saturation_modifier",food.getSaturationModifier());properties.addProperty("can_always_eat",food.canAlwaysEat());properties.addProperty("fast_food",food.isFastFood());properties.addProperty("meat",food.isMeat());row.add("serving_food_properties",properties);row.addProperty("food_properties_status","observed_public_getter_with_connected_player");}
                }catch(Throwable failure){row.add("serving_food_properties",JsonNull.INSTANCE);row.addProperty("food_properties_status","error_unknown");error(errors,"serving_food_properties",failure);}
            }catch(Throwable failure){row.add("default_state_serving_item",JsonNull.INSTANCE);row.add("serving_food_properties",JsonNull.INSTANCE);row.addProperty("food_properties_status","unknown_no_serving_item");error(errors,"serving_item_getter",failure);}
            row.addProperty("other_state_serving_items_status","unknown_not_enumerated");row.addProperty("effects_status","unknown_not_sampled");row.addProperty("observed_serving_or_intake",false);row.add("errors",errors);row.addProperty("status",errors.size()==0?"observed_public_getters_only":"error_unresolved");rows.add(row);
        }
        section.addProperty("block_item_count",rows.size());
    }
    private static void error(JsonArray errors,String stage,Throwable failure){JsonObject error=new JsonObject();error.addProperty("stage",stage);error.addProperty("exception",failure.getClass().getName());if(failure.getMessage()==null)error.add("message",JsonNull.INSTANCE);else error.addProperty("message",failure.getMessage());errors.add(error);}
}
