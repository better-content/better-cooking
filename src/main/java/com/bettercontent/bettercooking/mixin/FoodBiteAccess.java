package com.bettercontent.bettercooking.mixin;

import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock;
import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=FoodBiteBlock.class,remap=false)
public interface FoodBiteAccess {
    @Accessor("foodProperties") FoodProperties cooking$nativeBiteProperties();
}
