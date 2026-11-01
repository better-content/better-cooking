package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.stockpot.PreparedStockpotBreakPolicy;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotStateAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native GameMode mines/damages the tool BEFORE calling removal; reject at the real entry. */
@Mixin(ServerPlayerGameMode.class)
public abstract class PreparedStockpotPlayerBreakGuardMixin {
    // Actual targets are PROTECTED, not private: Mixin field aliases are illegal here.
    // AP/refmap must map these ordinary Mojmap shadows to f_9244_ / f_9245_.
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Inject(method={"destroyBlock(Lnet/minecraft/core/BlockPos;)Z","m_9280_(Lnet/minecraft/core/BlockPos;)Z"},
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$beforeAnyBreakPayment(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if(!(level.getBlockEntity(pos) instanceof PreparedStockpotStateAccess access)||!access.cooking$isPreparedStockpotSealed()) return;
        var decision=PreparedStockpotBreakPolicy.evaluate(access);
        if(decision.allowed())return;
        if(!(player instanceof FakePlayer))player.displayClientMessage(Component.literal(decision.message()),true);
        if(!decision.unpaidCarrier())LogUtils.getLogger().error("Refusing unsafe player stockpot break at {} before tool debit: {}",pos,decision.message(),decision.failure());
        var state=level.getBlockState(pos);level.sendBlockUpdated(pos,state,state,3);
        cir.setReturnValue(false);
    }
}
