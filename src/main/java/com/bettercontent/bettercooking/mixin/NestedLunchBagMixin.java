package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingMod;
import com.github.ysbbbbbb.kaleidoscopecookery.item.TransmutationLunchBagItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The native bag calls finishUsingItem directly; bridge each paid inner serving, never the outer bag. */
@Mixin(value=TransmutationLunchBagItem.class,remap=false)
public abstract class NestedLunchBagMixin {
    @Redirect(method="consumeOne",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",remap=true))
    private static ItemStack cooking$inner(ItemStack stack,Level level,LivingEntity entity) {
        ItemStack before=stack.copy();ItemStack returned=stack.finishUsingItem(level,entity);
        return level.isClientSide||CookingMod.discoveryOnly()?returned:ForgeEventFactory.onItemUseFinish(entity,before,0,returned);
    }
}
