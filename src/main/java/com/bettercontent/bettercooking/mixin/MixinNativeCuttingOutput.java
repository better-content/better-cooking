package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.cutting.*;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ChoppingBoardBlockEntity.class, remap = false)
public abstract class MixinNativeCuttingOutput implements CuttingBoardAccess {
    @Shadow private ItemStack currentCutStack;
    @Shadow private List<ItemStack> results;
    @Shadow private int currentCutCount;
    @Shadow private int maxCutCount;
    @Shadow private void resetBoardData() { throw new AssertionError("Mixin shadow"); }
    @Unique private CuttingSession cooking$cutting;
    @Unique private int cooking$outputOrdinal;
    @Unique private NativeCuttingSelection.Choice cooking$choice;
    @Override public NativeCuttingSelection.Choice cooking$cuttingChoice() { return cooking$choice; }
    @Override public void cooking$setCuttingChoice(NativeCuttingSelection.Choice choice) {
        if (!currentCutStack.isEmpty() || cooking$cutting != null) throw new IllegalStateException("Cannot change a paid cutting choice");
        cooking$choice = java.util.Objects.requireNonNull(choice);
    }
    @Override public CuttingSession cooking$cuttingSession() { return cooking$cutting; }
    @Unique private void cooking$dirty() { ((net.minecraft.world.level.block.entity.BlockEntity) (Object) this).setChanged(); }

