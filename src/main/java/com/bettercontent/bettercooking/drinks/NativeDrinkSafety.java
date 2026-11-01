package com.bettercontent.bettercooking.drinks;

import com.bettercontent.betterdrinkingwater.WaterQuality;
import dev.ghen.thirst.content.purity.WaterPurity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Root-approved input restriction, NOT purification, intake, a safety effect or an actor requirement. */
public final class NativeDrinkSafety {
    private NativeDrinkSafety() {}

    /** Query the ACTUAL water source; getPurity may initialize NBT, so give it a private copy only. */
    public static boolean allowsFluidItem(Fluid actualFluid,ItemStack actualSource){
        if(actualFluid==null)return false;
        if(!actualFluid.isSame(Fluids.WATER))return true;
        return actualSource!=null&&!actualSource.isEmpty()
                &&WaterQuality.normalize(WaterPurity.getPurity(actualSource.copy()))==3;
    }
    /** Native stockpot also supports non-fluid soup bases; do not pretend those or milk are water. */
    public static boolean allowsSoupItem(ItemStack actualSource){
        if(actualSource.isEmpty())return true;
        if(actualSource.is(Items.WATER_BUCKET))return allowsFluidItem(Fluids.WATER,actualSource);
        return actualSource.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(handler->{
            if(handler.getTanks()<1)return true;
            return allowsFluidItem(handler.getFluidInTank(0).getFluid(),actualSource);
        }).orElse(true);
    }
    /** No typed source position/purity reaches this callback. Do NOT query purity at the receiver. */
    public static boolean allowsDripstone(Fluid actualFluid){return actualFluid!=null&&!actualFluid.isSame(Fluids.WATER);}

    public static void explainRejection(LivingEntity actualActor){
        if(actualActor instanceof ServerPlayer player)
            player.displayClientMessage(Component.literal("This kitchen requires purified water. Purify the actual water source before filling."),true);
    }
}
