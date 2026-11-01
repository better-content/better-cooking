package com.bettercontent.bettercooking.policy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Concrete recipe-stage progress. No player XP, owner binding or transferable work currency. */
public final class PreparationState {
    private int stage;
    private int repetition;
    private UUID actor;
    private long started = -1;
    private long lastPulse = -1;
    public PreparationState() {}
    public PreparationState(int stage, int repetition) {
        if (stage < 0 || repetition < 0) throw new IllegalArgumentException("Negative preparation progress");
        this.stage = stage; this.repetition = repetition;
    }
    public int stage() { return stage; }
    public int repetition() { return repetition; }
    public boolean finished(List<CulinaryRoute.Stage> stages) { return stage >= stages.size(); }
    public boolean active() { return actor != null; }
    public int elapsed(long now) { return started < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, now - started)); }
    public boolean pulse(UUID player, long now, boolean valid, List<CulinaryRoute.Stage> stages) {
        if (!valid || finished(stages)) { interrupt(); return false; }
        if (actor == null || !Objects.equals(actor, player) || now < lastPulse || now - lastPulse > 6) {
            actor = player; started = now;
        }
        lastPulse = now;
        return true;
    }
    /** Called only after validating the current real actor, tool, station and held-input heartbeat. */
    public boolean tick(UUID player, long now, boolean valid, List<CulinaryRoute.Stage> stages) {
        if (finished(stages) || !valid || !Objects.equals(actor, player) || now < lastPulse || now - lastPulse > 6) {
            interrupt(); return false;
        }
        CulinaryRoute.Stage step = stages.get(stage);
        if (now - started < step.durationTicks()) return false;
        repetition++;
        if (repetition >= step.repetitions()) { stage++; repetition = 0; }
        // A new action starts only on another validated input pulse. Packet bursts cannot prepay work.
        interrupt();
        return true;
    }
    public void interrupt() { actor = null; started = -1; lastPulse = -1; }
}
