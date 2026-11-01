package com.bettercontent.bettercooking.drinks.placed;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Versioned, bounded full-stack FIFO codec. Known payload corruption never becomes UNKNOWN. */
public final class CupCodec {
    private CupCodec() {}
    public static void bounded(Tag tag){walk(tag,0,new int[]{0,0});}
    private static void walk(Tag tag,int depth,int[] nodes){
        if(tag==null||depth>32||++nodes[0]>16384)throw new IllegalStateException("Native cup NBT depth/node bound exceeded");
        if(tag instanceof CompoundTag compound){for(String key:compound.getAllKeys()){if(key.length()>512||(nodes[1]+=key.length())>528384)throw new IllegalStateException("Native cup NBT key bound exceeded");walk(compound.get(key),depth+1,nodes);}}
        else if(tag instanceof CollectionTag<?> sequence){if(sequence.size()>16384)throw new IllegalStateException("Native cup NBT collection bound exceeded");for(int i=0;i<sequence.size();i++)walk(sequence.get(i),depth+1,nodes);}
        else if(tag instanceof StringTag value&&(value.getAsString().length()>131072||(nodes[1]+=value.getAsString().length())>528384))throw new IllegalStateException("Native cup NBT string bound exceeded");
    }
    public static CompoundTag save(String block,CupQueue<EncodedCup> cups){
        if(!PlacedDrinks.supportedId(block)||cups.size()<1||block.equals("kaleidoscope_cookery:clay_pot_milk_tea")&&cups.size()!=1)throw new IllegalStateException("Invalid native cup block/count");
        var data=new CompoundTag();data.putInt("v",1);data.putString("block",block);var list=new ListTag();
        for(var original:cups.entries()){
            var entry=new CompoundTag();if(original.isPresent()){
                CompoundTag stack=original.get().save();entry.put("stack",stack);entry.putString("sha256",EncodedCup.fingerprint(stack));
            }else entry.putBoolean("unknown",true);list.add(entry);
        }data.put("queue",list);bounded(data);return data;
    }
    public static CupQueue<EncodedCup> load(CompoundTag data,String actualBlock){
        bounded(data);if(data.getAsString().length()>528384)throw new IllegalStateException("Native FIFO serialized bound exceeded");
        if(data.getAllKeys().size()!=3||!data.contains("v",Tag.TAG_INT)||data.getInt("v")!=1||!data.getString("block").equals(actualBlock)||!PlacedDrinks.supportedId(actualBlock)||!data.contains("queue",Tag.TAG_LIST))throw new IllegalStateException("Native cup schema/block mismatch");
        ListTag saved=(ListTag)data.get("queue");if(!saved.isEmpty()&&saved.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException("Wrong native cup list type");
        List<Optional<EncodedCup>> entries=new ArrayList<>();
        for(Tag raw:saved){var entry=(CompoundTag)raw;
            if(entry.getBoolean("unknown")){
                if(entry.getAllKeys().size()!=1||!entry.contains("unknown",Tag.TAG_BYTE)||entry.getByte("unknown")!=1)throw new IllegalStateException("Counterfeit UNKNOWN native cup");entries.add(Optional.empty());
            }else{
                if(entry.getAllKeys().size()!=2||!entry.contains("stack",Tag.TAG_COMPOUND)||!entry.contains("sha256",Tag.TAG_STRING))throw new IllegalStateException("Missing native original/integrity binding");
                CompoundTag stack=entry.getCompound("stack");if(!EncodedCup.fingerprint(stack).equals(entry.getString("sha256")))throw new IllegalStateException("Changed/missing native original metadata");
                ItemStack original=ItemStack.of(stack);if(!PlacedDrinks.itemId(original).equals(actualBlock))throw new IllegalStateException("Foreign native cup original");entries.add(Optional.of(new EncodedCup(original)));
            }
        }
        var queue=new CupQueue<>(entries);if(queue.size()<1||actualBlock.equals("kaleidoscope_cookery:clay_pot_milk_tea")&&queue.size()!=1)throw new IllegalStateException("Invalid native FIFO count");return queue;
    }
}
