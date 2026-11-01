package com.bettercontent.bettercooking.drinks.placed;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Full supported stack serialization, defensively detached at both boundaries. Never holds a mutable live stack. */
public final class EncodedCup {
    private final CompoundTag saved;
    public EncodedCup(ItemStack actual){
        if(actual.isEmpty()||actual.getCount()!=1||!PlacedDrinks.supported(actual))throw new IllegalStateException("Expected one actual native drink original");
        if(actual.hasTag())CupCodec.bounded(actual.getTag());
        saved=actual.save(new CompoundTag());CupCodec.bounded(saved);
        if(saved.getAsString().length()>131072)throw new IllegalStateException("Native cup original exceeds bounded persistence size");
    }
    public ItemStack stack(){return ItemStack.of(saved.copy());}
    public CompoundTag save(){return saved.copy();}
    /** Detect missing/changed receipt/quality/age fields even when the remaining stack still decodes. Not an authentication signature. */
    public static String fingerprint(CompoundTag value){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
