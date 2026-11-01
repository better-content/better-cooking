package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.drinks.*;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

/** Source-specific capture only, NOT a finish producer. P9 owns the sole finish call. */
@Mixin(value=TeapotBlockEntity.class,remap=false)
public abstract class NativeTeapotBaseCaptureMixin implements NativeDrinkBaseAccess {
    @Shadow private ResourceLocation teaFluidId;
    @Unique private CompoundTag cooking$base=new CompoundTag();
    @Unique private CompoundTag cooking$pending=new CompoundTag();
    @Unique private CompoundTag cooking$ingredients=new CompoundTag();
    @Unique private CompoundTag cooking$pendingIngredient=new CompoundTag();
    @Override public CompoundTag cooking$drinkBase(){return NativeDrinkCapture.bounded(cooking$base);}
    @Override public CompoundTag cooking$drinkIngredient(int slot){
        if(slot!=0)throw new IllegalArgumentException("Teapot has exactly one native ingredient slot");
        return NativeDrinkCapture.bounded(cooking$ingredients.getCompound("0"));
    }

    @Inject(method="addTeaFluid(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),cancellable=true,require=1)
    private void cooking$beforeFill(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        cooking$pending=new CompoundTag();if(actual.isEmpty())return;
        actual.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).ifPresent(handler->{
            if(handler.getTanks()<1)return;
            var fluid=handler.getFluidInTank(0);if(fluid.getAmount()<1000)return;
            // HEAD cancellation precedes native FluidUtils.emptyItem, container return and owner writes.
            if(!NativeDrinkSafety.allowsFluidItem(fluid.getFluid(),actual)){
                NativeDrinkSafety.explainRejection(actor);cir.setReturnValue(false);return;
            }
            if(level.isClientSide())return;
            ResourceLocation id=ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
            if(id!=null){
                cooking$pending=NativeDrinkCapture.base(actor,actual,id.toString());
                if(actor instanceof net.minecraft.world.entity.player.Player player&&player.isCreative()){
                    cooking$pending.putBoolean("unpaid",true);cooking$pending.remove("hydration");cooking$pending.remove("quenching");cooking$pending.remove("intake");
                    cooking$pending.putString("gap","Native creative FluidUtils.emptyItem skips physical source debit");
                }
            }
        });
    }
    @Inject(method="addTeaFluid(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"),require=1)
    private void cooking$afterFill(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()) {
            cooking$base=cooking$pending.copy();
            if(!cooking$base.getString("fluid").equals(teaFluidId.toString()))cooking$base=new CompoundTag();
            ((TeapotBlockEntity)(Object)this).setChanged();
        }
        cooking$pending=new CompoundTag();
    }
    @Inject(method="removeTeaFluid(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"),require=1)
    private void cooking$remove(Level level,LivingEntity actor,ItemStack vessel,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()){cooking$base=new CompoundTag();((TeapotBlockEntity)(Object)this).setChanged();}
    }
    @Inject(method="receiveDripstoneFluid(Lnet/minecraft/world/level/material/Fluid;)Z",at=@At("HEAD"),cancellable=true,require=1)
    private void cooking$beforeDrip(Fluid fluid,CallbackInfoReturnable<Boolean> cir){
        if(!NativeDrinkSafety.allowsDripstone(fluid))cir.setReturnValue(false);
    }
    @Inject(method="receiveDripstoneFluid(Lnet/minecraft/world/level/material/Fluid;)Z",at=@At("RETURN"),require=1)
    private void cooking$pipe(Fluid fluid,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValueZ()){cooking$base=NativeDrinkCapture.fluid(fluid);((TeapotBlockEntity)(Object)this).setChanged();}
    }
    @Inject(method="addIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),require=1)
    private void cooking$beforeIngredient(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        cooking$pendingIngredient=new CompoundTag();
        if(!level.isClientSide()&&!actual.isEmpty()&&!actual.is(com.bettercontent.bettercooking.CookingItems.PREPARED_BATCH.get()))
            cooking$pendingIngredient=NativeDrinkIngredients.snapshot(actor,actual);
    }
    @Inject(method="addIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"),require=1)
    private void cooking$afterIngredient(Level level,LivingEntity actor,ItemStack actual,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()){
            if(cooking$pendingIngredient.isEmpty())cooking$ingredients.remove("0");else cooking$ingredients.put("0",cooking$pendingIngredient.copy());
            ((TeapotBlockEntity)(Object)this).setChanged();
        }
        cooking$pendingIngredient=new CompoundTag();
    }
    @Inject(method="insertIngredient(Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"),require=1)
    private void cooking$beforeAutomation(ItemStack actual,CallbackInfo ci){
        cooking$pendingIngredient=new CompoundTag();TeapotBlockEntity owner=(TeapotBlockEntity)(Object)this;
        if((owner.getLevel()==null||!owner.getLevel().isClientSide())&&owner.canInsertIngredient(actual)
                &&!actual.is(com.bettercontent.bettercooking.CookingItems.PREPARED_BATCH.get()))
            cooking$pendingIngredient=NativeDrinkIngredients.snapshot(null,actual);
    }
    @Inject(method="insertIngredient(Lnet/minecraft/world/item/ItemStack;)V",at=@At("RETURN"),require=1)
    private void cooking$afterAutomation(ItemStack actual,CallbackInfo ci){
        if(!cooking$pendingIngredient.isEmpty()){
            cooking$ingredients.put("0",cooking$pendingIngredient.copy());((TeapotBlockEntity)(Object)this).setChanged();
        }
        cooking$pendingIngredient=new CompoundTag();
    }
    @Inject(method="removeIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Z",at=@At("RETURN"),require=1)
    private void cooking$takeIngredient(Level level,LivingEntity actor,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide()&&cir.getReturnValueZ()){cooking$ingredients=new CompoundTag();((TeapotBlockEntity)(Object)this).setChanged();}
    }
    @Inject(method="saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"),require=1,remap=true)
    private void cooking$save(CompoundTag tag,CallbackInfo ci){
        if(!cooking$base.isEmpty())tag.put(NativeDrinkCapture.BASE_KEY,NativeDrinkCapture.bounded(cooking$base));
        else tag.remove(NativeDrinkCapture.BASE_KEY);
        cooking$ingredients=NativeDrinkIngredients.restoreCaptures(cooking$ingredients,List.of(((TeapotBlockEntity)(Object)this).getInput()));
        if(cooking$ingredients.isEmpty())tag.remove(NativeDrinkIngredients.KEY);else tag.put(NativeDrinkIngredients.KEY,cooking$ingredients.copy());
    }
    @Inject(method="load(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"),require=1,remap=true)
    private void cooking$load(CompoundTag tag,CallbackInfo ci){
        cooking$pending=new CompoundTag();cooking$pendingIngredient=new CompoundTag();
        if(tag.contains(NativeDrinkIngredients.KEY)&&!tag.contains(NativeDrinkIngredients.KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Wrong teapot ingredient capture type");
        cooking$ingredients=NativeDrinkIngredients.restoreCaptures(tag.getCompound(NativeDrinkIngredients.KEY),List.of(((TeapotBlockEntity)(Object)this).getInput()));
        if(tag.contains(NativeDrinkCapture.BASE_KEY)&&!tag.contains(NativeDrinkCapture.BASE_KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Wrong teapot base capture type");
        cooking$base=tag.contains(NativeDrinkCapture.BASE_KEY,Tag.TAG_COMPOUND)
                ?NativeDrinkCapture.bounded(tag.getCompound(NativeDrinkCapture.BASE_KEY)):new CompoundTag();
        if(!cooking$base.isEmpty()&&!cooking$base.getString("fluid").equals(teaFluidId.toString()))throw new IllegalArgumentException("Teapot/base provenance fluid drift");
    }
    @Inject(method="getDrops()Ljava/util/List;",at=@At("RETURN"),require=1)
    private void cooking$portable(CallbackInfoReturnable<List<ItemStack>> cir){
        // Native portable teapot preserves base provenance across pickup/replacement; do not
        // touch ordinary cup/input drops or fabricate an ItemStack for the consumed fluid.
        if(cooking$base.isEmpty())return;
        for(ItemStack drop:cir.getReturnValue()) {
            ResourceLocation id=ForgeRegistries.ITEMS.getKey(drop.getItem());
            if(id==null||!id.toString().equals("kaleidoscope_cookery:teapot"))continue;
            CompoundTag block=BlockItem.getBlockEntityData(drop);if(block==null)continue;
            CompoundTag copy=block.copy();copy.put(NativeDrinkCapture.BASE_KEY,NativeDrinkCapture.bounded(cooking$base));
            BlockItem.setBlockEntityData(drop,((TeapotBlockEntity)(Object)this).getType(),copy);
        }
    }
}
