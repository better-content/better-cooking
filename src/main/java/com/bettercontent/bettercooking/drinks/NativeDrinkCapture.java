package com.bettercontent.bettercooking.drinks;

import com.bettercontent.betterdrinkingwater.WaterSources;
import com.bettercontent.betterdrinkingwater.api.BeverageEvents;
import com.bettercontent.betterspicyfarmers.api.SpiceApi;
import com.bettercontent.betterspicyfarmers.inference.RecipeInference;
import com.bettercontent.bettersurvivalphysiology.metabolism.ConsumableProfiles;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import java.io.*;
import java.util.*;

/** Pure snapshots of actual inputs. Never calls Check/Sip, reserve debit or a tracker mutation. */
public final class NativeDrinkCapture {
    public static final String BASE_KEY="better_cooking:native_drink_base";
    private static final Set<String> ORDINARY=Set.of("proteins","grains","fruits","vegetables","dairy");
    private NativeDrinkCapture() {}

    public static CompoundTag base(LivingEntity actor,ItemStack actual,String fluid) {
        CompoundTag receipt=new CompoundTag();receipt.putInt("v",1);receipt.putString("fluid",fluid);receipt.putInt("millibuckets",1000);
        receipt.putString("kind","item");receipt.put("source",bounded(snapshotItem(actual).save(new CompoundTag())));
        if(NativeDrinkIngredients.connected(actor))receipt.putUUID("actor",actor.getUUID()); // audit only, never finish authority
        // Source-proven quantities ONLY: generic tank whole-container profiles have no public mB basis.
        boolean water=actual.is(Items.WATER_BUCKET)&&fluid.equals("minecraft:water");
        boolean milk=actual.is(Items.MILK_BUCKET); // actual exact item, not a fluid-name heuristic
        if(!water&&!milk) {receipt.putString("gap","unsupported actual base/container volume basis");return receipt;}
        var hydration=WaterSources.nativeHydration(actual);
        if(hydration.isEmpty()) {receipt.putString("gap","missing native Water owner");return receipt;}
        receipt.putDouble("hydration",hydration.get().hydration());receipt.putDouble("quenching",hydration.get().quenching());
        CompoundTag captured=NativeDrinkIngredients.snapshot(actor,actual);
        if(captured.contains("intake",Tag.TAG_COMPOUND)){
            receipt.put("intake",captured.getCompound("intake").copy());receipt.putString("intake_basis",captured.getString("basis"));
            if(captured.contains("default_binding",Tag.TAG_COMPOUND))receipt.put("default_binding",captured.getCompound("default_binding").copy());
        }
        else receipt.putString("gap",captured.getString("gap"));
        return bounded(receipt);
    }

