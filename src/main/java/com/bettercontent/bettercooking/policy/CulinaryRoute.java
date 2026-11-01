package com.bettercontent.bettercooking.policy;

import java.util.List;

public record CulinaryRoute(String id, String surface, List<List<Input>> inputGroups, String outputId,
                            int outputCount, int servings, String workClass, List<Stage> stages,
                            int cookTicks, List<String> replaces, List<String> unresolved) {
    public record Input(String kind, String id, int count) {
        public Input { if (!List.of("item", "tag").contains(kind) || count < 1 || count > 64) throw new IllegalArgumentException("Invalid ingredient"); }
    }
    public record Stage(String action, int durationTicks, int repetitions, String toolTag) {
        public Stage {
            if (!List.of("cut", "mix", "knead", "roll", "assemble", "stir").contains(action)
                    || durationTicks < 1 || durationTicks > 1200 || repetitions < 1 || repetitions > 64)
                throw new IllegalArgumentException("Invalid preparation action");
        }
        public int activeTicks() { return Math.multiplyExact(durationTicks, repetitions); }
    }
    public CulinaryRoute {
        inputGroups = inputGroups.stream().map(List::copyOf).toList();
        stages = List.copyOf(stages); replaces = List.copyOf(replaces); unresolved = List.copyOf(unresolved);
        if (id == null || outputId == null || inputGroups.isEmpty() || inputGroups.size() > 9
                || inputGroups.stream().anyMatch(List::isEmpty) || outputCount < 1 || outputCount > 64
                || servings < 1 || servings > 256 || stages.isEmpty() || cookTicks < 0)
            throw new IllegalArgumentException("Invalid culinary route: " + id);
    }
    public boolean resolved() { return unresolved.isEmpty(); }
    public int activeTicks() { return stages.stream().mapToInt(Stage::activeTicks).sum(); }
    public double referenceSecondsForEight() { return activeTicks() / 20.0 * 8 / servings; }
    public boolean immediate() { return surface.equals("board"); }
}
