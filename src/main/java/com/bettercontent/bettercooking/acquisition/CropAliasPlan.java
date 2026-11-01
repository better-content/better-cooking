package com.bettercontent.bettercooking.acquisition;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Dependency-free count/identity plan. Authority lookup is exclusively the root's qualified acquisition API. */
final class CropAliasPlan {
    record Rewrite(String canonicalId, int count) {}
    static Optional<Rewrite> plan(String sourceId, int count, Function<String, Optional<String>> qualifiedLookup) {
        Objects.requireNonNull(sourceId); Objects.requireNonNull(qualifiedLookup);
        if (count <= 0) return Optional.empty();
        Optional<String> canonical = Objects.requireNonNull(qualifiedLookup.apply(sourceId));
        if (canonical.isEmpty() || canonical.get().equals(sourceId)) return Optional.empty();
        if (canonical.get().isBlank()) throw new IllegalArgumentException("Empty canonical acquisition ID");
        return Optional.of(new Rewrite(canonical.get(), count));
    }
}
