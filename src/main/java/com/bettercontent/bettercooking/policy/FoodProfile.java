package com.bettercontent.bettercooking.policy;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pack-authored per-serving base values. Quality is applied only at resolution. */
public record FoodProfile(String id, String canonicalId, String role, String workClass, int servings,
                          int hunger, double saturationModifier, double hydration, double quenching,
                          Map<String, Double> nutrients, String quality, List<String> unresolved, String quantityBasis, NativeIntake nativeIntake) {
    /** Frozen default-stack native doses survive removal of their old production recipe graph. */
    public record NativeIntake(Double sugar,Double alcohol,Double spice) {
        public static final NativeIntake UNKNOWN=new NativeIntake(null,null,null);
        public NativeIntake { for(Double dose:new Double[]{sugar,alcohol,spice})if(dose!=null&&!finite(dose,0,10000))throw new IllegalArgumentException("Invalid native dose"); }
    }
    public FoodProfile(String id,String canonicalId,String role,String workClass,int servings,int hunger,double saturationModifier,
                       double hydration,double quenching,Map<String,Double> nutrients,String quality,List<String> unresolved,String quantityBasis) {
        this(id,canonicalId,role,workClass,servings,hunger,saturationModifier,hydration,quenching,nutrients,quality,unresolved,quantityBasis,NativeIntake.UNKNOWN);
    }
    public FoodProfile(String id,String canonicalId,String role,String workClass,int servings,int hunger,double saturationModifier,
                       double hydration,double quenching,Map<String,Double> nutrients,String quality,List<String> unresolved) {
        this(id,canonicalId,role,workClass,servings,hunger,saturationModifier,hydration,quenching,nutrients,quality,unresolved,
                role.equals("meal")?"per_portion":role.equals("drink")?"paid_beverage":"native_intake",NativeIntake.UNKNOWN);
    }
    private static final Set<String> ROLES = Set.of("ingredient", "staple", "meal", "drink", "special", "unresolved");
    public FoodProfile {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || canonicalId == null || !ROLES.contains(role))
            throw new IllegalArgumentException("Invalid food identity/role: " + id);
        boolean signedNative = role.equals("special") || !unresolved.isEmpty();
        if (servings < 1 || servings > 256 || hunger < (signedNative ? -100 : 0) || hunger > 100
                || !finite(saturationModifier, signedNative ? -100 : 0, 10) || !finite(hydration, 0, 128) || !finite(quenching, 0, 256))
            throw new IllegalArgumentException("Invalid serving quantities: " + id);
        if(!Set.of("per_portion","native_intake","paid_beverage").contains(quantityBasis))throw new IllegalArgumentException("Unknown quantity basis: "+id);
        java.util.Objects.requireNonNull(nativeIntake,"nativeIntake");
        nutrients = Map.copyOf(nutrients);
        nutrients.forEach((key, value) -> { if (!finite(value, 0, 100)) throw new IllegalArgumentException("Invalid nutrient " + key); });
        unresolved = List.copyOf(unresolved);
    }
    public boolean protectedMeal() { return role.equals("meal"); }
    public boolean resolved() { return unresolved.isEmpty() && !role.equals("unresolved"); }
    private static boolean finite(double number, double min, double max) { return Double.isFinite(number) && number >= min && number <= max; }
}
