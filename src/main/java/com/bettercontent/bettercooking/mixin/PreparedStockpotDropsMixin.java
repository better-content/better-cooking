package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.stockpot.PreparedStockpotStateAccess;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotBreakPolicy;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

/** Pure old-BE drop snapshot, never a world lookup after destruction and never a refund cursor. */
@Mixin(value=StockpotBlock.class,remap=false)
public abstract class PreparedStockpotDropsMixin {
    @Inject(method={"getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",
        "m_49635_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;"},
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$validateDrops(BlockState state, LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        var old=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if(old instanceof PreparedStockpotStateAccess access&&access.cooking$isPreparedStockpotSealed()
            &&!PreparedStockpotBreakPolicy.evaluate(access).allowed()) cir.setReturnValue(List.of());
    }
    @Inject(method={"getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",
        "m_49635_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;"},
        at=@At("RETURN"),cancellable=true,require=1,remap=false)
    private void cooking$conserveDrops(BlockState state, LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        var old=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if(!(old instanceof StockpotBlockEntity pot)||!(old instanceof PreparedStockpotStateAccess access)
            ||!access.cooking$isPreparedStockpotSealed()) return;
        var decision=PreparedStockpotBreakPolicy.evaluate(access);
        if(!decision.allowed()) { cir.setReturnValue(List.of());return; }
        List<ItemStack> paid=decision.drops();
        ArrayList<ItemStack> result=new ArrayList<>(cir.getReturnValue());
        // Native PUT_INGREDIENT adds the actual input object once. Replace that one reference,
        // not an equal loot item or lid. COOKING/FINISHED native drops contain no input/output.
        if(pot.getStatus()==IStockpot.PUT_INGREDIENT) for(ItemStack input:pot.getInputs()) {
            if(input.isEmpty()) continue;
            boolean removed=false;
            for(int i=0;i<result.size();i++) if(result.get(i)==input) { result.remove(i);removed=true;break; }
            if(!removed) {
                com.mojang.logging.LogUtils.getLogger().error("Prepared stockpot native input drop identity changed during loot callback; refusing all loot (conservation unproven)");
                cir.setReturnValue(List.of());return;
            }
        }
        result.addAll(paid);cir.setReturnValue(result);
    }
}
