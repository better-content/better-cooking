package com.bettercontent.bettercooking.acquisition;

import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.betterbumblezonecrops.api.CultivarAcquisition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.bettercontent.bettercooking.policy.PolicyRepository;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Used ONLY at audited new native crop-production calls, never inventories or global item drops. */
public final class NewCropOutputs {
    private NewCropOutputs() {}
    public static ItemStack canonicalNewOutput(ItemStack original) {
        // Inert discovery must not even require an installed policy/alias provider.
        if (CookingMod.discoveryOnly() || original.isEmpty()) return original;
        ResourceLocation source = ForgeRegistries.ITEMS.getKey(original.getItem());
        if (source == null) throw new IllegalArgumentException("Unregistered new crop output");
        var plan = CropAliasPlan.plan(source.toString(), original.getCount(), PolicyRepository::productionCropAlias);
        return plan.isEmpty() ? original : canonicalCopy(original, new ResourceLocation(plan.get().canonicalId()));
    }
    /** Full native invocation context; seed authority is evaluated only for NEW output, after identity canonicalization. */
    public static ItemStack canonicalNewOutput(Level level, BlockPos position, ItemStack original) {
        if (CookingMod.discoveryOnly() || original.isEmpty()) return original;
        java.util.Objects.requireNonNull(level); java.util.Objects.requireNonNull(position);
        return CultivarAcquisition.restrictNewOutput(level.dimension().location(), canonicalNewOutput(original));
    }
    static ItemStack canonicalCopy(ItemStack original, ResourceLocation canonical) {
        if (!ForgeRegistries.ITEMS.containsKey(canonical) || ForgeRegistries.ITEMS.getValue(canonical) == Items.AIR)
            throw new IllegalStateException("Missing/non-item canonical crop acquisition target " + canonical);
        // Supported serialization includes ordinary meaningful item NBT and compatible ForgeCaps.
        // Change ONLY identity; the paid/origin/quality/age payload remains a detached deep copy.
        CompoundTag serialized = original.save(new CompoundTag());
        serialized.putString("id", canonical.toString());
        ItemStack result = ItemStack.of(serialized);
        result.setCount(original.getCount()); // Also preserve non-byte-sized test/plugin stack counts.
        if (result.isEmpty() && !original.isEmpty()) throw new IllegalStateException("Canonical crop reconstruction lost output " + canonical);
        return result;
    }
}
