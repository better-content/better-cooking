package com.bettercontent.bettercooking.cutting;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.CookingRuntime;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModEnchantments;
import com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/** Mainhand native tool + offhand ingredient. No board state, animation or replacement recipe. */
public final class HandCutting {
    private record Match(String id, CuttingBoardRecipe farmers, ChoppingBoardRecipe kaleidoscope) {}

    @SubscribeEvent
    public void use(PlayerInteractEvent.RightClickItem event) {
        if (CookingMod.discoveryOnly() || event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) return;
        var player = event.getEntity();
        ItemStack tool = player.getMainHandItem(), source = player.getOffhandItem();
        if (tool.isEmpty() || source.isEmpty()) return;
        Level level = event.getLevel();
        ItemStack one = source.copyWithCount(1);
        List<Match> choices = matches(level, one, tool);
        if (choices.isEmpty()) return;
        // RecipeManager collection iteration is not a selection policy. Stable ID is both
        // the choice and the interaction hint when multiple provider recipes overlap.
        Match chosen = choices.get(0);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(player instanceof ServerPlayer server)) return;
        server.displayClientMessage(Component.literal("Cutting: " + chosen.id() + (choices.size() > 1 ? " (first of " + choices.size() + ")" : "")), true);
        // Refuse oversized or unsupported output before touching either hand. Chance draws
        // occur only after input/tool preflight, never during matching or preview.
        if (one.getCount() != 1 || !ItemStack.matches(tool, server.getMainHandItem()) || !ItemStack.isSameItemSameTags(source, one)) return;
        int kcActions = 0;
        if (chosen.kaleidoscope() != null) {
            int quick = Math.min(ModEnchantments.QUICK_KNIFE.get().getMaxLevel(),
                    Math.max(0, tool.getEnchantmentLevel(ModEnchantments.QUICK_KNIFE.get())));
            kcActions = (chosen.kaleidoscope().getCutCount() + (1 << quick) - 1) / (1 << quick) + 1;
            // Instant hand cutting has no board on which to retain a partially paid
            // transaction if the knife breaks mid-sequence; reject that case before debit.
            if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() < kcActions) return;
        }
        List<ItemStack> ready = new ArrayList<>();
        try {
            CuttingPlan.requireBounded(one);
            ItemStack remainder = one.getCraftingRemainingItem();
            if (!remainder.isEmpty()) CuttingPlan.requireBounded(remainder);
            List<ItemStack> output;
            if (chosen.farmers() != null) {
                var holder = new ItemStackHandler(1);
                holder.setStackInSlot(0, one);
                int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
                output = chosen.farmers().rollResults(level.random, fortune, new RecipeWrapper(holder));
            } else if (chosen.kaleidoscope() instanceof NativeCuttingRecipe owned) {
                output = owned.plan().roll(level.random);
            } else {
                output = chosen.kaleidoscope().getResults();
            }
            if (output == null || output.size() > CuttingPlan.MAX_OUTPUTS) return;
            if (chosen.kaleidoscope() instanceof NativeCuttingRecipe) {
                // Match the board's paid-source provenance without crediting an entire stack.
                output = CookingRuntime.carryCuttingProvenance(one, output, level.getGameTime());
            }
            for (ItemStack stack : output) {
                if (stack == null || stack.getCount() < 0 || stack.getCount() > 64) return;
                if (stack.isEmpty()) continue;
                CuttingPlan.requireBounded(stack);
                ready.add(stack.copy());
            }
            if (!remainder.isEmpty()) ready.add(remainder.copy());
        } catch (RuntimeException invalid) {
            // A malformed or unsupported recipe/provenance must not debit the input.
            server.displayClientMessage(Component.literal("Cutting unavailable for " + chosen.id()), true);
            return;
        }
        // Creative still pays one input: otherwise this hand route creates unlimited food.
        source.shrink(1);
        if (chosen.kaleidoscope() == null) {
            tool.hurtAndBreak(1, server, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        } else {
            // Pinned KC board: each successful onCutItem (including retrieval) has a
            // 25% one-durability draw; Quick Knife advances 2^level cuts per action.
            for (int i = 0; i < kcActions; i++) if (level.random.nextDouble() < .25)
                tool.hurtAndBreak(1, server, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        for (ItemStack stack : ready) {
            ItemStack extra = stack.copy();
            server.getInventory().add(extra);
            if (!extra.isEmpty()) {
                // Vanilla/Forge may return null if an external toss hook cancels the drop.
                // The input was already paid; do not crash or duplicate the canceled item.
                server.drop(extra, false);
            }
        }
    }

    private static List<Match> matches(Level level, ItemStack input, ItemStack tool) {
        var found = new ArrayList<Match>();
        var holder = new ItemStackHandler(1);
        holder.setStackInSlot(0, input);
        RecipeWrapper wrapped = new RecipeWrapper(holder);
        for (CuttingBoardRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CUTTING.get())) {
            if (recipe.matches(wrapped, level) && recipe.getTool().test(tool))
                found.add(new Match(recipe.getId().toString(), recipe, null));
        }
        for (ChoppingBoardRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.CHOPPING_BOARD_RECIPE)) {
            if (!recipe.matches(new SimpleContainer(input), level) || !tool.is(TagMod.KITCHEN_KNIFE)) continue;
            if (recipe instanceof NativeCuttingRecipe own && !own.plan().tool().test(tool)) continue;
            found.add(new Match(recipe.getId().toString(), null, recipe));
        }
        found.sort(Comparator.comparing(Match::id));
        return found;
    }
}
