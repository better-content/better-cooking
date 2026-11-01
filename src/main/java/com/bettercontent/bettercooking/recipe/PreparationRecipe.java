package com.bettercontent.bettercooking.recipe;

import com.bettercontent.bettercooking.policy.CulinaryRoute;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import java.util.List;

/** Actual registered recipe, not a JSON-only plan. Assembly is committed by a validated kitchen action. */
public final class PreparationRecipe implements Recipe<Container> {
    public record Input(Ingredient ingredient, int count) {
        public Input { if (ingredient == null || ingredient.isEmpty() || count < 1 || count > 64) throw new IllegalArgumentException("Invalid preparation input"); }
    }
    private final ResourceLocation id;
    private final List<Input> inputs;
    private final ItemStack result;
    private final String surface, finish, workClass;
    private final int servings, cookTicks;
    private final List<CulinaryRoute.Stage> stages;
    private final FlexibleFormulation nativeQuality;
    public PreparationRecipe(ResourceLocation id, List<Input> inputs, ItemStack result, String surface, String finish,
                             int servings, String workClass, List<CulinaryRoute.Stage> stages, int cookTicks) {
        this(id, inputs, result, surface, finish, servings, workClass, stages, cookTicks, FlexibleFormulation.fixed());
    }
    public PreparationRecipe(ResourceLocation id, List<Input> inputs, ItemStack result, String surface, String finish,
                             int servings, String workClass, List<CulinaryRoute.Stage> stages, int cookTicks, FlexibleFormulation nativeQuality) {
        this.nativeQuality = java.util.Objects.requireNonNull(nativeQuality);
        if (nativeQuality.flexible() && inputs.stream().anyMatch(input -> input.count() != 1))
            throw new IllegalArgumentException("Flexible required groups must each request one distinct unit");
        if (inputs.isEmpty() || inputs.size() > 9 || result.isEmpty() || result.getCount() > 64 || servings < 1
                || stages.isEmpty() || cookTicks < 0) throw new IllegalArgumentException("Invalid preparation recipe " + id);
        this.id = id; this.inputs = List.copyOf(inputs); this.result = result.copy(); this.surface = surface;
        this.finish = finish; this.servings = servings; this.workClass = workClass; this.stages = List.copyOf(stages); this.cookTicks = cookTicks;
    }
    public List<Input> inputs() { return inputs; }
    public List<CulinaryRoute.Stage> stages() { return stages; }
    public String surface() { return surface; }
    public String finish() { return finish; }
    public String workClass() { return workClass; }
    public int servings() { return servings; }
    public int cookTicks() { return cookTicks; }
    /** Full executable definition, shared by in-progress and committed transferable batches. */
    public String fingerprint() {
        String definition = "better_cooking:preparation_definition.v1|" + id + "|" + surface + "|" + finish + "|" + workClass
            + "|" + servings + "|" + cookTicks + "|" + stages
            + "|" + inputs.stream().map(input -> input.ingredient().toJson() + ":" + input.count()).toList()
            + "|" + result.save(new net.minecraft.nbt.CompoundTag()) + "|native_quality=" + nativeQuality.fingerprint();
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(definition.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public FlexibleFormulation nativeQuality() { return nativeQuality; }
    public boolean flexible() { return nativeQuality.flexible(); }
    public java.util.Optional<IngredientAccounting.Plan> plan(Container container) { return IngredientAccounting.plan(this, container); }
    public boolean acceptsPaid(List<ItemStack> paid) {
        if (paid.isEmpty() || paid.size() > (flexible() ? 9 : 64) || paid.stream().anyMatch(s -> s.isEmpty() || (flexible() && s.getCount() != 1))) return false;
        var plan = plan(new net.minecraft.world.SimpleContainer(paid.stream().map(ItemStack::copy).toArray(ItemStack[]::new)));
        if (plan.isEmpty()) return false;
        int[] consumed = plan.get().consumed();
        for (int slot = 0; slot < paid.size(); slot++) if (consumed[slot] != paid.get(slot).getCount()) return false;
        return true;
    }
    /** Called once at preparation commit; baking inherits stored quality and must not call this again. */
    public com.github.ysbbbbbb.kaleidoscopecookery.item.quality.Quality evaluateQuality(net.minecraft.server.level.ServerLevel level, List<ItemStack> paid) {
        java.util.Objects.requireNonNull(level, "Actual server level required");
        if (!flexible()) return com.github.ysbbbbbb.kaleidoscopecookery.item.quality.Quality.STANDARD;
        if (!acceptsPaid(paid)) throw new IllegalArgumentException("Invalid actual paid flexible formulation");
        return com.github.ysbbbbbb.kaleidoscopecookery.item.quality.QualityEvaluator.evaluate(
            paid.stream().map(ItemStack::copy).toList(), inputs.stream().map(Input::ingredient).toList(),
            new ResourceLocation(nativeQuality.sourceRecipeId()), level.getSeed());
    }
    public boolean finishedOnPreparationSurface() { return finish.equals("board") || finish.equals("assembly"); }
    @Override public boolean matches(Container container, Level level) { return plan(container).isPresent(); }
    @Override public ItemStack assemble(Container container, RegistryAccess registry) { return ItemStack.EMPTY; }
    @Override public ItemStack getResultItem(RegistryAccess registry) { return result.copy(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return CookingRecipes.PREPARATION_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return CookingRecipes.PREPARATION_TYPE.get(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, inputs.stream().map(Input::ingredient).toArray(Ingredient[]::new));
    }
}
