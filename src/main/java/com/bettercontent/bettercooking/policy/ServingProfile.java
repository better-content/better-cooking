package com.bettercontent.bettercooking.policy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Final single-portion values used by both presentation and completion. */
public record ServingProfile(String itemId, CuisineQuality quality, int hunger, float saturationModifier,
                             Map<String, Double> baseNutrients, Map<String, Double> nutrients,
                             double hydration, double quenching, double sugar, double alcohol, double spice) {
    public ServingProfile { baseNutrients = Map.copyOf(baseNutrients); nutrients = Map.copyOf(nutrients); }
    public static ServingProfile resolve(FoodProfile profile, CuisineQuality quality, Map<String, Double> base,
                                         double sugar, double alcohol, double spice) {
        double scale = profile.protectedMeal() ? quality.multiplier() : 1;
        int hunger = (int) Math.round(profile.hunger() * scale);
        double totalSaturation = 2 * profile.hunger() * profile.saturationModifier() * scale;
        float modifier = hunger == 0 ? 0 : (float) (totalSaturation / (2 * hunger));
        Map<String, Double> adjusted = new LinkedHashMap<>();
        base.forEach((key, value) -> adjusted.put(key, value * scale));
        return new ServingProfile(profile.id(), quality, hunger, modifier, base, adjusted,
                profile.hydration(), profile.quenching(), sugar, alcohol, spice);
    }
    /** Whole feast intake and one placed bite share per-portion arithmetic, not a second quality scaling. */
    public ServingProfile forPortions(double portions) {
        if(!Double.isFinite(portions)||portions<=0||portions>256)throw new IllegalArgumentException("Invalid consumed portions");
        int totalHunger=(int)Math.round(hunger*portions);
        double totalSaturation=2*hunger*saturationModifier*portions;
        Map<String,Double> unscaled=new LinkedHashMap<>(),adjusted=new LinkedHashMap<>();
        baseNutrients.forEach((group,value)->unscaled.put(group,value*portions));nutrients.forEach((group,value)->adjusted.put(group,value*portions));
        return new ServingProfile(itemId,quality,totalHunger,totalHunger==0?0:(float)(totalSaturation/(2*totalHunger)),unscaled,adjusted,
                hydration*portions,quenching*portions,sugar*portions,alcohol*portions,spice*portions);
    }
    public String identity() { return itemId + "#" + quality.name().toLowerCase(java.util.Locale.ROOT); }
}
