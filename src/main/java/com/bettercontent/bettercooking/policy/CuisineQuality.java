package com.bettercontent.bettercooking.policy;

/** Stable native IDs, not ordinal ordering or player skill. */
public enum CuisineQuality {
    POOR(3, .75), STANDARD(2, 1), EXCELLENT(1, 1.15), SUPERB(0, 1.30);
    private final int nativeId;
    private final double multiplier;
    CuisineQuality(int nativeId, double multiplier) { this.nativeId = nativeId; this.multiplier = multiplier; }
    public int nativeId() { return nativeId; }
    public double multiplier() { return multiplier; }
    public static CuisineQuality fromNative(int id) {
        for (CuisineQuality quality : values()) if (quality.nativeId == id) return quality;
        return STANDARD;
    }
    public static CuisineQuality worst(CuisineQuality first, CuisineQuality second) {
        return first.multiplier <= second.multiplier ? first : second;
    }
}
