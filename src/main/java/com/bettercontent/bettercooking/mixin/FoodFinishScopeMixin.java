package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CulinaryEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemStack.class)
public abstract class FoodFinishScopeMixin {
    @Redirect(method="finishUsingItem",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/Item;finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cooking$scope(Item item,ItemStack stack,Level level,LivingEntity entity){
        return CulinaryEffects.call(stack,()->item.finishUsingItem(stack,level,entity));
    }
}
