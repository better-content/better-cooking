package com.bettercontent.bettercooking.mixin;
import com.bettercontent.bettercooking.drinks.placed.NativeDrinkBlockEntity;
import com.bettercontent.bettercooking.drinks.placed.PlacedDrinks;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.TeacupBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.ClayPotMilkTeaBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/** Adds only the vanilla BE interface/factory. Existing block IDs, state properties, MODEL rendering and ticking stay native. */
@Mixin({TeacupBlock.class,ClayPotMilkTeaBlock.class})
public abstract class NativePlacedDrinkEntityMixin implements EntityBlock {
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return PlacedDrinks.known(state)?new NativeDrinkBlockEntity(pos,state):null;
    }
}
