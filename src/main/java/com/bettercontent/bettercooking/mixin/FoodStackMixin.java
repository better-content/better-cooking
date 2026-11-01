package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CulinaryFoodProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Implements the pinned Forge inherited default seam on the concrete stack. */
@Mixin(ItemStack.class)
public abstract class FoodStackMixin implements IForgeItemStack {
    @Shadow public abstract Item getItem();
    @Override public FoodProperties getFoodProperties(LivingEntity entity) {
        ItemStack stack=(ItemStack)(Object)this;
        return CulinaryFoodProperties.apply(stack,getItem().getFoodProperties(stack,entity));
    }
}
