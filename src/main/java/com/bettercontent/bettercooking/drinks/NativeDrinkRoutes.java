package com.bettercontent.bettercooking.drinks;

import java.util.Set;

/** Explicit pinned KC1.6.0 native whole-item routes; no keyword or edible heuristic. */
public final class NativeDrinkRoutes {
    public static final String PREFIX="kaleidoscope_cookery:";
    public static final String MILK_TEA=PREFIX+"clay_pot_milk_tea", MYSTERY=PREFIX+"mystery_tea";
    public static final Set<String> IDS=Set.of(MYSTERY,PREFIX+"barley_tea",PREFIX+"tieguanyin",
            PREFIX+"biluochun",PREFIX+"oolong",PREFIX+"sakura_fubuki",PREFIX+"butter_tea",PREFIX+"flower_tea",MILK_TEA);
    private NativeDrinkRoutes() {}
    public static void validate(String route,String output,int yield) {
        if(!IDS.contains(output))throw new IllegalArgumentException("Not a source-proven native KC drink: "+output);
        if(route.equals("teapot")) {
            if(output.equals(MILK_TEA)||yield!=(output.equals(MYSTERY)?4:12))
                throw new IllegalArgumentException("Native teapot output/yield drift");
        } else if(route.equals("stockpot")) {
            if(!output.equals(MILK_TEA)||yield<1||yield>9)throw new IllegalArgumentException("Native milk stockpot output/yield drift");
        } else throw new IllegalArgumentException("Unknown native drink route");
    }
    public static double perCup(double paidWhole,int yield) {
        if(!Double.isFinite(paidWhole)||paidWhole<0||yield<1||yield>12)throw new IllegalArgumentException("Invalid paid dose/yield");
        return paidWhole/yield;
    }
}
