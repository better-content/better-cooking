package com.bettercontent.bettercooking.mixin;

import com.bettercontent.bettercooking.CookingItems;
import com.bettercontent.bettercooking.CookingMod;
import com.bettercontent.bettercooking.recipe.PreparedStockpotRecipe;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotAccess;
import com.bettercontent.bettercooking.stockpot.PreparedStockpotStateAccess;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotContainer;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModSoupBases;
import com.github.ysbbbbbb.kaleidoscopecookery.util.ItemUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** No water inventory is created: sealed synthetic-base ownership is persisted independently of inputs. */
@Mixin(value=StockpotBlockEntity.class,remap=false)
public abstract class PreparedStockpotBlockEntityMixin implements PreparedStockpotStateAccess {
    @Override public boolean cooking$isPreparedStockpotSealed() { return cooking$sealed; }
    @Unique private static final String COOKING_SEALED="better_cooking:prepared_stockpot";
    @Unique private boolean cooking$sealed;
    @Unique private ResourceLocation cooking$recipe;
    @Unique private String cooking$binding="";
    @Unique private ItemStack cooking$witness=ItemStack.EMPTY;
    @Shadow(remap=false) private ResourceLocation soupBaseId;
    @Shadow(remap=false) private ResourceLocation recipeId;
    @Invoker(value="applyRecipe",remap=false)
    protected abstract void cooking$apply(Level level, StockpotContainer inputs, StockpotRecipe recipe);
    @Unique private StockpotBlockEntity cooking$self() { return (StockpotBlockEntity)(Object)this; }
    @Unique private boolean cooking$batch(ItemStack stack) { return stack.is(CookingItems.PREPARED_BATCH.get()); }
    @Unique private boolean cooking$hasBatch() {
        for(ItemStack stack:cooking$self().getInputs()) if(cooking$batch(stack)) return true;
        return false;
    }
    @Unique private StockpotRecipe cooking$current(Level level) {
        if(!cooking$sealed||cooking$recipe==null||cooking$witness.getCount()!=1) return null;
        var current=level.getRecipeManager().byKey(cooking$recipe).orElse(null);
        if(!(current instanceof StockpotRecipe nativeRecipe)) return null;
        PreparedStockpotRecipe descriptor=((PreparedStockpotAccess)(Object)nativeRecipe).cooking$preparedStockpot();
        return descriptor!=null && descriptor.binding().equals(cooking$binding)
            && descriptor.accepts(cooking$witness,level)?nativeRecipe:null;
    }
    @Override public List<ItemStack> cooking$preparedStockpotBreakDrops() {
        if(!cooking$sealed) throw new IllegalStateException("Not an owned prepared stockpot");
        StockpotBlockEntity self=cooking$self(); Level level=self.getLevel();
        StockpotRecipe current=level==null?null:cooking$current(level);
        if(current==null) throw new IllegalStateException("Prepared stockpot break: missing or changed admitted recipe/batch definition");
        int status=self.getStatus();
        if(status==IStockpot.PUT_INGREDIENT||status==IStockpot.COOKING) {
            if(status==IStockpot.PUT_INGREDIENT&&(!self.getResult().isEmpty()||self.getTakeoutCount()!=0)
                ||status==IStockpot.COOKING&&(self.getResult().getItem()!=current.result().getItem()
                    ||self.getResult().getCount()!=current.result().getCount()||self.getTakeoutCount()!=current.result().getCount()))
                throw new IllegalStateException("Prepared stockpot break: inconsistent unfinished result/takeout phase");
            if(!current.matches(self.getContainer(),level)) throw new IllegalStateException("Prepared stockpot break: physical committed batch absent/invalid");
            for(ItemStack actual:self.getInputs()) if(!actual.isEmpty()) return List.of(actual.copy());
            throw new IllegalStateException("Prepared stockpot break: no physical input");
        }
        if(status!=IStockpot.FINISHED||!self.isEmpty()||!current.getId().equals(recipeId))
            throw new IllegalStateException("Prepared stockpot break: corrupt native phase");
        ItemStack result=self.getResult(); int remaining=self.getTakeoutCount();
        if(result.isEmpty()||result.getItem()!=current.result().getItem()||result.getCount()!=current.result().getCount()
            ||remaining<1||remaining>current.result().getCount()||remaining>StockpotBlockEntity.MAX_TAKEOUT_COUNT)
            throw new IllegalStateException("Prepared stockpot break: invalid materialized result/remaining quantity");
        if(!current.carrier().isEmpty())
            throw new com.bettercontent.bettercooking.stockpot.PreparedStockpotBreakPolicy.UnpaidCarrier();
        java.util.ArrayList<ItemStack> drops=new java.util.ArrayList<>();
        for(int i=0;i<remaining;i++) drops.add(result.copyWithCount(1));
        return List.copyOf(drops);
    }
    @Unique private void cooking$reset() {
        cooking$sealed=false; cooking$recipe=null; cooking$binding=""; cooking$witness=ItemStack.EMPTY;
        cooking$self().setStatus(IStockpot.PUT_SOUP_BASE); soupBaseId=ModSoupBases.WATER;
        cooking$self().refresh();
    }
    @Inject(method="addIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$insert(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if(cooking$sealed) { cir.setReturnValue(false); return; }
        if(CookingMod.discoveryOnly()) return;
        StockpotBlockEntity self=cooking$self();
        if(!cooking$batch(stack)) return;
        cir.setReturnValue(false);
        if(level.isClientSide || self.getLevel()!=level || self.isRemoved() || self.hasLid()
            || self.getStatus()!=IStockpot.PUT_SOUP_BASE || !self.isEmpty() || !self.getResult().isEmpty()) return;
        StockpotRecipe accepted=null;
        ItemStack unit=stack.copyWithCount(1);
        for(StockpotRecipe candidate:level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)) {
            PreparedStockpotRecipe descriptor=((PreparedStockpotAccess)(Object)candidate).cooking$preparedStockpot();
            if(descriptor==null || !descriptor.accepts(unit,level)) continue;
            if(accepted!=null) return; // Ambiguous route bindings never choose by iteration order.
            accepted=candidate;
        }
        if(accepted==null) return;
        // All validation precedes split; no actor check/work grant: Root committed work is the authority.
        cooking$recipe=accepted.getId(); cooking$witness=unit; cooking$sealed=true;
        cooking$binding=((PreparedStockpotAccess)(Object)accepted).cooking$preparedStockpot().binding();
        self.getInputs().set(0,stack.split(1)); soupBaseId=ModSoupBases.WATER;
        self.setStatus(IStockpot.PUT_INGREDIENT); self.refresh(); cir.setReturnValue(true);
    }
    @Inject(method="addAllIngredients(Ljava/util/List;Lnet/minecraft/world/entity/LivingEntity;)V",
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$noBulk(List<ItemStack> inputs, LivingEntity user, CallbackInfo ci) {
        if(cooking$sealed||!CookingMod.discoveryOnly()&&inputs.stream().anyMatch(this::cooking$batch)) ci.cancel();
    }
    @Inject(method={"addSoupBase(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
        "removeSoupBase(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"},
        at=@At("HEAD"),cancellable=true,require=2,allow=2,remap=false)
    private void cooking$noFluid(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if(cooking$sealed||!CookingMod.discoveryOnly()&&(cooking$batch(stack)||cooking$hasBatch())) cir.setReturnValue(false);
    }
    @Inject(method="removeIngredient(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Z",
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$cancel(Level level, LivingEntity user, CallbackInfoReturnable<Boolean> cir) {
        if(!cooking$sealed) return;
        cir.setReturnValue(false); StockpotBlockEntity self=cooking$self();
        if(level.isClientSide||user==null||self.hasLid()||self.getStatus()!=IStockpot.PUT_INGREDIENT) return;
        ItemStack refund=ItemStack.EMPTY;
        for(ItemStack input:self.getInputs()) {
            if(input.isEmpty()) continue;
            if(!refund.isEmpty()||!cooking$batch(input)||input.getCount()!=1) return;
            refund=input.copy();
        }
        if(refund.isEmpty()) { cooking$reset(); return; }
        self.getInputs().clear(); cooking$reset(); // Empty/base reset BEFORE returning the paid batch.
        ItemUtils.getItemToLivingEntity(user,refund); cir.setReturnValue(true);
    }
    @Inject(method="tick(Lnet/minecraft/world/level/Level;)V",at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$validateTick(Level level, CallbackInfo ci) {
        if(!cooking$sealed) return;
        StockpotRecipe current=cooking$current(level);
        if(current==null || cooking$self().getStatus()==IStockpot.PUT_INGREDIENT&&!current.matches(cooking$self().getContainer(),level)) ci.cancel();
    }
    @Inject(method="setRecipe(Lnet/minecraft/world/level/Level;)V",at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$select(Level level, CallbackInfo ci) {
        if(!cooking$sealed) return;
        ci.cancel(); StockpotRecipe current=cooking$current(level);
        if(current!=null&&current.matches(cooking$self().getContainer(),level)) cooking$apply(level,cooking$self().getContainer(),current);
    }
    @Inject(method="takeOutProduct(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
        at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void cooking$validateTakeout(Level level, LivingEntity user, ItemStack carrier, CallbackInfoReturnable<Boolean> cir) {
        if(!cooking$sealed) return;
        StockpotRecipe current=cooking$current(level); StockpotBlockEntity self=cooking$self();
        if(current==null||!current.getId().equals(recipeId)||self.getResult().getItem()!=current.result().getItem()
            ||self.getResult().getCount()!=current.result().getCount()||self.getTakeoutCount()>current.result().getCount()) cir.setReturnValue(false);
    }
    @Inject(method="takeOutProduct(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
        at=@At("RETURN"),require=1,remap=false)
    private void cooking$drained(Level level, LivingEntity user, ItemStack carrier, CallbackInfoReturnable<Boolean> cir) {
        if(cooking$sealed&&cir.getReturnValueZ()&&cooking$self().getStatus()==IStockpot.PUT_SOUP_BASE) cooking$reset();
    }
    @Inject(method={"saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V","m_183515_(Lnet/minecraft/nbt/CompoundTag;)V"},
        at=@At("TAIL"),require=1,allow=1,remap=false)
    private void cooking$save(CompoundTag tag, CallbackInfo ci) {
        if(!cooking$sealed) { tag.remove(COOKING_SEALED); return; }
        CompoundTag data=new CompoundTag(); data.putBoolean("sealed",true);
        if(cooking$recipe!=null) data.putString("recipe",cooking$recipe.toString());
        data.putString("binding",cooking$binding);
        data.put("witness",cooking$witness.save(new CompoundTag())); tag.put(COOKING_SEALED,data);
    }
    @Inject(method={"load(Lnet/minecraft/nbt/CompoundTag;)V","m_142466_(Lnet/minecraft/nbt/CompoundTag;)V"},
        at=@At("TAIL"),require=1,allow=1,remap=false)
    private void cooking$load(CompoundTag tag, CallbackInfo ci) {
        // Presence seals even malformed data; never turn corrupt ownership into withdrawable water.
        cooking$sealed=tag.contains(COOKING_SEALED); cooking$recipe=null; cooking$binding=""; cooking$witness=ItemStack.EMPTY;
        if(!cooking$sealed) return;
        if(!tag.contains(COOKING_SEALED,Tag.TAG_COMPOUND)) return;
        CompoundTag data=tag.getCompound(COOKING_SEALED);
        cooking$recipe=ResourceLocation.tryParse(data.getString("recipe")); cooking$binding=data.getString("binding");
        cooking$witness=ItemStack.of(data.getCompound("witness"));
    }
}
