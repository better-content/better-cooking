package com.bettercontent.bettercooking;

import com.bettercontent.bettercooking.policy.*;
import com.bettercontent.bettercooking.recipe.PreparationRecipe;
import com.bettercontent.betterindustrialheat.api.CookingAgeApi;
import com.bettercontent.betterindustrialheat.food.FoodThermalService;
import com.bettercontent.betterspicyfarmers.api.SpiceApi;
import com.bettercontent.bettersurvivalphysiology.metabolism.ConsumableProfiles;
import com.github.ysbbbbbb.kaleidoscopecookery.item.quality.QualityEvaluator;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

/** Committed recipe-state and unscaled ingredient provenance; no personal skill or work-point balance. */
public final class CookingRuntime {
    public static final String PREP="better_cooking:preparation", SERVING="better_cooking:serving", QUALITY="kaleidoscope_cookery:quality";
    private CookingRuntime() {}
    public static FoodProfile profile(ItemStack stack) {
        var key=ForgeRegistries.ITEMS.getKey(stack.getItem()); if(key==null) return null;
        var policy=PolicyRepository.current(); FoodProfile value=policy.food(key.toString());
        return value!=null?value:policy.food(policy.aliases().getOrDefault(key.toString(),key.toString()));
    }
    public static final String SINGLE_PORTION="better_cooking:single_portion", NATIVE_BITE="better_cooking:native_bite", AGE_INPUTS="age_inputs";
    public static double portionCount(ItemStack stack) {
        FoodProfile food=profile(stack);if(food==null)return 1;
        double whole=food.quantityBasis().equals("per_portion")?food.servings():1;
        if(stack.hasTag()&&stack.getTag().getBoolean(SINGLE_PORTION))return whole/food.servings();
        return whole*com.bettercontent.betterspicyfarmers.seasoning.ServingContext.intrinsicFraction(stack);
    }
    private static double ediblePortions(ItemStack stack) {
        FoodProfile food=profile(stack);if(food==null)return 1;
        if(stack.hasTag()&&stack.getTag().getBoolean(SINGLE_PORTION))return 1;
        return food.servings()*com.bettercontent.betterspicyfarmers.seasoning.ServingContext.intrinsicFraction(stack);
    }
    private static Map<String,Double> scaledVector(Map<String,Double> source,double factor) {
        Map<String,Double> result=new TreeMap<>();source.forEach((group,value)->result.put(group,value*factor));return result;
    }
    private static double sauceSpice(ItemStack stack) {
        return com.bettercontent.betterspicyfarmers.seasoning.SeasoningNbt.read(stack)
                .map(dose->dose.units(id->com.bettercontent.betterspicyfarmers.api.SpiceCatalog.sauce(id).unitsPerMb())).orElse(0D);
    }
    public static double metadataSpice(ItemStack stack) {
        return bounded(stack.getTag().getCompound(SERVING).getDouble("spice"),1000)*ediblePortions(stack)+sauceSpice(stack);
    }
    /** Captured base/ingredient Spice plus the actual output seasoning, without recursive provider lookup. */
    public static OptionalDouble capturedDrinkSpice(ItemStack stack) {
        var dose=com.bettercontent.bettercooking.drinks.NativeDrinkProvenance.resolve(stack);
        return dose.isPresent()?OptionalDouble.of(dose.get().spice()+sauceSpice(stack)):OptionalDouble.empty();
    }
    public static OptionalDouble nativeSpice(ItemStack stack) {
        FoodProfile food=profile(stack);if(food==null||food.nativeIntake().spice()==null)return OptionalDouble.empty();
        double fraction=stack.hasTag()&&stack.getTag().getBoolean(SINGLE_PORTION)?1D/food.servings():
                com.bettercontent.betterspicyfarmers.seasoning.ServingContext.intrinsicFraction(stack);
        return OptionalDouble.of(food.nativeIntake().spice()*fraction+sauceSpice(stack));
    }
    private static double nativeSugar(ItemStack stack,FoodProfile food,Map<String,Double> whole) {
        return food!=null&&food.nativeIntake().sugar()!=null?food.nativeIntake().sugar():ConsumableProfiles.servingSugar(stack,whole);
    }
    private static double nativeAlcohol(ItemStack stack,FoodProfile food) {
        return food!=null&&food.nativeIntake().alcohol()!=null?food.nativeIntake().alcohol():ConsumableProfiles.alcohol(stack);
    }
    public static Optional<ServingProfile> resolve(ItemStack stack) {
        if(stack.isEmpty())return Optional.empty();FoodProfile food=profile(stack);if(food==null||!food.resolved())return Optional.empty();
        CompoundTag data=stack.hasTag()?stack.getTag().getCompound(SERVING):new CompoundTag();
        double metadataBasis=food.quantityBasis().equals("per_portion")?1:food.servings();
        Map<String,Double> nutrients=data.contains("nutrients")?scaledVector(readVector(data.getCompound("nutrients")),metadataBasis):ordinary(food.nutrients());
        CuisineQuality quality=stack.hasTag()&&stack.getTag().contains(QUALITY)?CuisineQuality.fromNative(stack.getTag().getInt(QUALITY)):CuisineQuality.STANDARD;
        if(com.bettercontent.bettercooking.drinks.NativeDrinkProvenance.hasReceipt(stack)) {
            var captured=com.bettercontent.bettercooking.drinks.NativeDrinkProvenance.resolve(stack);
            if(captured.isEmpty())return Optional.empty(); // New partial/unpaid receipts are never legacy zero servings.
            var dose=captured.get();
            ServingProfile resolved=ServingProfile.resolve(food,quality,dose.nutrients(),dose.sugar(),dose.alcohol(),dose.spice()+sauceSpice(stack));
            return Optional.of(new ServingProfile(resolved.itemId(),quality,resolved.hunger(),resolved.saturationModifier(),
                    resolved.baseNutrients(),resolved.nutrients(),dose.hydration(),dose.quenching(),
                    dose.sugar(),dose.alcohol(),dose.spice()+sauceSpice(stack)).forPortions(portionCount(stack)));
        }
        double perItem=food.quantityBasis().equals("per_portion")?food.servings():1;
        Map<String,Double> whole=new TreeMap<>();nutrients.forEach((group,value)->whole.put(group,value*perItem));
        double sugar=data.contains("sugar")?data.getDouble("sugar")*metadataBasis:nativeSugar(stack,food,whole)/perItem;
        double alcohol=data.contains("alcohol")?data.getDouble("alcohol")*metadataBasis:nativeAlcohol(stack,food)/perItem;
        double portions=portionCount(stack),actualSpice=SpiceApi.resolveUnits(null,stack);
        ServingProfile base=ServingProfile.resolve(food,quality,nutrients,sugar,alcohol,actualSpice/portions);
        if(data.contains("hydration"))base=new ServingProfile(base.itemId(),quality,base.hunger(),base.saturationModifier(),base.baseNutrients(),base.nutrients(),
                bounded(data.getDouble("hydration"),128)*metadataBasis,bounded(data.getDouble("quenching"),256)*metadataBasis,sugar,alcohol,actualSpice/portions);
        ServingProfile result=base.forPortions(portions);
        if(stack.hasTag()&&stack.getTag().contains(NATIVE_BITE)) {
            CompoundTag nativeBite=stack.getTag().getCompound(NATIVE_BITE);
            if(nativeBite.getInt("max_portions")!=food.servings())throw new IllegalStateException("Native bite/food-policy portion drift: "+food.id());
            if(food.quantityBasis().equals("native_intake")) {
                int nativeHunger=nativeBite.getInt("hunger");double nativeSat=nativeBite.getDouble("saturation_modifier");
                if(!nativeBite.contains("hunger",Tag.TAG_INT)||!nativeBite.contains("saturation_modifier",Tag.TAG_DOUBLE)||nativeHunger< -100||nativeHunger>100||!Double.isFinite(nativeSat)||nativeSat< -100||nativeSat>10)
                    throw new IllegalArgumentException("Invalid actual native bite properties");
                result=new ServingProfile(result.itemId(),result.quality(),nativeHunger,(float)nativeSat,result.baseNutrients(),result.nutrients(),
                        result.hydration(),result.quenching(),result.sugar(),result.alcohol(),result.spice());
            }
        }
        return Optional.of(result);
    }
    public static ItemStack copyServingProvenance(ItemStack original,ItemStack portion,long gameTime) {
        ItemStack result=portion.copy();
        if(original.hasTag()) {
            if(original.getTag().contains(SERVING))result.getOrCreateTag().put(SERVING,original.getTag().getCompound(SERVING).copy());
            if(original.getTag().contains(QUALITY))result.getOrCreateTag().putInt(QUALITY,original.getTag().getInt(QUALITY));
        }
        result.getOrCreateTag().putBoolean(SINGLE_PORTION,true);
        return CookingAgeApi.transfer(List.of(new CookingAgeApi.Input(original,FoodThermalService.Storage.AMBIENT)),result,gameTime);
    }
    private static Map<String,Double> ordinary(Map<String,Double> values) {
        Set<String> groups=Set.of("proteins","grains","fruits","vegetables","dairy");Map<String,Double> result=new TreeMap<>();
        values.forEach((group,value)->{
            if(!groups.contains(group))throw new IllegalArgumentException("Unsupported Diet nutrient: "+group);
            result.put(group,value);
        });return result;
    }
    public static ItemStack finishPreparation(PreparationRecipe recipe,List<ItemStack> paid,ServerLevel level) {
        return finishPreparation(recipe,paid,level,null);
    }
    public static ItemStack finishPreparation(PreparationRecipe recipe,List<ItemStack> paid,net.minecraft.server.level.ServerPlayer player) {
        return finishPreparation(recipe,paid,player.serverLevel(),player);
    }
    private static ItemStack finishPreparation(PreparationRecipe recipe,List<ItemStack> paid,ServerLevel level,net.minecraft.server.level.ServerPlayer player) {
        if(!recipe.acceptsPaid(paid)) throw new IllegalArgumentException("Unpaid or incompatible culinary preparation");
        ItemStack output=recipe.getResultItem(level.registryAccess());
        FoodProfile profile=profile(output); if(profile==null||!profile.resolved()) throw new IllegalStateException("Unresolved culinary output "+output);
        Map<String,Double> base=new TreeMap<>(); double sugar=0,alcohol=0,spice=0,hydration=0,quenching=0;
        CuisineQuality inherited=null;
        List<ItemStack> settled=new ArrayList<>();
        for(ItemStack ingredient:paid) {
            ItemStack input=ingredient.copy(); FoodThermalService.INSTANCE.settle(input,FoodThermalService.Storage.AMBIENT,level.getGameTime()); settled.add(input);
            CompoundTag data=input.hasTag()?input.getTag().getCompound(SERVING):new CompoundTag();
            FoodProfile raw=profile(input);
            Map<String,Double> vector;
            if(data.contains("nutrients")) vector=readVector(data.getCompound("nutrients"));
            else if(raw!=null&&!raw.nutrients().isEmpty()) vector=ordinary(raw.nutrients());
            else if(player!=null&&input.isEdible()) {
                var potential=ConsumableProfiles.dietGains(player,input)
                        .orElseThrow(()->new IllegalStateException("Diet ingredient gains unavailable: "+input));
                if(potential.containsKey("fats")) throw new IllegalStateException("Removed Diet Fats group in ingredient: "+input);
                Map<String,Double> supported=new TreeMap<>();
                for(String group:List.of("proteins","grains","fruits","vegetables","dairy"))
                    if(potential.containsKey(group))supported.put(group,potential.get(group));
                vector=Map.copyOf(supported);
            } else vector=raw==null?Map.of():ordinary(raw.nutrients());
            double parts=portionCount(input),metadataParts=ediblePortions(input),vectorParts=data.contains("nutrients")?metadataParts:parts;
            vector.forEach((group,value)->base.merge(group,value*vectorParts*input.getCount()/recipe.servings(),Double::sum));
            Map<String,Double> wholeVector=scaledVector(vector,vectorParts);
            sugar+=(data.contains("sugar")?data.getDouble("sugar")*metadataParts:nativeSugar(input,raw,wholeVector))*input.getCount()/recipe.servings();
            alcohol+=(data.contains("alcohol")?data.getDouble("alcohol")*metadataParts:nativeAlcohol(input,raw))*input.getCount()/recipe.servings();
            spice+=SpiceApi.resolveUnits(null,input)*input.getCount()/recipe.servings();
            hydration+=(data.contains("hydration")?data.getDouble("hydration")*metadataParts:(raw==null?0:raw.hydration())*parts)*input.getCount()/recipe.servings();
            quenching+=(data.contains("quenching")?data.getDouble("quenching")*metadataParts:(raw==null?0:raw.quenching())*parts)*input.getCount()/recipe.servings();
            if(input.hasTag()&&input.getTag().contains(QUALITY)) inherited=inherited==null?CuisineQuality.fromNative(input.getTag().getInt(QUALITY)):
                    CuisineQuality.worst(inherited,CuisineQuality.fromNative(input.getTag().getInt(QUALITY)));
        }
        CuisineQuality quality=inherited==null?CuisineQuality.STANDARD:inherited;
        if(recipe.flexible()) {
            var evaluated=recipe.evaluateQuality(level,settled);
            quality=CuisineQuality.fromNative(evaluated.getId()); if(inherited!=null) quality=CuisineQuality.worst(quality,inherited);
        }
        CompoundTag serving=new CompoundTag(); serving.put("nutrients",writeVector(base));serving.putDouble("sugar",sugar);serving.putDouble("alcohol",alcohol);serving.putDouble("spice",spice);
        // A direct authored composite hydration profile is an override, not an additional credit.
        serving.putDouble("hydration",profile.hydration());serving.putDouble("quenching",profile.quenching());
        if(!output.isEdible())serving.put(AGE_INPUTS,new ListTag());
        output.getOrCreateTag().put(SERVING,serving);
        if(profile.protectedMeal()||recipe.flexible()||inherited!=null)output.getOrCreateTag().putInt(QUALITY,quality.nativeId());
        CookingAgeApi.transfer(settled.stream().map(stack->new CookingAgeApi.Input(stack,FoodThermalService.Storage.AMBIENT)).toList(),output,level.getGameTime());
        if(recipe.finishedOnPreparationSurface()) return output;
        ItemStack batch=new ItemStack(CookingItems.PREPARED_BATCH.get());CompoundTag prep=new CompoundTag();
        prep.putBoolean("committed",true);prep.putString("route",recipe.getId().toString());prep.putString("finish",recipe.finish());
        prep.putString("definition_fingerprint",recipe.fingerprint());
        prep.put("output",output.save(new CompoundTag()));prep.putInt("servings",recipe.servings());
        ListTag ingredients=new ListTag();settled.forEach(stack->ingredients.add(stack.save(new CompoundTag())));prep.put("ingredients",ingredients);
        batch.getOrCreateTag().put(PREP,prep);return batch;
    }
    /** Whole unscaled intake, never a second quality-adjusted copy. */
    private record InputTotals(Map<String,Double> nutrients,double sugar,double alcohol,double spice,double hydration,double quenching,boolean known) {
        private static InputTotals of(ItemStack input) {
            if(input.isEmpty())return new InputTotals(Map.of(),0,0,0,0,0,false);
            if(input.hasTag()&&input.getTag().contains(PREP,Tag.TAG_COMPOUND)) {
                CompoundTag prep=input.getTag().getCompound(PREP);String route=prep.getString("route");
                if(!ResourceLocation.isValidResourceLocation(route)||!isPreparedFor(input,new ResourceLocation(route)))throw new IllegalArgumentException("Invalid committed preparation");
                int portions=prep.getInt("servings");if(portions<1||portions>4096)throw new IllegalArgumentException("Invalid committed preparation yield");
                ItemStack output=preparedOutput(input);CompoundTag data=output.hasTag()?output.getTag().getCompound(SERVING):new CompoundTag();
                if(!data.contains("nutrients",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Preparation lacks paid intake provenance");
                double units=(double)portions*input.getCount();
                return new InputTotals(scaledVector(readVector(data.getCompound("nutrients")),units),
                        bounded(data.getDouble("sugar"),1000)*units,bounded(data.getDouble("alcohol"),1000)*units,
                        bounded(data.getDouble("spice"),1000)*units,bounded(data.getDouble("hydration"),128)*units,
                        bounded(data.getDouble("quenching"),256)*units,true);
            }
            Optional<ServingProfile> serving=resolve(input);
            if(serving.isEmpty())return new InputTotals(Map.of(),0,0,0,0,0,false);
            ServingProfile value=serving.get();double units=input.getCount();
            return new InputTotals(scaledVector(value.baseNutrients(),units),value.sugar()*units,value.alcohol()*units,
                    value.spice()*units,value.hydration()*units,value.quenching()*units,true);
        }
        private CompoundTag perPortion(double portions) {
            if(!Double.isFinite(portions)||portions<=0)throw new IllegalArgumentException("Invalid actual edible yield");
            CompoundTag data=new CompoundTag();data.put("nutrients",writeVector(scaledVector(nutrients,1D/portions)));
            data.putDouble("sugar",bounded(sugar/portions,1000));data.putDouble("alcohol",bounded(alcohol/portions,1000));
            data.putDouble("spice",bounded(spice/portions,1000));data.putDouble("hydration",bounded(hydration/portions,128));
            data.putDouble("quenching",bounded(quenching/portions,256));return data;
        }
    }
    /** Preserve exact native failure outcomes without laundering paid ingredients or food age. */
    public static ItemStack carryFailedFromPrepared(List<ItemStack> actualInputs,ItemStack nativeFailure,int actualOutputItems,long gameTime) {
        if(actualOutputItems<1||actualOutputItems>4096||nativeFailure.isEmpty())throw new IllegalArgumentException("Invalid native failure yield");
        if(actualInputs.stream().noneMatch(stack->stack.hasTag()&&stack.getTag().contains(PREP)))return nativeFailure;
        FoodProfile failure=profile(nativeFailure);
        if(failure==null||!failure.resolved())throw new IllegalStateException("Unresolved native failure serving: "+nativeFailure);
        Map<String,Double> nutrients=new TreeMap<>();double sugar=0,alcohol=0,spice=0,hydration=0,quenching=0;
        for(ItemStack input:actualInputs) {
            InputTotals value=InputTotals.of(input);value.nutrients().forEach((key,amount)->nutrients.merge(key,amount,Double::sum));
            sugar+=value.sugar();alcohol+=value.alcohol();spice+=value.spice();hydration+=value.hydration();quenching+=value.quenching();
        }
        ItemStack result=nativeFailure.copy();CompoundTag data=new InputTotals(nutrients,sugar,alcohol,spice,hydration,quenching,true)
                .perPortion((double)actualOutputItems*failure.servings());
        if(!result.isEdible())data.put(AGE_INPUTS,new ListTag());
        result.getOrCreateTag().put(SERVING,data);result.getOrCreateTag().putInt(QUALITY,CuisineQuality.POOR.nativeId());
        return CookingAgeApi.transfer(actualInputs.stream().map(stack->new CookingAgeApi.Input(stack,FoodThermalService.Storage.AMBIENT)).toList(),result,gameTime);
    }
    /** Reviewed cuts have at most one edible result entry. No inverse-chance inflation or food credit on nonfood byproducts. */
    public static List<ItemStack> carryCuttingProvenance(ItemStack original,List<ItemStack> realized,long gameTime) {
        if(original.isEmpty()||original.getCount()!=1)throw new IllegalArgumentException("A cut must pay exactly one actual input");
        List<ItemStack> result=realized.stream().map(ItemStack::copy).toList();
        List<ItemStack> edible=result.stream().filter(stack->!stack.isEmpty()&&stack.isEdible()).toList();
        if(edible.size()>1)throw new IllegalArgumentException("Multiple edible cutting results require an explicit allocation policy");
        InputTotals totals=InputTotals.of(original);
        for(ItemStack output:result) {
            if(output.isEmpty())continue;
            if(output.isEdible()) {
                FoodProfile target=profile(output);
                if(target==null||!target.resolved())throw new IllegalStateException("Unresolved cutting result: "+output);
                double portions=(double)output.getCount()*target.servings();
                FoodProfile source=profile(original);
                if(source!=null&&source.protectedMeal()&&Double.compare(portions,ediblePortions(original))!=0)
                    throw new IllegalArgumentException("Already-paid meal cutting changes actual edible portion count");
                if(totals.known())output.getOrCreateTag().put(SERVING,totals.perPortion(portions));
            }
            if(original.hasTag()&&original.getTag().contains(QUALITY,Tag.TAG_INT))
                output.getOrCreateTag().putInt(QUALITY,original.getTag().getInt(QUALITY));
            CookingAgeApi.transfer(List.of(new CookingAgeApi.Input(original,FoodThermalService.Storage.AMBIENT)),output,gameTime);
        }
        return result;
    }
    public static List<ItemStack> remainders(List<ItemStack> paid) {
        List<ItemStack> result=new ArrayList<>();
        for(ItemStack input:paid) if(input.hasCraftingRemainingItem()) {
            ItemStack remainder=input.getCraftingRemainingItem();
            if(!remainder.isEmpty()) { remainder=remainder.copy();remainder.setCount(Math.multiplyExact(remainder.getCount(),input.getCount()));result.add(remainder); }
        }
        return List.copyOf(result);
    }
    public static boolean isPreparedFor(ItemStack stack,ResourceLocation route) {
        if(stack.isEmpty()||!stack.is(CookingItems.PREPARED_BATCH.get())||!stack.hasTag()||stack.getTag().contains("better_cooking:kitchen_resume")) return false;
        CompoundTag prep=stack.getTag().getCompound(PREP);
        if(!prep.getBoolean("committed")||!prep.getString("route").equals(route.toString())||!prep.contains("ingredients",Tag.TAG_LIST)) return false;
        ItemStack output=ItemStack.of(prep.getCompound("output"));
        return !output.isEmpty()&&output.getCount()<=64&&PolicyRepository.current().routes().containsKey(route.toString());
    }
    /** Exact current executable definition and actual paid units; never upgrades obsolete paid work. */
    public static boolean isPreparedForCurrentRecipe(ItemStack stack,PreparationRecipe recipe) {
        if(recipe==null||stack.getCount()!=1||!isPreparedFor(stack,recipe.getId()))return false;
        CompoundTag prep=stack.getTag().getCompound(PREP);
        if(!prep.contains("definition_fingerprint",Tag.TAG_STRING)
                ||!recipe.fingerprint().equals(prep.getString("definition_fingerprint"))
                ||!recipe.finish().equals(prep.getString("finish"))
                ||!prep.contains("servings",Tag.TAG_INT)||prep.getInt("servings")!=recipe.servings())return false;
        ListTag encoded=prep.getList("ingredients",Tag.TAG_COMPOUND);
        List<ItemStack> paid=preparedInputs(stack);
        if(encoded.isEmpty()||encoded.size()!=paid.size()||!recipe.acceptsPaid(paid))return false;
        ItemStack expected=recipe.getResultItem(null),actual=preparedOutput(stack);
        return actual.getItem()==expected.getItem()&&actual.getCount()==expected.getCount();
    }
    public static ItemStack preparedOutput(ItemStack stack) {
        if(!stack.hasTag()||!stack.getTag().contains(PREP)) return ItemStack.EMPTY;
        return ItemStack.of(stack.getTag().getCompound(PREP).getCompound("output"));
    }
    public static boolean ageCarrier(ItemStack stack) {
        if(stack.isEmpty()||stack.isEdible()||!stack.hasTag())return false;
        return stack.is(CookingItems.PREPARED_BATCH.get())&&stack.getTag().contains(PREP)||stack.getTag().getCompound(SERVING).contains(AGE_INPUTS,Tag.TAG_LIST);
    }
    public static List<ItemStack> preparedInputs(ItemStack stack) {
        if(!stack.hasTag()) return List.of();List<ItemStack> result=new ArrayList<>();
        ListTag children=stack.getTag().contains(PREP)?stack.getTag().getCompound(PREP).getList("ingredients",Tag.TAG_COMPOUND):stack.getTag().getCompound(SERVING).getList(AGE_INPUTS,Tag.TAG_COMPOUND);
        for(Tag raw:children) {
            ItemStack input=ItemStack.of((CompoundTag)raw);if(!input.isEmpty()) result.add(input);
        }
        return result;
    }
    public static void writePreparedInputs(ItemStack stack,List<ItemStack> inputs) {
        if(!stack.hasTag())return;
        ListTag list=new ListTag();inputs.forEach(input->list.add(input.save(new CompoundTag())));
        if(stack.getTag().contains(PREP))stack.getTag().getCompound(PREP).put("ingredients",list);
        else if(stack.getTag().contains(SERVING))stack.getTag().getCompound(SERVING).put(AGE_INPUTS,list);
    }
    public static ItemStack finishFromPrepared(ItemStack input,ItemStack expected) {
        if(!input.hasTag()) return ItemStack.EMPTY;
        CompoundTag prep=input.getTag().getCompound(PREP);
        ResourceLocation route=ResourceLocation.tryParse(prep.getString("route"));
        if(route==null||!isPreparedFor(input,route)) return ItemStack.EMPTY;
        ItemStack prepared=preparedOutput(input);
        if(prepared.getItem()!=expected.getItem()||prepared.getCount()!=expected.getCount()) return ItemStack.EMPTY;
        ItemStack result=prepared.copy();var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null) CookingAgeApi.transfer(preparedInputs(input).stream().map(stack->new CookingAgeApi.Input(stack,FoodThermalService.Storage.AMBIENT)).toList(),result,server.overworld().getGameTime());
        return result;
    }
    public static ItemStack carryFromContainer(net.minecraft.world.Container inputs,ItemStack output) {
        for(int slot=0;slot<inputs.getContainerSize();slot++) if(inputs.getItem(slot).is(CookingItems.PREPARED_BATCH.get())) return finishFromPrepared(inputs.getItem(slot),output);
        return output;
    }
    private static Map<String,Double> readVector(CompoundTag tag) { Map<String,Double> values=new TreeMap<>();for(String key:tag.getAllKeys()){
        if(!Set.of("proteins","grains","fruits","vegetables","dairy").contains(key))
            throw new IllegalArgumentException("Unsupported Diet serving nutrient: "+key);
        values.put(key,bounded(tag.getDouble(key),100));
    }return values; }
    private static CompoundTag writeVector(Map<String,Double> values) { CompoundTag tag=new CompoundTag();values.forEach(tag::putDouble);return tag; }
    private static double bounded(double value,double max) { if(!Double.isFinite(value)||value<0||value>max)throw new IllegalArgumentException("Invalid culinary serving data");return value; }
}
