package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.stockpot.PreparedStockpotStateAccess;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotBreakPolicy;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Before the actual chunk mutation, after native Level bounds/load checks. No extra chunk load. */
@Mixin(value=Level.class,remap=false)
public abstract class PreparedStockpotRemovalGuardMixin {
    @Group(name="cooking$preparedRemoval",min=1,max=1)
    @Redirect(method={"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",remap=false),
        require=0,allow=1,remap=false)
    private BlockState cooking$guardMapped(LevelChunk chunk, BlockPos pos, BlockState replacement, boolean moving) {
        return cooking$guard(chunk,pos,replacement,moving);
    }
    @Group(name="cooking$preparedRemoval",min=1,max=1)
    @Redirect(method={"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunk;m_6978_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",remap=false),
        require=0,allow=1,remap=false)
    private BlockState cooking$guardRaw(LevelChunk chunk, BlockPos pos, BlockState replacement, boolean moving) {
        return cooking$guard(chunk,pos,replacement,moving);
    }
    @org.spongepowered.asm.mixin.Unique
    private BlockState cooking$guard(LevelChunk chunk,BlockPos pos,BlockState replacement,boolean moving) {
        BlockState old=chunk.getBlockState(pos);
        if(!chunk.getLevel().isClientSide&&old.getBlock() instanceof StockpotBlock&&old.getBlock()!=replacement.getBlock()) {
            var oldEntity=chunk.getBlockEntity(pos);
            if(oldEntity instanceof PreparedStockpotStateAccess access&&access.cooking$isPreparedStockpotSealed()) {
                var decision=PreparedStockpotBreakPolicy.evaluate(access);
                if(!decision.allowed()) {
                    if(!decision.unpaidCarrier()) LogUtils.getLogger().error("Refusing unsafe prepared stockpot removal at {} before chunk mutation: {}",pos,decision.message(),decision.failure());
                    return null; // Actual Level.setBlock interprets null as failure; old block/BE remain intact.
                }
            }
        }
        return chunk.setBlockState(pos,replacement,moving);
    }
}