    /** Actual native dripstone fill, not an invented bucket or a paid ItemStack. */
    public static CompoundTag fluid(net.minecraft.world.level.material.Fluid actual){
        var id=net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(actual);
        if(id==null)throw new IllegalArgumentException("Unregistered actual fluid");
        CompoundTag receipt=new CompoundTag();receipt.putInt("v",1);receipt.putString("kind","fluid");
        receipt.putString("fluid",id.toString());receipt.putInt("millibuckets",1000);receipt.putString("basis","native_dripstone_1000mb");
        if(actual==net.minecraft.world.level.material.Fluids.WATER){
            receipt.putDouble("hydration",com.bettercontent.betterdrinkingwater.DrinkHydration.WATER.hydration(1000));
            receipt.putDouble("quenching",com.bettercontent.betterdrinkingwater.DrinkHydration.WATER.quenching(1000));
        }
        receipt.putString("gap","Actual non-item fluid has no observed item Diet/Spice intake; no proxy bucket fabricated");
        return receipt;
    }
    public static CompoundTag intake(ServerPlayer player,ItemStack actual,boolean explicitMilk) {
        if(!NativeDrinkIngredients.connected(player))throw new IllegalArgumentException("Real connected player required");
        CompoundTag result=new CompoundTag(), nutrients=new CompoundTag();
        double sugar,alcohol;
        if(explicitMilk) {
            if(!actual.is(Items.MILK_BUCKET))throw new IllegalArgumentException("Not actual vanilla milk");
            CompoundTag snapshot=new CompoundTag();
            MinecraftForge.EVENT_BUS.post(new BeverageEvents.Profile(player,actual,snapshot));
            if(!snapshot.contains("diet",Tag.TAG_COMPOUND)||!snapshot.contains("sugar",Tag.TAG_DOUBLE)||!snapshot.contains("alcohol",Tag.TAG_DOUBLE))
                throw new IllegalStateException("Missing typed milk profile listener");
            CompoundTag diet=snapshot.getCompound("diet");
            // The exact milk branch supplies physiology's .06 dairy. Do not generalize
            // this event to unknown ingredients: its generic branch has a legacy zero fallback.
            if(!diet.contains("dairy",Tag.TAG_ANY_NUMERIC)||diet.contains("fats",Tag.TAG_ANY_NUMERIC))
                throw new IllegalStateException("Missing explicit milk nutrition");
            for(String key:ORDINARY)if(diet.contains(key,Tag.TAG_ANY_NUMERIC))nutrients.putDouble(key,checked(diet.getDouble(key)));
            sugar=checked(snapshot.getDouble("sugar"));alcohol=checked(snapshot.getDouble("alcohol"));
        } else {
            var gains=ConsumableProfiles.dietGains(player,actual).orElseThrow(()->new IllegalStateException("Diet potential gains unavailable"));
            for(var gain:gains.entrySet())if(ORDINARY.contains(gain.getKey()))nutrients.putDouble(gain.getKey(),checked(gain.getValue()));
            sugar=checked(ConsumableProfiles.servingSugar(actual,gains));alcohol=checked(ConsumableProfiles.alcohol(actual));
        }
        var inference=RecipeInference.status();
        if(inference.derivedDisabled()||inference.recipeCount()==0)throw new IllegalStateException("Spice inference unavailable; zero is not a resolved dose");
        result.put("nutrients",nutrients);result.putDouble("sugar",sugar);result.putDouble("alcohol",alcohol);
        result.putDouble("spice",checked(SpiceApi.resolveUnits(player,actual)));return result;
    }
    public static ItemStack snapshotItem(ItemStack actual) {
        if(actual.hasTag())bounded(actual.getTag());
        return actual.copyWithCount(1);
    }
    public static double checked(double value) {
        if(!Double.isFinite(value)||value<0||value>10000)throw new IllegalArgumentException("Invalid native intake value");return value;
    }
    /** Bound BEFORE copy/serialization: protects saves and nested original-input carriers. */
    public static CompoundTag bounded(CompoundTag tag) {
        record Node(Tag value,int depth) {}
        ArrayDeque<Node> pending=new ArrayDeque<>();pending.push(new Node(tag,0));int nodes=0;
        while(!pending.isEmpty()) {
            Node n=pending.pop();if(++nodes>4096||n.depth()>24)throw new IllegalArgumentException("Native drink metadata structural bound");
            if(n.value() instanceof CompoundTag c)for(String key:c.getAllKeys())pending.push(new Node(c.get(key),n.depth()+1));
            else if(n.value() instanceof ListTag l)for(Tag child:l)pending.push(new Node(child,n.depth()+1));
        }
        try {
            NbtIo.write(tag,new DataOutputStream(new OutputStream(){int count;
                @Override public void write(int b)throws IOException{add(1);}
                @Override public void write(byte[] b,int off,int len)throws IOException{add(len);}
                void add(int len)throws IOException{if((long)count+len>65536)throw new IOException("Native drink metadata byte bound");count+=len;}
            }));
        } catch(IOException failure){throw new IllegalArgumentException("Oversized native drink metadata",failure);}
        return tag.copy();
    }
}
