package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.finishing.NativeFinishing;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.registry.FoodBiteRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PotBlockEntity.class)
public abstract class NativePotFinishMixin {
    @Shadow(remap=false) private ItemStack result;
    @Shadow(remap=false) private int stirFryCount;
    @Shadow(remap=false) public abstract SimpleContainer getContainer();
    /** One completed-cook write, AFTER quality and native failed-stir selection, not recipe assembly. */
    @Inject(method="tickCooking(Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;)V",remap=false,
        at=@At(value="FIELD",target="Lcom/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/PotBlockEntity;currentTick:I",opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER,remap=false),require=1)
    private void betterCooking$carryFinalPot(Level level,RandomSource random,CallbackInfo ci) {
        boolean nativeFailure=stirFryCount>0&&result.getCount()==1&&result.is(FoodBiteRegistry.getItem(FoodBiteRegistry.SUSPICIOUS_STIR_FRY));
        result=NativeFinishing.pot(getContainer(),result,nativeFailure,level.getGameTime());
    }
}
