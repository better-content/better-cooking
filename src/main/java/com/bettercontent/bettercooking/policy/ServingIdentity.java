package com.bettercontent.bettercooking.policy;

import com.bettercontent.bettercooking.CookingRuntime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Stable actual item/variant/quality key for future funded markets. No player/owner identity is introduced. */
public record ServingIdentity(String itemId,String variantSha256,CuisineQuality quality) {
    public static ServingIdentity of(ItemStack stack) {
        if(stack.isEmpty())throw new IllegalArgumentException("Empty goods have no serving identity");
        CompoundTag semantic=stack.hasTag()?stack.getTag().copy():new CompoundTag();
        CuisineQuality quality=semantic.contains(CookingRuntime.QUALITY)?CuisineQuality.fromNative(semantic.getInt(CookingRuntime.QUALITY)):CuisineQuality.STANDARD;
        semantic.remove(CookingRuntime.QUALITY);semantic.remove(CookingRuntime.SINGLE_PORTION);semantic.remove(CookingRuntime.NATIVE_BITE);
        if(semantic.contains(CookingRuntime.SERVING))semantic.getCompound(CookingRuntime.SERVING).remove(CookingRuntime.AGE_INPUTS);
        // These are authoritative mutable age clocks, not a formulation or item variant.
        semantic.remove("better_industrial_heat_food");semantic.remove("better_industrial_heat_food_active");
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(semantic).getBytes(StandardCharsets.UTF_8)));
            return new ServingIdentity(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(),hash,quality);
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static String canonical(Tag value) {
        if(value instanceof CompoundTag compound) {
            StringBuilder out=new StringBuilder("{");
            for(String key:new TreeSet<>(compound.getAllKeys()))out.append(StringTag.valueOf(key)).append(':').append(canonical(compound.get(key))).append(';');
            return out.append('}').toString();
        }
        if(value instanceof ListTag list) {
            StringBuilder out=new StringBuilder("[");for(Tag child:list)out.append(canonical(child)).append(';');return out.append(']').toString();
        }
        return value.getId()+":"+value;
    }
    public String key(){return itemId+"#"+variantSha256+"#"+quality.name().toLowerCase(java.util.Locale.ROOT);}
}
