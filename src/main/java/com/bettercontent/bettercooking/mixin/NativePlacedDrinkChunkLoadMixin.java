package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.drinks.placed.NativeDrinkBlockEntity;
import com.bettercontent.bettercooking.drinks.placed.PlacedDrinks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Existing saved BE tag with wrong/missing type is not an old block without a BE tag. Preserve poison, not factory UNKNOWN. */
@Mixin(LevelChunk.class)
public abstract class NativePlacedDrinkChunkLoadMixin {
    @Inject(method="promotePendingBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/block/entity/BlockEntity;",at=@At("HEAD"),cancellable=true,require=1)
    private void cooking$rejectSavedTypeFallback(BlockPos pos,CompoundTag tag,CallbackInfoReturnable<BlockEntity> callback){
        var chunk=(LevelChunk)(Object)this;var state=chunk.getBlockState(pos);if(!PlacedDrinks.known(state))return;
        String id=tag.getString("id");if(id.equals("better_cooking:native_drink_cups")||id.equals("DUMMY")&&!tag.contains(NativeDrinkBlockEntity.KEY))return;
        var body=NativeDrinkBlockEntity.rejectedSavedType(pos,state,tag);chunk.addAndRegisterBlockEntity(body);callback.setReturnValue(body);
    }
}
