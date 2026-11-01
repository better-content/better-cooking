package com.bettercontent.bettercooking.drinks.placed;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.drinks.NativeDrinkRoutes;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** Root calls register(bus) in its mod constructor, before BE registration. Does not register/replace blocks. */
public final class NativeDrinkEntities {
    private NativeDrinkEntities() {}
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,CookingMod.MOD_ID);
    public static final RegistryObject<BlockEntityType<NativeDrinkBlockEntity>> CUPS=TYPES.register("native_drink_cups",()->{
        Block[] blocks=NativeDrinkRoutes.IDS.stream().sorted().map(id->{
            Block block=Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)),"Missing pinned native drink block "+id);
            if(!(block instanceof EntityBlock))throw new IllegalStateException("Native drink EntityBlock mixin not applied: "+id);return block;
        }).toArray(Block[]::new);
        if(blocks.length!=9)throw new IllegalStateException("Native drink block roster drift");
        return BlockEntityType.Builder.of(NativeDrinkBlockEntity::new,blocks).build(null);
    });
    public static void register(IEventBus bus){TYPES.register(bus);}
}
