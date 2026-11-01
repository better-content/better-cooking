package com.bettercontent.bettercooking.drinks;

import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Immutable facts captured at actual native insertion; finish never needs an online actor. */
public final class NativeDrinkIngredients {
    public static final String KEY="better_cooking:native_drink_ingredients";
    private static final Set<String> ORDINARY=Set.of("proteins","grains","fruits","fats","dairy","vegetables");
    private static final NativeDrinkFactBinding<CompoundTag> DEFAULTS=new NativeDrinkFactBinding<>();
    private NativeDrinkIngredients() {}
    public static boolean connected(LivingEntity actor){
        return actor instanceof ServerPlayer player&&!(player instanceof FakePlayer)&&player.connection!=null
                &&player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID())==player;
    }
    public static CompoundTag snapshot(LivingEntity actor,ItemStack actual){
        ItemStack original=NativeDrinkCapture.snapshotItem(actual);
        if(original.isEmpty())throw new IllegalArgumentException("Cannot snapshot empty native ingredient");
        CompoundTag row=new CompoundTag();row.putInt("v",1);row.put("original",original.save(new CompoundTag()));
        try {
            CompoundTag intake;
            if(connected(actor)) {
                row.putUUID("actor",actor.getUUID());
                try{intake=NativeDrinkCapture.intake((ServerPlayer)actor,original,original.is(Items.MILK_BUCKET));row.putString("basis","actual_connected_insertion");}
                catch(RuntimeException failure){
                    intake=boundIntake(original,row).orElseThrow(()->failure);row.putString("basis","verified_exact_default_variant");
                }
            } else {
                intake=boundIntake(original,row).orElseThrow(()->new IllegalStateException("No actual actor or verified exact-default facts"));
                row.putString("basis","verified_exact_default_variant");
            }
            row.put("intake",validateIntake(intake));
        } catch(RuntimeException failure){row.putString("gap",failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
        return NativeDrinkCapture.bounded(row);
    }
    public static Optional<CompoundTag> defaultIntake(ItemStack actual){
        if(actual.isEmpty())return Optional.empty();
        return DEFAULTS.find(key(NativeDrinkCapture.snapshotItem(actual).save(new CompoundTag()))).map(CompoundTag::copy);
    }
    private static Optional<CompoundTag> boundIntake(ItemStack actual,CompoundTag receipt){
        return DEFAULTS.findBound(key(NativeDrinkCapture.snapshotItem(actual).save(new CompoundTag()))).map(bound->{
            CompoundTag binding=new CompoundTag();binding.putString("runtime_inputs_sha256",bound.runtimeInputsSha256());binding.putString("native_snapshot_sha256",bound.nativeSnapshotSha256());
            receipt.put("default_binding",binding);return bound.value().copy();
        });
    }
    /** A captured paid slot may age without rerolling nutrition; its item/count must remain native.
     * Missing captures (e.g. direct automation) can use ONLY exact full-NBT verified defaults. */
    public static CompoundTag paidIntake(ItemStack actual,CompoundTag row){return resolvedSnapshot(actual,row).getCompound("intake").copy();}
    public static CompoundTag resolvedSnapshot(ItemStack actual,CompoundTag captured){
        CompoundTag row=NativeDrinkCapture.bounded(captured);
        if(!row.isEmpty()){
            if(row.getInt("v")!=1||!row.contains("original",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Corrupt native ingredient capture");
            ItemStack original=ItemStack.of(row.getCompound("original"));
            if(original.isEmpty()||actual.getCount()!=1||original.getCount()!=1||!actual.is(original.getItem()))
                throw new IllegalArgumentException("Native ingredient paid slot identity/count drift");
            if(row.contains("intake")){
                if(!row.contains("intake",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Corrupt captured intake type");
                validateIntake(row.getCompound("intake"));return row;
            }
            if(!row.contains("gap",Tag.TAG_STRING))throw new IllegalArgumentException("Unresolved insertion capture missing gap");
            // An unresolved insertion can gain exact default facts only after a real bound snapshot;
            // do not query a player later or reinterpret an arbitrary metadata-bearing variant.
        }
        CompoundTag intake=boundIntake(actual,row).orElseThrow(()->new IllegalStateException("Actual native ingredient intake unresolved"));
        row.putInt("v",1);row.put("original",NativeDrinkCapture.snapshotItem(actual).save(new CompoundTag()));
        row.putString("basis","verified_exact_default_variant");row.remove("gap");row.put("intake",validateIntake(intake));return NativeDrinkCapture.bounded(row);
    }
    public static CompoundTag restoreCaptures(CompoundTag saved,List<ItemStack> actual){
        CompoundTag result=NativeDrinkCapture.bounded(saved);
        if(result.size()>9)throw new IllegalArgumentException("Too many native insertion captures");
        for(String key:List.copyOf(result.getAllKeys())){
            int slot;try{slot=Integer.parseInt(key);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid native input slot",invalid);}
            if(slot<0||slot>=actual.size()||!key.equals(Integer.toString(slot))||!result.contains(key,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid native insertion slot/type");
            CompoundTag row=result.getCompound(key);
            if(row.getInt("v")!=1||!row.contains("original",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid native insertion version/original");
            ItemStack original=ItemStack.of(row.getCompound("original"));
            if(original.isEmpty()||original.getCount()!=1)throw new IllegalArgumentException("Invalid actual paid original");
            if(actual.get(slot).isEmpty()){result.remove(key);continue;} // Native completion/removal already cleared it.
            if(actual.get(slot).getCount()!=1||!actual.get(slot).is(original.getItem()))throw new IllegalArgumentException("Saved insertion/native slot identity drift");
            if(row.contains("intake")){if(!row.contains("intake",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid captured intake tag type");validateIntake(row.getCompound("intake"));}
            else if(!row.contains("gap",Tag.TAG_STRING))throw new IllegalArgumentException("Unresolved capture missing explicit gap");
        }
        return result;
    }
    public static CompoundTag validateIntake(CompoundTag intake){
        if(!intake.getAllKeys().equals(Set.of("nutrients","sugar","alcohol","spice"))||!intake.contains("nutrients",Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Incomplete native ingredient intake");
        CompoundTag nutrients=intake.getCompound("nutrients");
        for(String group:nutrients.getAllKeys()){
            if(!ORDINARY.contains(group)||!nutrients.contains(group,Tag.TAG_DOUBLE))throw new IllegalArgumentException("Invalid actual ordinary nutrient");
            NativeDrinkCapture.checked(nutrients.getDouble(group));
        }
        for(String field:List.of("sugar","alcohol","spice")){
            if(!intake.contains(field,Tag.TAG_DOUBLE))throw new IllegalArgumentException("Missing actual "+field);
            NativeDrinkCapture.checked(intake.getDouble(field));
        }
        return NativeDrinkCapture.bounded(intake);
    }
    /** Root MUST independently verify actual current provider/resource BYTES and raw snapshot bytes.
     * No auto-install. Domain excludes only approved self-derived outputs, NOT tags/server overrides.
     * Root calls observeRuntimeInputs/invalidateDefaults on reload/definition change BEFORE lookup. */
    public static void install(JsonObject manifest,String currentInputsSha,String actualSnapshotSha){
        DEFAULTS.install(manifest,currentInputsSha,actualSnapshotSha,NativeDrinkIngredients::parseDefaults);
    }
    public static void observeRuntimeInputs(String currentInputsSha){DEFAULTS.observeRuntimeInputs(currentInputsSha);}
    public static void invalidateDefaults(){DEFAULTS.clear();}
    private static Map<String,CompoundTag> parseDefaults(JsonObject manifest){
        if(!manifest.has("native_drink_ingredients")||!manifest.get("native_drink_ingredients").isJsonArray())throw new IllegalArgumentException("Missing actual ingredient rows");
        JsonArray rows=manifest.getAsJsonArray("native_drink_ingredients");if(rows.size()>512)throw new IllegalArgumentException("Too many default ingredients");
        Map<String,CompoundTag> candidate=new HashMap<>();
        for(JsonElement raw:rows){
            if(!raw.isJsonObject())throw new IllegalArgumentException("Invalid actual ingredient row");JsonObject row=raw.getAsJsonObject();
            if(row.has("default_stack")||!row.has("observation_kind")||!row.get("observation_kind").isJsonPrimitive()
                    ||!row.getAsJsonPrimitive("observation_kind").isString()||!row.get("observation_kind").getAsString().equals("observed_template"))
                throw new IllegalArgumentException("Explicit v2 observed input template required; no v1 auto-upgrade");
            if(!row.has("paid_intake")||row.get("paid_intake").isJsonNull())continue; // Explicit unresolved rows stay absent, never zero.
            if(!row.get("paid_intake").isJsonObject())throw new IllegalArgumentException("Invalid paid-intake object");
            JsonObject paid=row.getAsJsonObject("paid_intake");
            if(!paid.has("complete")||!paid.get("complete").isJsonPrimitive()||!paid.getAsJsonPrimitive("complete").isBoolean())
                throw new IllegalArgumentException("Missing actual paid-intake completeness");
            if(!paid.get("complete").getAsBoolean())continue;
            if(!row.has("input_template")||!row.get("input_template").isJsonPrimitive()||!row.getAsJsonPrimitive("input_template").isString()
                    ||!row.has("template_basis")||!row.get("template_basis").isJsonPrimitive()||!row.getAsJsonPrimitive("template_basis").isString())
                throw new IllegalArgumentException("Missing explicit count1 input template/basis");
            String text=row.get("input_template").getAsString();checkSnbtStructure(text);
            CompoundTag original;
            try{original=NativeDrinkCapture.bounded(TagParser.parseTag(text));}catch(Exception invalid){throw new IllegalArgumentException("Invalid native default stack SNBT",invalid);}
            if(!original.contains("id",Tag.TAG_STRING)||!original.contains("Count",Tag.TAG_BYTE)||original.getByte("Count")!=1)
                throw new IllegalArgumentException("Default stack must be actual serialized count1");
            ItemStack stack=ItemStack.of(original);if(stack.isEmpty()||stack.getCount()!=1||!row.has("id")
                    ||!Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString().equals(row.get("id").getAsString()))
                throw new IllegalArgumentException("Default ingredient identity drift");
            // Exact CURRENT public-provider template, not a claim of paid material or an untagged
            // getDefaultInstance for WATER. Attestor must first prove actual connected observations.
            String basis=row.get("template_basis").getAsString();
            ItemStack expected=stack.getItem().getDefaultInstance().copyWithCount(1);
            if(stack.is(Items.WATER_BUCKET)){
                if(!basis.equals("provider_purity3_variant"))throw new IllegalArgumentException("Water cannot masquerade as native_default");
                expected=dev.ghen.thirst.content.purity.WaterPurity.addPurity(expected,3);
            }else if(!basis.equals("native_default"))throw new IllegalArgumentException("Unsupported input template basis");
            if(!expected.save(new CompoundTag()).equals(original))
                throw new IllegalArgumentException("Not the exact current native/provider template variant");
            JsonObject intake=row.getAsJsonObject("paid_intake");CompoundTag dose=new CompoundTag(),nutrients=new CompoundTag();
            if(!intake.keySet().equals(Set.of("complete","nutrients","sugar","alcohol","spice"))||!intake.get("nutrients").isJsonObject())
                throw new IllegalArgumentException("Incomplete actual paid-intake facts");
            for(var gain:intake.getAsJsonObject("nutrients").entrySet())nutrients.putDouble(gain.getKey(),number(gain.getValue()));
            dose.put("nutrients",nutrients);for(String field:List.of("sugar","alcohol","spice"))dose.putDouble(field,number(intake.get(field)));
            if(candidate.putIfAbsent(key(original),validateIntake(dose))!=null)throw new IllegalArgumentException("Duplicate default variant");
        }
        return candidate;
    }
    private static double number(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Unknown actual dose is not zero");
        return NativeDrinkCapture.checked(value.getAsDouble());
    }
    private static void checkSnbtStructure(String text){
        if(text.length()>65536)throw new IllegalArgumentException("Default stack too large");
        int depth=0;char quote=0;boolean escaped=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quote!=0){if(escaped){escaped=false;continue;}if(c=='\\'){escaped=true;continue;}if(c==quote)quote=0;continue;}
            if(c=='\"'||c=='\''){quote=c;continue;}
            if(c=='{'||c=='['){if(++depth>24)throw new IllegalArgumentException("Default SNBT nesting bound");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Invalid default SNBT nesting");}
        }
        if(depth!=0||quote!=0)throw new IllegalArgumentException("Incomplete default stack SNBT");
    }
    private static String key(CompoundTag original){return original.toString();} // Native SNBT visitor sorts compound keys.
}
