package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.finishing.NativeFinishing;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.registry.TeacupRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TeapotBlockEntity.class)
public abstract class NativeTeaFinishMixin {
    @Shadow(remap=false) private ItemStack input;
    @Shadow(remap=false) private ItemStack result;
    @Unique private boolean betterCooking$recipeMiss;
    @Inject(method="tick(Lnet/minecraft/world/level/Level;)V",remap=false,
        at=@At(value="FIELD",target="Lcom/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/TeapotBlockEntity;status:I",opcode=Opcodes.PUTFIELD,ordinal=0,shift=At.Shift.AFTER,remap=false),require=1)
    private void betterCooking$matchedTeaRecipe(Level level,CallbackInfo ci) {
        if(!CookingMod.discoveryOnly())betterCooking$recipeMiss=false;
    }
    @Inject(method="tick(Lnet/minecraft/world/level/Level;)V",remap=false,
        at=@At(value="FIELD",target="Lcom/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/TeapotBlockEntity;status:I",opcode=Opcodes.PUTFIELD,ordinal=1,shift=At.Shift.AFTER,remap=false),require=1)
    private void betterCooking$actualTeaRecipeMiss(Level level,CallbackInfo ci) {
        if(!CookingMod.discoveryOnly())betterCooking$recipeMiss=NativeFinishing.prepared(input);
    }
    @Inject(method="tick(Lnet/minecraft/world/level/Level;)V",remap=false,
        at=@At(value="FIELD",target="Lcom/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/TeapotBlockEntity;status:I",opcode=Opcodes.PUTFIELD,ordinal=2,shift=At.Shift.AFTER,remap=false),require=1)
    private void betterCooking$carryFinalTea(Level level,CallbackInfo ci) {
        boolean nativeFailure=betterCooking$recipeMiss&&result.getCount()==4&&result.is(TeacupRegistry.getItem(TeacupRegistry.MYSTERY_TEA));
        result=NativeFinishing.tea((TeapotBlockEntity)(Object)this,input,result,nativeFailure,level.getGameTime());
    }
    /** A processing teapot can unload/restart between the real miss and its final tick. */
    @Inject(method="saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V",remap=true,at=@At("TAIL"),require=1)
    private void betterCooking$saveActualRecipeMiss(CompoundTag tag,CallbackInfo ci) {
        if(!CookingMod.discoveryOnly())tag.putBoolean("better_cooking:actual_teapot_recipe_miss",betterCooking$recipeMiss);
    }
    @Inject(method="load(Lnet/minecraft/nbt/CompoundTag;)V",remap=true,at=@At("TAIL"),require=1)
    private void betterCooking$loadActualRecipeMiss(CompoundTag tag,CallbackInfo ci) {
        if(!CookingMod.discoveryOnly())betterCooking$recipeMiss=tag.getBoolean("better_cooking:actual_teapot_recipe_miss");
    }
}
