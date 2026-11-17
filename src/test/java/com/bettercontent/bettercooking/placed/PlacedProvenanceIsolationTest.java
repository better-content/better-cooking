package com.bettercontent.bettercooking.placed;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Source-owned SavedData stress fixture; 32 positions are NOT 32 connected players. */
class PlacedProvenanceIsolationTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError boundary) {
            boolean known = false;
            for (Throwable cause = boundary; cause != null; cause = cause.getCause())
                if (cause instanceof NoSuchMethodException && cause.getMessage() != null
                        && cause.getMessage().startsWith("net.minecraftforge.network.NetworkEvent")) known = true;
            if (!known) throw boundary;
        }
    }

    @Test void independentAgedPlacedOriginalsSurviveSaveReloadAndOnePositionDebit() {
        var state = Blocks.CAKE.defaultBlockState();
        var data = new PlacedProvenanceData();
        for (int i = 0; i < 32; i++) {
            var item = new ItemStack(Items.CAKE);
            var heat = new CompoundTag();
            heat.putDouble("decay", (i + 1) / 64D);
            heat.putLong("last_time", 1000L + i);
            item.getOrCreateTag().put("better_industrial_heat_food", heat);
            data.put(new BlockPos(i * 16, 70, 0), state, item, 7);
        }
        var restored = PlacedProvenanceData.load(data.save(new CompoundTag()));
        var first = new BlockPos(0, 70, 0);
        restored.complete(first, state, 7);
        assertTrue(restored.original(first, state, 6).isPresent());
        var reloaded = PlacedProvenanceData.load(restored.save(new CompoundTag()));
        for (int i = 0; i < 32; i++) {
            var pos = new BlockPos(i * 16, 70, 0);
            var original = reloaded.original(pos, state, i == 0 ? 6 : 7).orElseThrow();
            var heat = original.getTag().getCompound("better_industrial_heat_food");
            assertEquals((i + 1) / 64D, heat.getDouble("decay"), 0D);
            assertEquals(1000L + i, heat.getLong("last_time"));
            if (i != 0) assertThrows(IllegalStateException.class, () -> reloaded.original(pos, state, 6));
        }
        reloaded.clear(first);
        assertTrue(reloaded.original(first, state, 6).isEmpty());
        assertTrue(reloaded.original(new BlockPos(16, 70, 0), state, 7).isPresent());
    }
}
