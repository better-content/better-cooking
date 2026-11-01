package com.bettercontent.bettercooking.acquisition;

import com.bettercontent.betterbumblezonecrops.api.CultivarAcquisition;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.policy.PolicyRepository;
import com.github.ysbbbbbb.kaleidoscopecookery.block.crop.RiceCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit pinned-source aliases into BCC's existing definitions, not new planting/growth policy. */
public final class KaleidoscopeCropAuthority {
    private KaleidoscopeCropAuthority() {}
    private static ResourceLocation kc(String path) { return new ResourceLocation("kaleidoscope_cookery", path); }
    public static void installAliases() {
        if (CookingMod.discoveryOnly()) return;
        if (CultivarAcquisition.apiVersion() != 1) throw new IllegalStateException("Unsupported runtime Bumblezone cultivar acquisition API");
        if (!PolicyRepository.current().qualified()) throw new IllegalStateException("Cannot install acquisition authority for an unqualified policy");
        CultivarAcquisition.register(new ResourceLocation(CookingMod.MOD_ID,"kaleidoscope_crops"), List.of(
            new CultivarAcquisition.Binding("farmers_delight_tomato", Set.of(kc("tomato_seed")), Set.of(kc("tomato_crop")), Set.of(kc("tomato"))),
            new CultivarAcquisition.Binding("farmers_delight_cabbage", Set.of(kc("lettuce_seed")), Set.of(kc("lettuce_crop")), Set.of(kc("lettuce"))),
            new CultivarAcquisition.Binding("farmers_delight_rice", Set.of(kc("rice"),kc("wild_rice")), Set.of(kc("rice_crop")), Set.of(kc("rice"),kc("rice_panicle")))
        ), Map.of(kc("rice_crop"), KaleidoscopeCropAuthority::riceAnchor));
    }
    static CultivarAcquisition.HarvestPosition riceAnchor(BlockState state, BlockPos position) {
        if (!(state.getBlock() instanceof RiceCropBlock) || !state.hasProperty(RiceCropBlock.LOCATION))
            throw new IllegalStateException("Pinned KC rice LOCATION adapter received another native source");
        int location = state.getValue(RiceCropBlock.LOCATION);
        if (location < 0 || location > 2) throw new IllegalStateException("Pinned KC rice no longer has three locations");
        return new CultivarAcquisition.HarvestPosition(position.below(location), location == 0);
    }
}
