package com.bettercontent.bettercooking.cutting;

import com.google.gson.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CuttingPlanTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError boundary) {
            boolean known = false;
            for (Throwable cause = boundary; cause != null; cause = cause.getCause())
                if (cause instanceof NoSuchMethodException && cause.getMessage() != null && cause.getMessage().startsWith("net.minecraftforge.network.NetworkEvent")) known = true;
            if (!known) throw boundary; // Known plain-JVM Forge constructor boundary ONLY, never production suppression.
        }
        // Perform the real public vanilla Ingredient codec registration normally provided by Forge startup.
        var vanilla = net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE;
        if (net.minecraftforge.common.crafting.CraftingHelper.getID(vanilla) == null)
            net.minecraftforge.common.crafting.CraftingHelper.register(ResourceLocation.tryParse("minecraft:item"), vanilla);
    }
    private CuttingPlan plan() {
        return new CuttingPlan(Ingredient.of(Items.BEEF), Ingredient.of(Items.IRON_SWORD), List.of(
                new CuttingPlan.Output(new ItemStack(Items.WHEAT_SEEDS, 3), 1),
                new CuttingPlan.Output(new ItemStack(Items.BEEF, 4), 0),
                new CuttingPlan.Output(new ItemStack(Items.COOKED_BEEF, 2), .2)), 1, null, ResourceLocation.tryParse("test:cut"));
    }
    @Test void orderedCountsChanceBoundsAndDefensiveCopies() {
        var plan = plan(); var first = plan.preview(); first.get(0).setCount(1);
        assertEquals(3, plan.preview().get(0).getCount());
        assertEquals(Items.WHEAT_SEEDS, plan.outputs().get(0).stack().getItem()); // First need not be edible.
        assertEquals(.2, plan.outputs().get(2).chance());
        assertThrows(IllegalArgumentException.class, () -> new CuttingPlan.Output(new ItemStack(Items.BEEF), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new CuttingPlan.Output(new ItemStack(Items.BEEF), -1));
        assertThrows(IllegalArgumentException.class, () -> new CuttingPlan.Output(new ItemStack(Items.BEEF, 65), 1));
        assertThrows(IllegalArgumentException.class, () -> plan.selected(0)); // Guaranteed seeds cannot disappear.
        assertThrows(IllegalArgumentException.class, () -> plan.selected(3)); // Zero-chance food cannot appear.
    }
    @Test void actualRandomSourceMakesOnlyOneProbabilityDrawInAuthoredOrder() {
        RandomSource actual = RandomSource.create(4321), expected = RandomSource.create(4321);
        long mask = plan().select(actual); long expectedMask = expected.nextFloat() < .2 ? 5 : 1;
        assertEquals(expectedMask, mask); assertEquals(expected.nextLong(), actual.nextLong());
    }
    @Test void jsonAndNetworkRoundtripPreserveCountsChancesAndOptionalNullModel() {
        CuttingPlan original = plan(); JsonObject json = original.json();
        json.add("conditions", JsonParser.parseString("[{\"type\":\"forge:mod_loaded\",\"modid\":\"snowyspirit\"}]"));
        var parsed = CuttingPlan.parse(json); assertNull(parsed.model()); assertEquals(original.fingerprint(), parsed.fingerprint());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { original.write(buffer); assertEquals(original.fingerprint(), CuttingPlan.read(buffer).fingerprint()); assertEquals(0, buffer.readableBytes()); }
        finally { buffer.release(); }
        json.addProperty("unsupported_bonus", .2); assertThrows(JsonParseException.class, () -> CuttingPlan.parse(json));
    }
    @Test void selectedPaidTransactionPersistsAndNeverRerollsAfterCallbackBoundary() {
        ItemStack paid = new ItemStack(Items.BEEF); paid.getOrCreateTag().putInt("kaleidoscope_cookery:quality", 3);
        CompoundTag age = new CompoundTag(); age.putDouble("decay", .8); age.putLong("last_time", 1234);
        paid.getOrCreateTag().put("better_industrial_heat_food", age);
        var session = new CuttingSession(ResourceLocation.tryParse("test:cut"), plan(), paid);
        AtomicInteger commits = new AtomicInteger(); session.select(RandomSource.create(9), commits::incrementAndGet);
        CompoundTag saved = session.save(); var loaded = CuttingSession.load(saved);
        loaded.validateNative(paid, 1, 1); assertEquals(1, commits.get());
        RandomSource actual = RandomSource.create(77), expected = RandomSource.create(77);
        loaded.select(actual, commits::incrementAndGet);
        assertEquals(expected.nextLong(), actual.nextLong()); assertEquals(1, commits.get()); assertEquals(saved, loaded.save());
        assertEquals(.8, loaded.save().getCompound("paid").getCompound("tag").getCompound("better_industrial_heat_food").getDouble("decay"));
        assertThrows(IllegalStateException.class, () -> loaded.validateNative(paid, 1, 0));
    }
    @Test void sourceFingerprintCursorAndCommittedChanceCorruptionRefused() {
        ItemStack paid = new ItemStack(Items.BEEF);
        var session = new CuttingSession(ResourceLocation.tryParse("test:cut"), plan(), paid);
        CompoundTag sourceChanged = session.save(); sourceChanged.getCompound("paid").putByte("Count", (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> CuttingSession.load(sourceChanged));
        CompoundTag badCursor = session.save(); badCursor.putInt("cursor", 1);
        assertThrows(IllegalArgumentException.class, () -> CuttingSession.load(badCursor));
        CompoundTag missingPhase = session.save(); missingPhase.remove("selected");
        assertThrows(IllegalArgumentException.class, () -> CuttingSession.load(missingPhase));
        ItemStack deep = new ItemStack(Items.BEEF); CompoundTag root = new CompoundTag(), child = root;
        for (int i = 0; i < 33; i++) { CompoundTag next = new CompoundTag(); child.put("child", next); child = next; }
        deep.setTag(root); assertThrows(IllegalArgumentException.class, () -> new CuttingSession(ResourceLocation.tryParse("test:cut"), plan(), deep));
        ItemStack oversized = new ItemStack(Items.BEEF); oversized.getOrCreateTag().putString("large", "x".repeat(65536));
        assertThrows(IllegalArgumentException.class, () -> CuttingPlan.requireBounded(oversized));
        session.select(RandomSource.create(5), () -> {});
        CompoundTag lostGuaranteed = session.save(); lostGuaranteed.putLong("selection_mask", 0);
        assertThrows(IllegalArgumentException.class, () -> CuttingSession.load(lostGuaranteed));
        CompoundTag outputChanged = session.save(); outputChanged.putString("outputs_fingerprint", "forged");
        assertThrows(IllegalArgumentException.class, () -> CuttingSession.load(outputChanged));
    }
    public static void main(String[] args) {
        bootstrap(); var test = new CuttingPlanTest(); test.orderedCountsChanceBoundsAndDefensiveCopies();
        test.actualRandomSourceMakesOnlyOneProbabilityDrawInAuthoredOrder(); test.jsonAndNetworkRoundtripPreserveCountsChancesAndOptionalNullModel();
        test.selectedPaidTransactionPersistsAndNeverRerollsAfterCallbackBoundary(); test.sourceFingerprintCursorAndCommittedChanceCorruptionRefused();
        System.out.println("CuttingPlanTest: 5 PASS (real RandomSource, codecs and persisted paid transaction; not native acceptance)");
    }
}
