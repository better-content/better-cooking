package com.bettercontent.bettercooking.placed;

/** Pure commit guard. Matching native pre-state advances; replay of that state is a no-op. */
public final class PortionProgress {
    private PortionProgress() {}
    public static void validate(int remaining) {
        if (remaining < 1 || remaining > 64) throw new IllegalArgumentException("Invalid placed portion count: " + remaining);
    }
    public static int complete(int current, int nativeBefore) {
        validate(current); validate(nativeBefore);
        if (current == nativeBefore) return current - 1;
        if (current == nativeBefore - 1) return current;
        throw new IllegalStateException("Placed portion state disagrees with native block");
    }
}
