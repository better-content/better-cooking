package com.bettercontent.bettercooking.drinks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure arithmetic/explicit route tests. No Minecraft/bootstrap or pretend consumption. */
public final class NativeDrinkRoutesTest {
    @Test void exactNineSourceIdsNoKeywords(){
        assertEquals(9,NativeDrinkRoutes.IDS.size());
        assertFalse(NativeDrinkRoutes.IDS.contains("kaleidoscope_cookery:teapot"));
        assertFalse(NativeDrinkRoutes.IDS.contains("kaleidoscope_cookery:empty_cup"));
        assertFalse(NativeDrinkRoutes.IDS.contains("minecraft:milk_bucket"));
    }
    @Test void normalTeapotTwelveAndMysteryFourNotTrayCount(){
        for(String id:NativeDrinkRoutes.IDS)if(!id.equals(NativeDrinkRoutes.MILK_TEA)) {
            int yield=id.equals(NativeDrinkRoutes.MYSTERY)?4:12;
            assertDoesNotThrow(()->NativeDrinkRoutes.validate("teapot",id,yield));
            assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.validate("teapot",id,yield==4?12:4));
        }
    }
    @Test void milkStockpotEveryActualYieldOneThroughNine(){
        for(int n=1;n<=9;n++){int yield=n;assertDoesNotThrow(()->NativeDrinkRoutes.validate("stockpot",NativeDrinkRoutes.MILK_TEA,yield));}
        for(int n:new int[]{0,10,12})assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.validate("stockpot",NativeDrinkRoutes.MILK_TEA,n));
        assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.validate("teapot",NativeDrinkRoutes.MILK_TEA,12));
        assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.validate("stockpot",NativeDrinkRoutes.MYSTERY,4));
    }
    @Test void waterAndMilkEntitlementConservedForWholeBatch(){
        for(int yield:new int[]{1,2,3,4,5,6,7,8,9,12})for(double paid:new double[]{.06,12,16,20,1000})
            assertEquals(paid,NativeDrinkRoutes.perCup(paid,yield)*yield,1e-10);
        assertEquals(1000D/12,NativeDrinkRoutes.perCup(1000,12),1e-10);
        assertEquals(250,NativeDrinkRoutes.perCup(1000,4),1e-10);
        assertEquals(1,NativeDrinkRoutes.perCup(12,12),1e-10);
    }
    @Test void invalidRouteAndDoseNeverBecomeZeroFacts(){
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1})assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.perCup(value,12));
        assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.perCup(16,0));
        assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.perCup(16,13));
        assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.validate("guess",NativeDrinkRoutes.MYSTERY,4));
        assertThrows(IllegalArgumentException.class,()->NativeDrinkRoutes.validate("teapot","unknown:tea",12));
    }
}
