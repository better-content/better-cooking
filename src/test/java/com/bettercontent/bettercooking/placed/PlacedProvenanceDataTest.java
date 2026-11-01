package com.bettercontent.bettercooking.placed;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacedProvenanceDataTest {
    private static final BlockPos POS = new BlockPos(4, 72, -8);
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError boundary) {
            // Same narrow plain-JVM boundary as Heat's existing ItemStack tests: vanilla
            // registries are ready, but Forge's event constructor needs the native transformer.
            boolean known = false;
            for (Throwable cause = boundary; cause != null; cause = cause.getCause())
                if (cause instanceof NoSuchMethodException && cause.getMessage() != null
                        && cause.getMessage().startsWith("net.minecraftforge.network.NetworkEvent")) known = true;
            if (!known) throw boundary;
        }
    }
    private ItemStack original() {
        ItemStack stack = new ItemStack(Items.CAKE, 5);
        stack.getOrCreateTag().putInt("kaleidoscope_cookery:quality", 3);
        CompoundTag serving = new CompoundTag(); serving.putDouble("sugar", .03);
        CompoundTag nutrients = new CompoundTag(); nutrients.putDouble("grains", .08); serving.put("nutrients", nutrients);
        stack.getOrCreateTag().put("better_cooking:serving", serving);
        CompoundTag heat = new CompoundTag(); heat.putDouble("decay", .8); heat.putLong("last_time", 1234);
        stack.getOrCreateTag().put("better_industrial_heat_food", heat);
        return stack;
    }
    @Test void wholeOriginalNbtAndOldClockSurviveRealSavedDataRoundtrip() {
        var data = new PlacedProvenanceData(); var source = original(); var before = source.copy();
        data.put(POS, Blocks.CAKE.defaultBlockState(), source, 7);
        source.getOrCreateTag().putInt("kaleidoscope_cookery:quality", 0);
        var loaded = PlacedProvenanceData.load(data.save(new CompoundTag()));
        var restored = loaded.original(POS, Blocks.CAKE.defaultBlockState(), 7).orElseThrow();
        assertEquals(1, restored.getCount()); assertEquals(before.getTag(), restored.getTag());
        assertEquals(1234L, restored.getTag().getCompound("better_industrial_heat_food").getLong("last_time"));
    }
    @Test void previewsAreCopiesAndDoNotDebitOrDirtySavedState() {
        var data = new PlacedProvenanceData(); data.put(POS, Blocks.CAKE.defaultBlockState(), original(), 7);
        data.setDirty(false); CompoundTag before = data.save(new CompoundTag());
        var preview = data.original(POS, Blocks.CAKE.defaultBlockState(), 7).orElseThrow();
        preview.getOrCreateTag().putInt("kaleidoscope_cookery:quality", 0);
        assertEquals(before, data.save(new CompoundTag())); assertFalse(data.isDirty());
        assertEquals(3, data.original(POS, Blocks.CAKE.defaultBlockState(), 7).orElseThrow().getTag().getInt("kaleidoscope_cookery:quality"));
    }
    @Test void onlyMatchingNativeCommitDebitsAndDuplicateBeforeIsNoop() {
        var data = new PlacedProvenanceData(); data.put(POS, Blocks.CAKE.defaultBlockState(), original(), 7);
        data.complete(POS, Blocks.CAKE.defaultBlockState(), 7);
        data.setDirty(false); data.complete(POS, Blocks.CAKE.defaultBlockState(), 7);
        assertFalse(data.isDirty()); assertTrue(data.original(POS, Blocks.CAKE.defaultBlockState(), 6).isPresent());
        for (int remaining = 6; remaining >= 1; remaining--) data.complete(POS, Blocks.CAKE.defaultBlockState(), remaining);
        data.complete(POS, Blocks.CAKE.defaultBlockState(), 1);
        assertTrue(data.original(POS, Blocks.CAKE.defaultBlockState(), 1).isEmpty());
    }
    @Test void nativeMismatchRefusesWithoutManufacturingFreshState() {
        var data = new PlacedProvenanceData(); data.put(POS, Blocks.CAKE.defaultBlockState(), original(), 7);
        assertThrows(IllegalStateException.class, () -> data.original(POS, Blocks.CAKE.defaultBlockState(), 5));
        assertThrows(IllegalStateException.class, () -> data.original(POS, Blocks.STONE.defaultBlockState(), 7));
        assertThrows(IllegalStateException.class, () -> data.complete(POS, Blocks.CAKE.defaultBlockState(), 3));
        assertTrue(data.original(POS, Blocks.CAKE.defaultBlockState(), 7).isPresent());
        data.clear(POS); assertTrue(data.original(POS, Blocks.CAKE.defaultBlockState(), 7).isEmpty());
    }
    @Test void corruptSchemaRowsListsAndDuplicatePositionsRefuse() {
        assertThrows(IllegalArgumentException.class, () -> PlacedProvenanceData.load(new CompoundTag()));
        var data = new PlacedProvenanceData(); data.put(POS, Blocks.CAKE.defaultBlockState(), original(), 7);
        CompoundTag duplicate = data.save(new CompoundTag());
        duplicate.getList("entries", Tag.TAG_COMPOUND).add(duplicate.getList("entries", Tag.TAG_COMPOUND).getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> PlacedProvenanceData.load(duplicate));
        CompoundTag invalid = data.save(new CompoundTag()); invalid.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putInt("remaining", 0);
        assertThrows(IllegalArgumentException.class, () -> PlacedProvenanceData.load(invalid));
        CompoundTag wrongList = new CompoundTag(); wrongList.putInt("v", 1); ListTag strings = new ListTag(); strings.add(StringTag.valueOf("bad")); wrongList.put("entries", strings);
        assertThrows(IllegalArgumentException.class, () -> PlacedProvenanceData.load(wrongList));
    }
    @Test void excessiveItemPayloadAndDepthRefuseBeforeAdmission() {
        var data = new PlacedProvenanceData(); ItemStack huge = new ItemStack(Items.CAKE);
        huge.getOrCreateTag().putByteArray("oversized", new byte[65537]);
        assertThrows(IllegalArgumentException.class, () -> data.put(POS, Blocks.CAKE.defaultBlockState(), huge, 7));
        ItemStack deep = new ItemStack(Items.CAKE); CompoundTag cursor = deep.getOrCreateTag();
        for (int i = 0; i < 34; i++) { CompoundTag next = new CompoundTag(); cursor.put("child", next); cursor = next; }
        assertThrows(IllegalArgumentException.class, () -> data.put(POS, Blocks.CAKE.defaultBlockState(), deep, 7));
        assertTrue(data.original(POS, Blocks.CAKE.defaultBlockState(), 7).isEmpty());
    }
    @Test void purePortionTransitionsCoverAllNativeCounts() {
        for (int count = 1; count <= 64; count++) {
            assertEquals(count - 1, PortionProgress.complete(count, count));
            if (count > 1) assertEquals(count - 1, PortionProgress.complete(count - 1, count));
        }
        assertThrows(IllegalArgumentException.class, () -> PortionProgress.complete(0, 1));
        assertThrows(IllegalArgumentException.class, () -> PortionProgress.complete(1, 65));
    }
    public static void main(String[] args) {
        bootstrap(); var tests = new PlacedProvenanceDataTest();
        tests.wholeOriginalNbtAndOldClockSurviveRealSavedDataRoundtrip();
        tests.previewsAreCopiesAndDoNotDebitOrDirtySavedState();
        tests.onlyMatchingNativeCommitDebitsAndDuplicateBeforeIsNoop();
        tests.nativeMismatchRefusesWithoutManufacturingFreshState();
        tests.corruptSchemaRowsListsAndDuplicatePositionsRefuse();
        tests.excessiveItemPayloadAndDepthRefuseBeforeAdmission();
        tests.purePortionTransitionsCoverAllNativeCounts();
        System.out.println("PlacedProvenanceDataTest: 7 PASS (Minecraft bootstrap JVM; not native interaction acceptance)");
    }
}
