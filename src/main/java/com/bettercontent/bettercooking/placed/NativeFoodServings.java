package com.bettercontent.bettercooking.placed;

import com.bettercontent.bettercooking.*;
import com.bettercontent.betterspicyfarmers.api.SpiceApi;
import com.bettercontent.betterspicyfarmers.seasoning.PlacedFoodAdapters;
import com.bettercontent.betterspicyfarmers.seasoning.ServingContext;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional setup-only KC family adapter. Root must explicitly register it after item registries settle. */
public final class NativeFoodServings {
    private static boolean registered;
    private static final ThreadLocal<Boolean> BITING = new ThreadLocal<>();
    private NativeFoodServings() {}
    public static boolean enabled() { return registered && !CookingMod.discoveryOnly(); }
    public static boolean inBiteScope() { return Boolean.TRUE.equals(BITING.get()); }
    public static synchronized void register() {
        if (CookingMod.discoveryOnly()) return;
        if (registered) throw new IllegalStateException("KC placed servings registered twice");
        int matched = 0;
        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            if (entry.getValue() instanceof BlockItem item && item.getBlock() instanceof FoodBiteBlock food) {
                PortionProgress.validate(food.getMaxBites());
                SpiceApi.registerServingAdapter(entry.getKey().location(), stack -> food.getMaxBites());
                matched++;
            }
        }
        if (matched == 0) throw new IllegalStateException("Register KC serving adapters after native item registration");
        PlacedFoodAdapters.register(new PlacedFoodAdapters.Adapter() {
            public boolean supports(BlockState state) { return state.getBlock() instanceof FoodBiteBlock; }
            public int remaining(BlockState state) { return NativeFoodServings.remaining(state); }
        });
        registered = true;
    }
    public static int remaining(BlockState state) {
        if (!(state.getBlock() instanceof FoodBiteBlock food)) return 0;
        return remaining(food.getMaxBites(), state.getValue(food.getBites()));
    }
    public static int remaining(int maximum, int bites) {
        PortionProgress.validate(maximum);
        if (bites < 0 || bites > maximum) throw new IllegalArgumentException("Invalid native KC bites");
        return maximum - bites;
    }
    public static BlockPos pairAnchor(BlockPos pos, Direction facing, boolean left) {
        return left ? pos.relative(facing.getCounterClockWise()) : pos;
    }
    public static BlockPos largeAnchor(BlockPos pos, int partX, int partZ) {
        if (Math.abs(partX) > 1 || Math.abs(partZ) > 1) throw new IllegalArgumentException("Invalid native NinePart offset");
        return pos.offset(-partX, 0, -partZ);
    }
    /** Pinned 1.6: LEFT is RIGHT + facing.clockwise, hence inverse is counterclockwise.
     * NinePart offsets are fixed world X/Z, not rotated by food facing. */
    public static BlockPos anchor(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof FoodBiteOneByTwoBlock && state.getValue(FoodBiteOneByTwoBlock.POSITION) == FoodBiteOneByTwoBlock.LEFT)
            return pairAnchor(pos, state.getValue(FoodBiteBlock.FACING), true);
        if (state.getBlock() instanceof FoodBiteThreeByThreeBlock) {
            var part = state.getValue(FoodBiteThreeByThreeBlock.PART);
            return largeAnchor(pos, part.getPosX(), part.getPosY());
        }
        return pos;
    }
    public static ItemStack preview(Level level, BlockPos pos, BlockState state) {
        BlockPos anchor = anchor(pos, state);
        BlockState whole = level.getBlockState(anchor);
        if (whole.getBlock() != state.getBlock()) throw new IllegalStateException("Missing KC food anchor");
        ItemStack portion = new ItemStack(whole.getBlock().asItem());
        if (portion.isEmpty()) throw new IllegalStateException("KC native bite has no registered whole item");
        int quality = whole.getValue(FoodBiteBlock.QUALITY);
        if (quality != FoodBiteBlock.DEFAULT_QUALITY) portion.getOrCreateTag().putInt(CookingRuntime.QUALITY, quality);
        portion.getOrCreateTag().putBoolean(CookingRuntime.SINGLE_PORTION, true);
        // Exact native action properties, not an item/maxBites approximation (DarkCuisine is 2 vs 1).
        // Root's shared preview overlays only SERVING/QUALITY, retaining this typed native metadata.
        var raw = ((com.bettercontent.bettercooking.mixin.FoodBiteAccess) whole.getBlock()).cooking$nativeBiteProperties();
        var bite = new net.minecraft.nbt.CompoundTag();
        bite.putInt("hunger", raw.getNutrition());
        bite.putDouble("saturation_modifier", raw.getSaturationModifier());
        bite.putInt("max_portions", ((FoodBiteBlock) whole.getBlock()).getMaxBites());
        portion.getOrCreateTag().put("better_cooking:native_bite", bite);
        return PlacedFoodAdapters.preview(level, anchor, whole, portion);
    }
    /** Wraps the actual native call, preserving its probability draw, effects and block mutation. */
    public static InteractionResult eat(FoodBiteBlock food, Level level, BlockPos pos, BlockState state, Player player) {
        NativeFoodEatAccess nativeFood = (NativeFoodEatAccess) food;
        if (!enabled() || level.isClientSide) return nativeFood.cooking$eatOriginal(level, pos, state, player);
        BlockPos anchor = anchor(pos, state);
        BlockState before = level.getBlockState(anchor);
        int remaining = remaining(before);
        if (remaining <= 0) return nativeFood.cooking$eatOriginal(level, pos, state, player);
        ItemStack portion = preview(level, pos, state);
        if (!CulinaryConsumption.manages(portion)) return nativeFood.cooking$eatOriginal(level, pos, state, player);
        Boolean previous = BITING.get(); BITING.set(true);
        try {
            return ServingContext.call(portion, 1D / food.getMaxBites(), () -> CulinaryEffects.call(portion, () -> {
                InteractionResult result = nativeFood.cooking$eatOriginal(level, pos, state, player);
                if (result.consumesAction()) {
                    BlockState after = level.getBlockState(anchor);
                    if (after.getBlock() != before.getBlock() || remaining(after) != remaining - 1)
                        throw new IllegalStateException("KC successful bite did not debit one native portion");
                    PlacedFoodAdapters.completed(level, anchor, before);
                    if (player instanceof ServerPlayer real && !(real instanceof FakePlayer) && real.connection != null)
                        CulinaryConsumption.complete(real, portion);
                }
                return result;
            }));
        } finally {
            if (previous == null) BITING.remove(); else BITING.set(previous);
        }
    }
    /** Only 1x2 omits super.setPlacedBy; ordinary and 3x3 families reach Spice's Block hook. */
    public static void placedOneByTwo(Level level, BlockPos pos, BlockState state, ItemStack original) {
        if (enabled() && level instanceof ServerLevel && state.getBlock() instanceof FoodBiteOneByTwoBlock)
            PlacedFoodAdapters.placed(level, anchor(pos, state), state, original);
    }
}
