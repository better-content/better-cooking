package com.bettercontent.bettercooking.drinks.placed;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One bounded FIFO on the existing block. No ticker/render shape change. Detached old BE remains a valid loot source. */
public final class NativeDrinkBlockEntity extends BlockEntity {
    public static final String KEY="better_cooking:native_cup_fifo";
    private final String binding;
    private CupQueue<EncodedCup> cups;
    private Throwable corrupt;
    private CompoundTag poison;
    public NativeDrinkBlockEntity(BlockPos pos,BlockState state){
        super(NativeDrinkEntities.CUPS.get(),pos,state);binding=PlacedDrinks.blockId(state);
        // Existing old chunks have no BE tag: the real native lazy factory initializes only explicit UNKNOWNs.
        cups=CupQueue.legacy(PlacedDrinks.count(state));
    }
    private void healthy(){if(corrupt!=null)throw new IllegalStateException("Corrupt native cup BE must not become legacy defaults",corrupt);}
    private void validate(BlockState state,CupQueue<EncodedCup> queue){
        healthy();if(!binding.equals(PlacedDrinks.blockId(state)))throw new IllegalStateException("Native cup BE block binding mismatch");
        queue.requireCount(PlacedDrinks.count(state));
        for(var original:queue.entries())if(original.isPresent()&&!original.get().stack().is(state.getBlock().asItem()))throw new IllegalStateException("Foreign native original on cup BE");
    }
    public CupQueue<EncodedCup> snapshot(BlockState expected){healthy();var view=PlacedDrinks.preview(this,expected,cups);validate(expected,view);return view;}
    public void commit(BlockState committed,CupQueue<EncodedCup> queue){
        validate(committed,queue);CupCodec.save(binding,queue);cups=queue;
        // The native state transition already handles packets/neighbours. Persist without introducing extra comparator cadence.
        if(level!=null)level.getChunkAt(worldPosition).setUnsaved(true);
    }
    @Override public void load(CompoundTag tag){
        try{
            super.load(tag);
            if(!tag.contains(KEY,Tag.TAG_COMPOUND))throw new IllegalStateException("Missing saved native cup FIFO");
            cups=CupCodec.load(tag.getCompound(KEY),binding);corrupt=null;poison=null;
            // Do not validate mutable state count until an operation boundary: native use may change state before finally-commit.
        }catch(Throwable invalid){poison(invalid,tag.get(KEY));}
        // Never throw a queue decoder error here: vanilla catches load failures and would otherwise manufacture a fresh BE.
    }
    /** Vanilla's empty client update tag is not a disk record. No server receipt is inferred from client prediction. */
    @Override public void handleUpdateTag(CompoundTag tag){
        if(level!=null&&level.isClientSide){cups=CupQueue.legacy(PlacedDrinks.count(getBlockState()));corrupt=null;}
        else load(tag);
    }
    private void poison(Throwable invalid,Tag rejected){
        corrupt=invalid;poison=new CompoundTag();poison.putInt("v",-1);poison.putString("block",binding);poison.putBoolean("poison",true);
        try{
            if(rejected!=null){
                CupCodec.bounded(rejected);
                if(rejected instanceof CompoundTag previous&&previous.getInt("v")==-1&&previous.getBoolean("poison")&&previous.getString("block").equals(binding)&&previous.getAsString().length()<=528384){poison=previous.copy();return;}
                var retained=poison.copy();retained.put("quarantine",rejected.copy());CupCodec.bounded(retained);if(retained.getAsString().length()<=528384)poison=retained;
            }
        }catch(Throwable unsafe){/* bounded poison sentinel remains permanently invalid */}
    }
    /** Saved type mismatch must not go through vanilla's null -> fresh UNKNOWN fallback. */
    public static NativeDrinkBlockEntity rejectedSavedType(BlockPos pos,BlockState state,CompoundTag rejected){
        var body=new NativeDrinkBlockEntity(pos,state);body.poison(new IllegalStateException("Foreign/missing saved native cup BE type"),rejected.get(KEY));return body;
    }
    @Override protected void saveAdditional(CompoundTag tag){
        // Chunk saving catches BE exceptions and omits the tag: persist poison instead, never throw/default it away.
        if(corrupt!=null){tag.put(KEY,poison.copy());return;}
        CompoundTag serialized=null;
        try{serialized=CupCodec.save(binding,cups);super.saveAdditional(tag);tag.put(KEY,serialized);}
        catch(Throwable failed){poison(failed,serialized);tag.put(KEY,poison.copy());}
    }
}