    @Inject(method = "onPutItem", at = @At("HEAD"), require = 1, remap = false)
    private void cooking$preflight(Level level, LivingEntity actor, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (CookingMod.discoveryOnly() || level.isClientSide || !results.isEmpty() || com.bettercontent.bettercooking.kitchen.KitchenHooks.occupied((net.minecraft.world.level.block.entity.BlockEntity) (Object) this)) return;
        if (cooking$cutting != null) throw new IllegalStateException("Native empty board retained a paid cutting transaction");
        var candidate = NativeCuttingSelection.resolve((ChoppingBoardBlockEntity) (Object) this, stack);
        if (candidate.orElse(null) instanceof NativeCuttingRecipe recipe) {
            CuttingPlan.requireBounded(stack);
            if (!recipe.plan().ingredient().test(stack)) throw new IllegalStateException("Native cutting candidate/input drift");
        }
    }
    @Group(name = "cutting_actual_lookup", min = 1, max = 1)
    @Redirect(method = "onPutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"), require = 0, remap = false)
    private Optional<?> cooking$mappedLookup(net.minecraft.world.item.crafting.RecipeManager manager, net.minecraft.world.item.crafting.RecipeType<?> type, net.minecraft.world.Container input, Level level) {
        return NativeCuttingSelection.resolve((ChoppingBoardBlockEntity) (Object) this, input.getItem(0));
    }
    @Group(name = "cutting_actual_lookup", min = 1, max = 1)
    @Redirect(method = "onPutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;m_44015_(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"), require = 0, remap = false)
    private Optional<?> cooking$rawLookup(net.minecraft.world.item.crafting.RecipeManager manager, net.minecraft.world.item.crafting.RecipeType<?> type, net.minecraft.world.Container input, Level level) {
        return NativeCuttingSelection.resolve((ChoppingBoardBlockEntity) (Object) this, input.getItem(0));
    }
    @Redirect(method = "onPutItem", at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/crafting/recipe/ChoppingBoardRecipe;getResults()Ljava/util/List;"), require = 1, remap = false)
    private List<ItemStack> cooking$capturePaid(ChoppingBoardRecipe recipe) {
        if (CookingMod.discoveryOnly() || !(recipe instanceof NativeCuttingRecipe owned)) return recipe.getResults();
        if (cooking$cutting != null) throw new IllegalStateException("Native board overwrote a paid cutting transaction");
        cooking$cutting = new CuttingSession(recipe.getId(), owned.plan(), currentCutStack);
        return owned.plan().preview();
    }
    @Inject(method = "onCutItem", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void cooking$ready(Level level, LivingEntity actor, ItemStack cutter, CallbackInfoReturnable<Boolean> cir) {
        cooking$outputOrdinal = 0;
        if (CookingMod.discoveryOnly() || cooking$cutting == null || com.bettercontent.bettercooking.kitchen.KitchenHooks.occupied((net.minecraft.world.level.block.entity.BlockEntity) (Object) this)) return;
        if (!(level instanceof ServerLevel server)) { cir.setReturnValue(false); return; }
        var actualBoard = (ChoppingBoardBlockEntity) (Object) this;
        if (actualBoard.isRemoved() || server.getBlockEntity(actualBoard.getBlockPos()) != actualBoard) { cir.setReturnValue(false); return; }
        cooking$cutting.validateNative(currentCutStack, maxCutCount, currentCutCount);
        if (currentCutCount < maxCutCount) {
            if (!cooking$cutting.plan().tool().test(cutter)) cir.setReturnValue(false);
            return; // Preserve the actual native knife action and its count/enchantment/sound behavior.
        }
        cooking$cutting.select(server.random, this::cooking$dirty);
        cooking$cutting.materialize(server, this::cooking$dirty);
        results = cooking$cutting.outputs();
        if (results.isEmpty()) { // All authored chance results failed / actual BCC provider denied new seeds.
            resetBoardData(); cir.setReturnValue(true);
        }
    }
    @Redirect(method = "onCutItem", at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopecookery/blockentity/kitchen/ChoppingBoardBlockEntity;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"), require = 1, remap = false)
    private void cooking$deliver(Level level, BlockPos position, ItemStack output) {
        if (CookingMod.discoveryOnly() || cooking$cutting == null) { ChoppingBoardBlockEntity.popResource(level, position, output); return; }
        if (!(level instanceof ServerLevel server)) throw new IllegalStateException("Client attempted owned cutting delivery");
        cooking$cutting.deliver(cooking$outputOrdinal++, output, server, position, this::cooking$dirty);
    }
    @Inject(method = "resetBoardData", at = @At("HEAD"), require = 1, remap = false)
    private void cooking$clear(CallbackInfo ci) {
        if (!CookingMod.discoveryOnly() && cooking$cutting != null && cooking$cutting.materialized() && !cooking$cutting.complete())
            throw new IllegalStateException("Native board attempted to discard undelivered paid cutting outputs");
        cooking$cutting = null;
    }
    @Inject(method = {"saveAdditional", "m_183515_"}, at = @At("RETURN"), require = 1, remap = false)
    private void cooking$save(CompoundTag tag, CallbackInfo ci) {
        if (!CookingMod.discoveryOnly() && cooking$cutting != null) tag.put(CuttingSession.KEY, cooking$cutting.save());
        if (!CookingMod.discoveryOnly() && cooking$choice != null) tag.put(NativeCuttingSelection.KEY, cooking$choice.save());
    }
    @Inject(method = {"load", "m_142466_"}, at = @At("RETURN"), require = 1, remap = false)
    private void cooking$load(CompoundTag tag, CallbackInfo ci) {
        cooking$cutting = null; cooking$choice = null;
        if (CookingMod.discoveryOnly()) return;
        if (tag.contains(NativeCuttingSelection.KEY)) {
            if (!tag.contains(NativeCuttingSelection.KEY, Tag.TAG_COMPOUND)) throw new IllegalStateException("Corrupt cutting choice type");
            cooking$choice = NativeCuttingSelection.Choice.load(tag.getCompound(NativeCuttingSelection.KEY));
        }
        if (!tag.contains(CuttingSession.KEY)) return;
        if (!tag.contains(CuttingSession.KEY, Tag.TAG_COMPOUND)) throw new IllegalStateException("Corrupt cutting transaction type");
        cooking$cutting = CuttingSession.load(tag.getCompound(CuttingSession.KEY));
        cooking$cutting.validateNative(currentCutStack, maxCutCount, currentCutCount);
        List<ItemStack> expected = cooking$cutting.materialized() ? cooking$cutting.outputs() : cooking$cutting.plan().preview();
        if (results.size() != expected.size()) throw new IllegalStateException("Persisted native cutting result count drift");
        for (int i = 0; i < results.size(); i++) if (!ItemStack.matches(results.get(i), expected.get(i))) throw new IllegalStateException("Persisted native cutting result payload drift");
    }
}
