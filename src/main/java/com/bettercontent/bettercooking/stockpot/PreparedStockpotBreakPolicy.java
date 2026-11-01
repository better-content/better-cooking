package com.bettercontent.bettercooking.stockpot;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Pure preview policy. Expected unpaid-carrier refusal is NOT corrupt state or permission to emit loot. */
public final class PreparedStockpotBreakPolicy {
    private PreparedStockpotBreakPolicy() {}
    public static final String SERVE_FIRST="Serve remaining food with required containers before breaking.";
    public static final class UnpaidCarrier extends IllegalStateException {
        public UnpaidCarrier() { super(SERVE_FIRST); }
    }
    public record Decision(boolean allowed,boolean unpaidCarrier,List<ItemStack> drops,String message,RuntimeException failure) {}
    public static Decision evaluate(PreparedStockpotStateAccess access) {
        try { return new Decision(true,false,access.cooking$preparedStockpotBreakDrops(),"",null); }
        catch(UnpaidCarrier unpaid) { return new Decision(false,true,List.of(),SERVE_FIRST,unpaid); }
        catch(RuntimeException invalid) { return new Decision(false,false,List.of(),"Cannot safely break prepared stockpot: "+invalid.getMessage(),invalid); }
    }
}
