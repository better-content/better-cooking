package com.bettercontent.bettercooking.cutting;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.kitchen.KitchenHooks;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared board configuration, NOT ownership/work/payment/intake. Real insertion still pays native split1. */
public final class NativeCuttingSelection {
    public static final String KEY = "better_cooking:cut_choice";
    public record Choice(ResourceLocation recipe, ResourceLocation input) {
        public Choice {
            Objects.requireNonNull(recipe); Objects.requireNonNull(input);
            if (recipe.toString().length() > 512 || input.toString().length() > 512)
                throw new IllegalArgumentException("Oversized cutting choice identity");
        }
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putInt("v", 1);
            tag.putString("recipe", recipe.toString()); tag.putString("input", input.toString()); return tag;
        }
        public static Choice load(CompoundTag tag) {
            if (!tag.contains("v", Tag.TAG_INT) || tag.getInt("v") != 1 || !tag.contains("recipe", Tag.TAG_STRING) || !tag.contains("input", Tag.TAG_STRING))
                throw new IllegalArgumentException("Corrupt cutting choice");
            return new Choice(ResourceLocation.tryParse(tag.getString("recipe")), ResourceLocation.tryParse(tag.getString("input")));
        }
    }
    private NativeCuttingSelection() {}
    public static List<ChoppingBoardRecipe> recipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.CHOPPING_BOARD_RECIPE).stream()
                .sorted(Comparator.comparing(r -> r.getId().toString())).toList();
    }
    public static List<ChoppingBoardRecipe> candidates(Level level, ItemStack actual) {
        if (actual.isEmpty()) return List.of();
        SimpleContainer input = new SimpleContainer(actual);
        return recipes(level).stream().filter(r -> r.matches(input, level)).toList();
    }
    /** Detached ordered POSSIBLE outputs; never assembles, selects RNG, pays or credits intake. */
    public static List<CuttingPlan.Output> preview(ChoppingBoardRecipe recipe) {
        if (recipe instanceof NativeCuttingRecipe owned) return owned.plan().outputs();
        return recipe.getResults().stream().map(stack -> new CuttingPlan.Output(stack, 1)).toList();
    }
    public static String chanceLabel(double chance) {
        return java.math.BigDecimal.valueOf(chance).movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
    /** Only server menu code calls choose after validating the actual connected player's window/reach/cursor. */
    public static boolean choose(ChoppingBoardBlockEntity board, ChoppingBoardRecipe recipe, ItemStack actual) {
        var level = board.getLevel();
        if (level == null || level.isClientSide || CookingMod.discoveryOnly() || board.isRemoved()
                || level.getBlockEntity(board.getBlockPos()) != board || !(board instanceof CuttingBoardAccess access)
                || access.cooking$cuttingSession() != null || !board.getCurrentCutStack().isEmpty() || KitchenHooks.occupied(board)
                || actual.isEmpty() || level.getRecipeManager().byKey(recipe.getId()).orElse(null) != recipe
                || recipe.getType() != ModRecipes.CHOPPING_BOARD_RECIPE || !recipe.matches(new SimpleContainer(actual), level)) return false;
        access.cooking$setCuttingChoice(new Choice(recipe.getId(), Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(actual.getItem()))));
        board.setChanged(); return true;
    }
    /** No paid mutation, preview assembly, random selection, result substitution or legacy recipe priority. */
    public static Optional<ChoppingBoardRecipe> resolve(ChoppingBoardBlockEntity board, ItemStack actual) {
        Level level = Objects.requireNonNull(board.getLevel());
        if (CookingMod.discoveryOnly()) return level.getRecipeManager().getRecipeFor(ModRecipes.CHOPPING_BOARD_RECIPE, new SimpleContainer(actual), level);
        Choice choice = ((CuttingBoardAccess) board).cooking$cuttingChoice();
        if (choice != null) {
            if (actual.isEmpty() || !choice.input().equals(ForgeRegistries.ITEMS.getKey(actual.getItem()))) return Optional.empty();
            return level.getRecipeManager().byKey(choice.recipe()).filter(ChoppingBoardRecipe.class::isInstance)
                    .map(ChoppingBoardRecipe.class::cast).filter(r -> r.getType() == ModRecipes.CHOPPING_BOARD_RECIPE && r.matches(new SimpleContainer(actual), level));
        }
        var matches = candidates(level, actual);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }
}
