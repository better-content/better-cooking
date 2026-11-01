package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.drinks.*;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Records only successful native soup-base payment. No synthetic milk item, producer or refund. */
@Mixin(value=StockpotBlockEntity.class,remap=false)
public abstract class NativeStockpotBaseCaptureMixin implements NativeDrinkBaseAccess {
    @Shadow private ResourceLocation soupBaseId;
    @Shadow private int status;
    @Unique private CompoundTag cooking$base=new CompoundTag();
    @Unique private CompoundTag cooking$pending=new CompoundTag();
    @Unique private CompoundTag cooking$ingredients=new CompoundTag();
    @Unique private CompoundTag cooking$pendingIngredient=new CompoundTag();
    @Unique private int cooking$pendingSlot=-1;
    @Override public CompoundTag cooking$drinkBase(){return NativeDrinkCapture.bounded(cooking$base);}
    @Override public CompoundTag cooking$drinkIngredient(int slot){
        if(slot<0||slot>=9)throw new IllegalArgumentException("Native stockpot slot out of range");
        return NativeDrinkCapture.bounded(cooking$ingredients.getCompound(Integer.toString(slot)));
    }
    @Inject(method="addSoupBase(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),cancellable=true,require=1)
    private void cooking$beforeBase(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        cooking$pending=new CompoundTag();if(actual.isEmpty())return;
        // No native soup-base decrement/container action has happened at HEAD, on either side.
        if(!NativeDrinkSafety.allowsSoupItem(actual)){
            NativeDrinkSafety.explainRejection(actor);cir.setReturnValue(false);return;
        }
        if(level.isClientSide())return;
        // FluidSoupBase.isSoupBase uses the registered exact bucket item. Other source kinds
        // remain explicit unresolved snapshots, not native milk inferred from a soup-base name.
        String fluid=actual.is(Items.MILK_BUCKET)?"minecraft:milk":actual.is(Items.WATER_BUCKET)?"minecraft:water":"unresolved:actual_soup_base";
        cooking$pending=NativeDrinkCapture.base(actor,actual,fluid);
    }
    @Inject(method="addSoupBase(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"),require=1)
    private void cooking$afterBase(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()) {
            cooking$base=cooking$pending.copy();cooking$base.putString("owner_base",soupBaseId.toString());
            ((StockpotBlockEntity)(Object)this).setChanged();
        }
        cooking$pending=new CompoundTag();
    }
    @Inject(method="removeSoupBase(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"),require=1)
    private void cooking$removedBase(Level level,LivingEntity actor,ItemStack vessel,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()){cooking$base=new CompoundTag();cooking$ingredients=new CompoundTag();((StockpotBlockEntity)(Object)this).setChanged();}
    }
    @Inject(method="takeOutProduct(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"),require=1)
    private void cooking$reset(Level level,LivingEntity actor,ItemStack vessel,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()&&status==IStockpot.PUT_SOUP_BASE){cooking$base=new CompoundTag();cooking$ingredients=new CompoundTag();((StockpotBlockEntity)(Object)this).setChanged();}
    }
    @Inject(method="setStatus(I)V",at=@At("RETURN"),require=1)
    private void cooking$explicitReset(int next,CallbackInfo ci){
        if(next==IStockpot.PUT_SOUP_BASE){cooking$base=new CompoundTag();cooking$pending=new CompoundTag();cooking$ingredients=new CompoundTag();cooking$pendingIngredient=new CompoundTag();cooking$pendingSlot=-1;((StockpotBlockEntity)(Object)this).setChanged();}
    }
    @Inject(method="addIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),require=1)
    private void cooking$beforeIngredient(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        cooking$pendingIngredient=new CompoundTag();cooking$pendingSlot=-1;
        if(level.isClientSide()||actual.isEmpty()||!soupBaseId.equals(com.github.ysbbbbbb.kaleidoscopecookery.init.ModSoupBases.MILK)
                ||actual.is(com.bettercontent.bettercooking.CookingItems.PREPARED_BATCH.get()))return;
        var inputs=((StockpotBlockEntity)(Object)this).getInputs();
        for(int slot=0;slot<inputs.size();slot++)if(inputs.get(slot).isEmpty()){
            cooking$pendingSlot=slot;cooking$pendingIngredient=NativeDrinkIngredients.snapshot(actor,actual);break;
        }
    }
    @Inject(method="addIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"),require=1)
    private void cooking$afterIngredient(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()&&cooking$pendingSlot>=0){
            if(cooking$pendingIngredient.isEmpty())throw new IllegalStateException("Successful stockpot insertion without actual input snapshot");
            cooking$ingredients.put(Integer.toString(cooking$pendingSlot),cooking$pendingIngredient.copy());
            cooking$ingredients=NativeDrinkIngredients.restoreCaptures(cooking$ingredients,((StockpotBlockEntity)(Object)this).getInputs());
            ((StockpotBlockEntity)(Object)this).setChanged();
        }
        cooking$pendingIngredient=new CompoundTag();cooking$pendingSlot=-1;
    }
    @Inject(method="removeIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Z",at=@At("RETURN"),require=1)
    private void cooking$takeIngredient(Level level,LivingEntity actor,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()){
            cooking$ingredients=NativeDrinkIngredients.restoreCaptures(cooking$ingredients,((StockpotBlockEntity)(Object)this).getInputs());
            ((StockpotBlockEntity)(Object)this).setChanged();
        }
    }
    @Inject(method="saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"),require=1,remap=true)
    private void cooking$save(CompoundTag tag,CallbackInfo ci){
        if(!cooking$base.isEmpty())tag.put(NativeDrinkCapture.BASE_KEY,NativeDrinkCapture.bounded(cooking$base));else tag.remove(NativeDrinkCapture.BASE_KEY);
        cooking$ingredients=NativeDrinkIngredients.restoreCaptures(cooking$ingredients,((StockpotBlockEntity)(Object)this).getInputs());
        if(cooking$ingredients.isEmpty())tag.remove(NativeDrinkIngredients.KEY);else tag.put(NativeDrinkIngredients.KEY,cooking$ingredients.copy());
    }
    @Inject(method="load(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"),require=1,remap=true)
    private void cooking$load(CompoundTag tag,CallbackInfo ci){
        cooking$pending=new CompoundTag();cooking$pendingIngredient=new CompoundTag();cooking$pendingSlot=-1;
        if(tag.contains(NativeDrinkIngredients.KEY)&&!tag.contains(NativeDrinkIngredients.KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Wrong stockpot ingredient capture type");
        cooking$ingredients=NativeDrinkIngredients.restoreCaptures(tag.getCompound(NativeDrinkIngredients.KEY),((StockpotBlockEntity)(Object)this).getInputs());
        if(tag.contains(NativeDrinkCapture.BASE_KEY)&&!tag.contains(NativeDrinkCapture.BASE_KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Wrong stockpot base capture type");
        cooking$base=tag.contains(NativeDrinkCapture.BASE_KEY,Tag.TAG_COMPOUND)
                ?NativeDrinkCapture.bounded(tag.getCompound(NativeDrinkCapture.BASE_KEY)):new CompoundTag();
        if(!cooking$base.isEmpty()&&(!cooking$base.getString("owner_base").equals(soupBaseId.toString())||status==IStockpot.PUT_SOUP_BASE))
            throw new IllegalArgumentException("Stockpot/base provenance identity/status drift");
    }
}
