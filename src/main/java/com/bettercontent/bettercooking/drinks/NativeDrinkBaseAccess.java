package com.bettercontent.bettercooking.drinks;

import net.minecraft.nbt.CompoundTag;

/** Implemented only by strict native producer capture mixins. Accessors always copy. */
public interface NativeDrinkBaseAccess {
    CompoundTag cooking$drinkBase();
    /** Committed insertion snapshot at the actual native slot, or empty for legacy/direct automation. */
    default CompoundTag cooking$drinkIngredient(int slot){return new CompoundTag();}
}
