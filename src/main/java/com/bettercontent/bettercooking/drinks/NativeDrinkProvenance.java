package com.bettercontent.bettercooking.drinks;

import com.bettercontent.bettercooking.CookingRuntime;
import com.bettercontent.bettercooking.policy.CuisineQuality;
import com.bettercontent.betterdrinkingwater.api.ServingHydration;
import com.bettercontent.betterindustrialheat.api.CookingAgeApi;
import com.bettercontent.betterindustrialheat.food.FoodThermalService;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Ordinary native producer receipts only. Never writes Root SERVING or awards/debits anything.
 * P9's single native finish helper calls these methods BEFORE inputs are cleared. */
public final class NativeDrinkProvenance {
    public static final String KEY="better_cooking:native_drink_route";
    private static boolean ageRegistered;
    public record Dose(double hydration,double quenching,Map<String,Double> nutrients,double sugar,double alcohol,double spice) {
        public Dose {nutrients=Map.copyOf(nutrients);}
    }
    private NativeDrinkProvenance() {}
    public static ItemStack decorateTeapot(TeapotBlockEntity source,ItemStack actualOutput,ItemStack actualBag) {
        return decorate(source,actualOutput,List.of(actualBag),"teapot");
    }
    public static ItemStack decorateStockpot(StockpotBlockEntity source,ItemStack actualOutput,List<ItemStack> actualInputs) {
        if(!isNativeDrink(actualOutput))return actualOutput.copy();
        if(source.getTakeoutCount()!=actualOutput.getCount())throw new IllegalArgumentException("Native actual takeout/output yield drift");
        // The existing finish helper compacts its Container snapshot. Bind captures to the
        // ACTUAL owner slots (including holes), never the compacted index of an occupied item.
        List<ItemStack> slots=source.getInputs().stream().map(ItemStack::copy).toList();
        List<ItemStack> owned=slots.stream().filter(stack->!stack.isEmpty()).toList();
        List<ItemStack> supplied=actualInputs.stream().filter(stack->!stack.isEmpty()).toList();
        if(owned.size()!=supplied.size())throw new IllegalArgumentException("Stockpot input snapshot/owner count drift");
        for(int i=0;i<owned.size();i++)if(owned.get(i).getCount()!=supplied.get(i).getCount()||!ItemStack.isSameItemSameTags(owned.get(i),supplied.get(i)))
            throw new IllegalArgumentException("Stockpot input snapshot/owner identity drift");
        return decorate(source,actualOutput,slots,"stockpot");
    }
    private static String id(ItemStack stack) {return Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString();}
    public static boolean isNativeDrink(ItemStack stack) {return !stack.isEmpty()&&NativeDrinkRoutes.IDS.contains(id(stack));}
    private static ItemStack decorate(BlockEntity source,ItemStack original,List<ItemStack> inputs,String route) {
        ItemStack output=original.copy();if(!isNativeDrink(output))return output;
        // Prepared output belongs exclusively to Root's carry authority, never this adapter.
        if(output.hasTag()&&output.getTag().contains(CookingRuntime.PREP))throw new IllegalStateException("Prepared native drink must use Root carry");
        NativeDrinkRoutes.validate(route,id(output),output.getCount());
        if(output.hasTag()&&output.getTag().contains(KEY))throw new IllegalStateException("Native drink finish decorated twice");
        CompoundTag data=new CompoundTag();data.putInt("v",1);data.putString("route",route);data.putString("output",id(output));
        data.putInt("yield",output.getCount());data.putInt("millibuckets",1000);
        CompoundTag base=source instanceof NativeDrinkBaseAccess access?access.cooking$drinkBase():new CompoundTag();
        data.put("base",NativeDrinkCapture.bounded(base));ListTag paid=new ListTag();List<ItemStack> ageInputs=new ArrayList<>();
        if(base.contains("source",Tag.TAG_COMPOUND)) {
            ItemStack stack=ItemStack.of(base.getCompound("source"));if(!stack.isEmpty())ageInputs.add(stack);
        }
        CuisineQuality quality=output.hasTag()&&output.getTag().contains(CookingRuntime.QUALITY,Tag.TAG_INT)
                ?CuisineQuality.fromNative(output.getTag().getInt(CookingRuntime.QUALITY)):CuisineQuality.STANDARD;
        int occupied=0;
        for(ItemStack input:inputs)if(!input.isEmpty()) {
            if(++occupied>9||input.getCount()!=1)throw new IllegalArgumentException("Native drink inputs must be actual count1 slots");
            ItemStack actual=input.copy();paid.add(NativeDrinkCapture.bounded(actual.save(new CompoundTag())));ageInputs.add(actual);
        }
        data.put("inputs",paid);
        for(ItemStack actual:ageInputs)if(actual.hasTag()&&actual.getTag().contains(CookingRuntime.QUALITY,Tag.TAG_INT))
            quality=CuisineQuality.worst(quality,CuisineQuality.fromNative(actual.getTag().getInt(CookingRuntime.QUALITY)));
        data.putInt("quality",quality.nativeId());
        try {
            validateBase(data);
            if(!base.contains("intake",Tag.TAG_COMPOUND))throw new IllegalStateException("Base intake unavailable");
            CompoundTag total=NativeDrinkIngredients.validateIntake(base.getCompound("intake"));
            if(occupied!=(route.equals("teapot")?1:output.getCount()))throw new IllegalStateException("Paid native input/yield drift");
            ListTag facts=new ListTag();
            for(int slot=0;slot<inputs.size();slot++)if(!inputs.get(slot).isEmpty()) {
                CompoundTag captured=source instanceof NativeDrinkBaseAccess access?access.cooking$drinkIngredient(slot):new CompoundTag();
                CompoundTag resolved=NativeDrinkIngredients.resolvedSnapshot(inputs.get(slot),captured);
                add(total,resolved.getCompound("intake"));resolved.putInt("native_slot",slot);facts.add(resolved);
            }
            data.put("input_facts",facts);data.put("intake",total);
        } catch(RuntimeException failure) {data.putString("gap",failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
        ListTag children=new ListTag();for(ItemStack input:ageInputs)children.add(input.copyWithCount(1).save(new CompoundTag()));
        data.put("age_inputs",children);output.getOrCreateTag().put(KEY,NativeDrinkCapture.bounded(data));
        output.getOrCreateTag().putInt(CookingRuntime.QUALITY,quality.nativeId());
        if(ageRegistered&&source.getLevel()!=null)CookingAgeApi.transfer(ageInputs.stream().map(stack->
                new CookingAgeApi.Input(stack,FoodThermalService.Storage.AMBIENT)).toList(),output,source.getLevel().getGameTime());
        return output;
    }
    private static void add(CompoundTag total,CompoundTag next) {
        if(!total.contains("nutrients",Tag.TAG_COMPOUND)||!next.contains("nutrients",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing actual nutrients");
        CompoundTag nutrients=total.getCompound("nutrients");
        for(String key:next.getCompound("nutrients").getAllKeys())nutrients.putDouble(key,NativeDrinkCapture.checked(nutrients.getDouble(key)+next.getCompound("nutrients").getDouble(key)));
        for(String key:List.of("sugar","alcohol","spice")) {
            if(!total.contains(key,Tag.TAG_DOUBLE)||!next.contains(key,Tag.TAG_DOUBLE))throw new IllegalArgumentException("Missing actual "+key);
            total.putDouble(key,NativeDrinkCapture.checked(total.getDouble(key)+next.getDouble(key)));
        }
    }
    /** Root must distinguish unknown NEW receipts from genuinely untagged legacy stock. */
    public static boolean hasReceipt(ItemStack stack){return isNativeDrink(stack)&&stack.hasTag()&&stack.getTag().contains(KEY);}
    private static CompoundTag data(ItemStack stack) {
        if(!isNativeDrink(stack)||!stack.hasTag()||!stack.getTag().contains(KEY))return null;
        if(!stack.getTag().contains(KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Wrong native drink receipt tag type");
        CompoundTag data=NativeDrinkCapture.bounded(stack.getTag().getCompound(KEY));
        if(!data.contains("v",Tag.TAG_INT)||!data.contains("yield",Tag.TAG_INT)||!data.contains("route",Tag.TAG_STRING)||!data.contains("output",Tag.TAG_STRING))
            throw new IllegalArgumentException("Missing native drink receipt identity fields");
        if(data.getInt("v")!=1||!id(stack).equals(data.getString("output")))throw new IllegalArgumentException("Native drink receipt identity/version drift");
        NativeDrinkRoutes.validate(data.getString("route"),id(stack),data.getInt("yield"));return data;
    }
    private static void validateBase(CompoundTag data) {
        CompoundTag base=data.getCompound("base");
        if(!base.contains("v",Tag.TAG_INT)||!base.contains("millibuckets",Tag.TAG_INT)||!data.contains("millibuckets",Tag.TAG_INT)
                ||base.getInt("v")!=1||base.getInt("millibuckets")!=1000||data.getInt("millibuckets")!=1000)
            throw new IllegalStateException("Missing/legacy actual paid1000mB base");
        if(base.getBoolean("unpaid"))throw new IllegalStateException("Creative native fill did not debit a source");
        if(base.getString("kind").equals("fluid")) {
            if(!data.getString("route").equals("teapot")||!base.getString("basis").equals("native_dripstone_1000mb")
                    ||!base.getString("fluid").equals("minecraft:water")||data.getString("output").equals(NativeDrinkRoutes.PREFIX+"butter_tea"))
                throw new IllegalStateException("Unresolved actual fluid source/route");
            if(!base.contains("hydration",Tag.TAG_DOUBLE)||!base.contains("quenching",Tag.TAG_DOUBLE)
                    ||base.getDouble("hydration")!=com.bettercontent.betterdrinkingwater.DrinkHydration.WATER.hydration(1000)
                    ||base.getDouble("quenching")!=com.bettercontent.betterdrinkingwater.DrinkHydration.WATER.quenching(1000))
                throw new IllegalArgumentException("Actual water-fluid quantity drift");
            return;
        }
        ItemStack source=ItemStack.of(base.getCompound("source"));
        if(source.isEmpty()||source.getCount()!=1)throw new IllegalArgumentException("Invalid actual base item");
        String expected=data.getString("route").equals("stockpot")||data.getString("output").equals(NativeDrinkRoutes.PREFIX+"butter_tea")
                ?"minecraft:milk_bucket":"minecraft:water_bucket";
        if(!data.getString("output").equals(NativeDrinkRoutes.MYSTERY)&&!id(source).equals(expected))throw new IllegalStateException("Actual base/route mismatch");
        var current=com.bettercontent.betterdrinkingwater.WaterSources.nativeHydration(source);
        if(current.isEmpty()||!base.contains("hydration",Tag.TAG_DOUBLE)||!base.contains("quenching",Tag.TAG_DOUBLE)
                ||Double.compare(current.get().hydration(),base.getDouble("hydration"))!=0
                ||Double.compare(current.get().quenching(),base.getDouble("quenching"))!=0)throw new IllegalStateException("Unresolved/changed paid Water basis");
    }
    /** Hydration is separately knowable even if actual Diet/Spice facts remain unavailable. */
    public static Optional<ServingHydration.Profile> hydration(ItemStack stack) {
        CompoundTag data=data(stack);if(data==null)return Optional.empty();
        try{validateBase(data);}catch(IllegalStateException gap){return Optional.empty();}
        CompoundTag base=data.getCompound("base");int yield=data.getInt("yield");
        return Optional.of(new ServingHydration.Profile(NativeDrinkRoutes.perCup(base.getDouble("hydration"),yield),NativeDrinkRoutes.perCup(base.getDouble("quenching"),yield)));
    }
    /** Empty is unresolved, NEVER a zero serving or a fabricated legacy250mB cup. */
    public static Optional<Dose> resolve(ItemStack stack) {
        CompoundTag data=data(stack);if(data==null||!data.contains("intake",Tag.TAG_COMPOUND))return Optional.empty();
        var hydration=hydration(stack);if(hydration.isEmpty())return Optional.empty();
        CompoundTag intake=data.getCompound("intake");int yield=data.getInt("yield");Map<String,Double> nutrients=new TreeMap<>();
        if(!intake.contains("nutrients",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing actual nutrient snapshot");
        for(String group:intake.getCompound("nutrients").getAllKeys()) {
            if(!intake.getCompound("nutrients").contains(group,Tag.TAG_DOUBLE))throw new IllegalArgumentException("Wrong nutrient snapshot type");
            nutrients.put(group,NativeDrinkRoutes.perCup(NativeDrinkCapture.checked(intake.getCompound("nutrients").getDouble(group)),yield));
        }
        for(String key:List.of("sugar","alcohol","spice"))if(!intake.contains(key,Tag.TAG_DOUBLE))throw new IllegalArgumentException("Incomplete actual drink intake");
        return Optional.of(new Dose(hydration.get().hydration(),hydration.get().quenching(),nutrients,
                NativeDrinkRoutes.perCup(NativeDrinkCapture.checked(intake.getDouble("sugar")),yield),
                NativeDrinkRoutes.perCup(NativeDrinkCapture.checked(intake.getDouble("alcohol")),yield),
                NativeDrinkRoutes.perCup(NativeDrinkCapture.checked(intake.getDouble("spice")),yield)));
    }
    public static List<ItemStack> ageInputs(ItemStack stack) {
        CompoundTag data=data(stack);if(data==null)return List.of();List<ItemStack> inputs=new ArrayList<>();
        if(!data.contains("age_inputs",Tag.TAG_LIST))throw new IllegalArgumentException("Missing actual age children");
        ListTag rawRows=(ListTag)data.get("age_inputs"),rows=data.getList("age_inputs",Tag.TAG_COMPOUND);
        int expected=data.getList("inputs",Tag.TAG_COMPOUND).size()+(data.getCompound("base").contains("source",Tag.TAG_COMPOUND)?1:0);
        if(rows.size()!=rawRows.size()||rows.isEmpty()||rows.size()>10||rows.size()!=expected)throw new IllegalArgumentException("Invalid native drink age input list/count");
        for(Tag raw:rows){ItemStack input=ItemStack.of((CompoundTag)raw);if(input.isEmpty()||input.getCount()!=1)throw new IllegalArgumentException("Invalid drink age child");inputs.add(input);}
        return List.copyOf(inputs);
    }
    /** Root calls during common registration BEFORE CookingAgeApi freezes. No shared mod edits here. */
    public static synchronized void registerAgeCarrier() {
        if(ageRegistered)throw new IllegalStateException("Native drink age carrier registered twice");
        CookingAgeApi.registerCarrier(new ResourceLocation("better_cooking","native_drinks"),new CookingAgeApi.Carrier(){
            public boolean handles(ItemStack stack){return isNativeDrink(stack)&&stack.hasTag()&&stack.getTag().contains(KEY,Tag.TAG_COMPOUND);}
            public List<ItemStack> read(ItemStack stack){return ageInputs(stack);}
            public void write(ItemStack stack,List<ItemStack> children){
                List<ItemStack> originals=ageInputs(stack);
                if(children.size()!=originals.size())throw new IllegalArgumentException("Native drink age child count drift");
                for(int i=0;i<children.size();i++)if(!children.get(i).is(originals.get(i).getItem()))throw new IllegalArgumentException("Native drink age child identity drift");
                CompoundTag data=stack.getTag().getCompound(KEY).copy();ListTag list=new ListTag();
                for(ItemStack child:children){if(child.isEmpty()||child.getCount()!=1)throw new IllegalArgumentException("Invalid settled drink child");list.add(child.save(new CompoundTag()));}
                data.put("age_inputs",list);stack.getTag().put(KEY,NativeDrinkCapture.bounded(data));
            }
        });ageRegistered=true;
    }
}
