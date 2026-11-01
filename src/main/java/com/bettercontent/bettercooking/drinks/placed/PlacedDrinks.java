package com.bettercontent.bettercooking.drinks.placed;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.drinks.NativeDrinkRoutes;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.*;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.item.TeapotItem;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;

/** No intake, receipt construction, metadata reinterpretation, age clock or quantity multiplication. */
public final class PlacedDrinks {
    private PlacedDrinks() {}
    public static boolean supportedId(String id){return NativeDrinkRoutes.IDS.contains(id);}
    public static String itemId(ItemStack item){return String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem()));}
    public static String blockId(BlockState state){return String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));}
    public static boolean supported(ItemStack item){return !item.isEmpty()&&supportedId(itemId(item));}
    public static boolean known(BlockState state){return supportedId(blockId(state))&&(state.getBlock() instanceof TeacupBlock||state.getBlock() instanceof ClayPotMilkTeaBlock);}
    public static int count(BlockState state){
        if(!known(state))return 0;
        if(state.getBlock() instanceof TeacupBlock cups){
            int filled=state.getValue(cups.getTeaCountProperty()),total=state.getValue(cups.getCupCountProperty());
            if(filled<1||filled>4||filled>total||total>4)throw new IllegalStateException("Corrupt native tea block count");return filled;
        }return 1;
    }
    private static final class Use {
        final ServerLevel level;final BlockPos pos;final BlockState before;final CupQueue<EncodedCup> cups;final NativeDrinkBlockEntity owner;
        final Player player;final InteractionHand hand;final ItemStack held,addition;final boolean teapot;
        boolean delivered;
        Use(ServerLevel level,BlockPos pos,BlockState before,Player player,InteractionHand hand){
            this.level=level;this.pos=pos.immutable();this.before=before;this.player=player;this.hand=hand;
            if(!level.getBlockState(pos).equals(before))throw new IllegalStateException("Native drink use supplied stale block state");
            if(known(before)){owner=entity(level,pos);cups=owner.snapshot(before);}
            else {owner=null;if(level.getBlockEntity(pos) instanceof NativeDrinkBlockEntity)throw new IllegalStateException("Native cup BE beneath an empty-cup state");cups=CupQueue.empty();}
            held=player.getItemInHand(hand).copy();teapot=held.is(ModItems.TEAPOT.get());
            ItemStack source=teapot?TeapotItem.getPourOut(held):held;
            addition=supported(source)?source.copyWithCount(1):ItemStack.EMPTY;
            if(!addition.isEmpty()){
                EncodedCup encoded=new EncodedCup(addition);
                if(before.getBlock() instanceof EmptyCupBlock)CupCodec.save(itemId(addition),CupQueue.<EncodedCup>empty().append(encoded));
                else if(addition.is(before.getBlock().asItem())&&cups.size()<4)CupCodec.save(blockId(before),cups.append(encoded));
            }
        }
        boolean paidOne(){
            ItemStack after=player.getItemInHand(hand);
            return teapot?TeapotItem.getPourOut(held).getCount()-TeapotItem.getPourOut(after).getCount()==1:
                held.getCount()-after.getCount()==1;
        }
    }
    private static final ThreadLocal<Use> USE=new ThreadLocal<>();
    /** Read-only projection while native setBlock has changed count but the whole-call finally has not committed yet. */
    static CupQueue<EncodedCup> preview(NativeDrinkBlockEntity body,BlockState expected,CupQueue<EncodedCup> stored){
        Use use=USE.get();if(use==null||body.getLevel()!=use.level||!body.getBlockPos().equals(use.pos))return stored;
        if(use.owner!=null?(body!=use.owner||stored!=use.cups):body!=use.level.getBlockEntity(use.pos))return stored;
        int n=count(expected),before=use.cups.size();
        if(n==before+1&&!use.addition.isEmpty()&&use.addition.is(expected.getBlock().asItem()))return use.cups.append(new EncodedCup(use.addition));
        if(n==before-1&&use.delivered)return use.cups.tail();
        return stored;
    }
    /** A direct third-party Block.use bypass must fail BEFORE losing a managed pour/merge original. */
    public static void requireUseScope(BlockState before,Level world,BlockPos pos,Player player,InteractionHand hand){
        if(CookingMod.discoveryOnly()||world.isClientSide||hand!=InteractionHand.MAIN_HAND)return;
        boolean relevant=known(before);
        if(before.getBlock() instanceof EmptyCupBlock){
            ItemStack held=player.getItemInHand(hand);relevant=supported(held.is(ModItems.TEAPOT.get())?TeapotItem.getPourOut(held):held);
        }
        if(!relevant)return;Use use=USE.get();
        if(use==null||use.level!=world||!use.pos.equals(pos)||!use.before.equals(before)||use.player!=player)throw new IllegalStateException("Native drink use bypassed exact whole-call scope");
    }
    /** Entire actual native Block.use invocation is scoped, including failures/exceptions and nested uses. */
    public static InteractionResult use(BlockState before,Level world,BlockPos pos,Player player,InteractionHand hand,Supplier<InteractionResult> operation){
        if(CookingMod.discoveryOnly()||hand!=InteractionHand.MAIN_HAND||!(world instanceof ServerLevel level)||(!known(before)&&!(before.getBlock() instanceof EmptyCupBlock)))return operation.get();
        Use parent=USE.get();
        if(parent!=null&&parent.level==level&&parent.pos.equals(pos))throw new IllegalStateException("Reentrant native drink transaction at same location");
        Use active=new Use(level,pos,before,player,hand);USE.set(active);
        Throwable failed=null;
        try{return operation.get();}catch(RuntimeException|Error failure){failed=failure;throw failure;}
        finally{
            try{reconcile(active);}catch(RuntimeException|Error invalid){if(failed==null)throw invalid;failed.addSuppressed(invalid);}
            finally{if(parent==null)USE.remove();else USE.set(parent);}
        }
    }
    private static void reconcile(Use use){
        BlockState after=use.level.getBlockState(use.pos);int before=use.cups.size(),now=count(after);
        if(known(use.before)&&known(after)&&use.before.getBlock()!=after.getBlock())throw new IllegalStateException("Native drink changed variant without a new original");
        if(now==before){
            if(use.delivered)throw new IllegalStateException("Native cup delivered without committed block debit");
            if(now>0&&entity(use.level,use.pos).snapshot(after)!=use.cups)throw new IllegalStateException("Same-count native action replaced its original FIFO");return;
        }
        if(now==before+1&&!use.delivered){
            if(use.addition.isEmpty()||!use.addition.is(after.getBlock().asItem())||!use.paidOne())throw new IllegalStateException("Native drink added without one actual paid original");
            entity(use.level,use.pos).commit(after,use.cups.append(new EncodedCup(use.addition)));return;
        }
        if(now==before-1&&use.delivered){
            var remaining=use.cups.tail();if(now>0)entity(use.level,use.pos).commit(after,remaining);return;
        }
        throw new IllegalStateException("Native drink transition disagrees with exact FIFO: "+before+" -> "+now);
    }
    /** Called only at the exact filled-stack native delivery arguments, before native state debit. */
    public static ItemStack pickup(Level world,BlockPos pos,BlockState before,Player player,ItemStack nativeDefault){
        if(CookingMod.discoveryOnly()||world.isClientSide||!supported(nativeDefault))return nativeDefault;
        Use use=USE.get();if(use==null||use.level!=world||!use.pos.equals(pos)||!use.before.equals(before)||use.player!=player)throw new IllegalStateException("Native filled-cup pickup bypassed its exact scoped actual use");
        if(use.delivered||nativeDefault.getCount()!=1||!nativeDefault.is(use.before.getBlock().asItem()))throw new IllegalStateException("Duplicate/foreign native cup delivery");
        use.delivered=true;
        // UNKNOWN uses exactly native candidate; Root owns its conservative legacy policy. Never fabricate original NBT.
        return use.cups.head().map(EncodedCup::stack).orElseGet(()->nativeDefault.copyWithCount(1));
    }
    /** Before native placement can mutate the world/debit input: bound the full prospective count-one record. */
    public static void validatePlacement(Level world,ItemStack actual){
        if(CookingMod.discoveryOnly()||world.isClientSide||!supported(actual))return;
        CupCodec.save(itemId(actual),CupQueue.<EncodedCup>empty().append(new EncodedCup(actual.copyWithCount(1))));
    }
    public static void placed(Level world,BlockPos pos,BlockState committed,ItemStack actual){
        if(CookingMod.discoveryOnly()||!(world instanceof ServerLevel level)||!known(committed))return;
        if(level.getBlockState(pos).getBlock()!=committed.getBlock()||count(committed)!=1||!actual.is(committed.getBlock().asItem())||actual.isEmpty())throw new IllegalStateException("Native placement did not commit one actual filled cup");
        entity(level,pos).commit(committed,CupQueue.<EncodedCup>empty().append(new EncodedCup(actual.copyWithCount(1))));
    }
    private static NativeDrinkBlockEntity entity(ServerLevel level,BlockPos pos){
        if(!(level.getBlockEntity(pos) instanceof NativeDrinkBlockEntity cups))throw new IllegalStateException("Missing registered native cup BE");return cups;
    }
    public static List<ItemStack> drops(BlockState state,LootParams.Builder params,List<ItemStack> nativeDrops){
        if(CookingMod.discoveryOnly()||!known(state))return nativeDrops;
        var origin=params.getOptionalParameter(LootContextParams.ORIGIN);
        if(origin==null)throw new IllegalStateException("Native drink loot lacks actual origin");
        ServerLevel level=params.getLevel();BlockPos pos=BlockPos.containing(origin);
        var old=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if(old==null){
            if(!level.getBlockState(pos).equals(state))throw new IllegalStateException("Removed native cup loot omitted its captured BLOCK_ENTITY");
            old=level.getBlockEntity(pos);
        }
        if(!(old instanceof NativeDrinkBlockEntity body)||!body.getBlockPos().equals(pos)||body.getLevel()!=level)throw new IllegalStateException("Foreign/missing native cup loot BE");
        CupQueue<EncodedCup> cups=body.snapshot(state);
        int filled=0;for(ItemStack drop:nativeDrops)if(drop.is(state.getBlock().asItem()))filled=Math.addExact(filled,drop.getCount());
        if(filled<0||filled>cups.size())throw new IllegalStateException("Native loot minted more cups than the actual FIFO");
        int index=0;List<ItemStack> result=new ArrayList<>();
        for(ItemStack drop:nativeDrops){
            if(!drop.is(state.getBlock().asItem()))result.add(drop);
            else for(int n=0;n<drop.getCount();n++){
                var original=cups.entries().get(index++);result.add(original.isPresent()?original.get().stack():drop.copyWithCount(1));
            }
        }
        // Native explosion/loot conditions may destroy some or all cups. Preserve that exact native quantity/RNG; preview never advances FIFO.
        return List.copyOf(result);
    }
}
