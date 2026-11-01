package com.bettercontent.bettercooking.kitchen;

import com.bettercontent.bettercooking.policy.CulinaryRoute;
import com.bettercontent.bettercooking.policy.PreparationState;
import java.util.List;
import java.util.UUID;

/** Dependency-free executable controls. These do not substitute for Forge/native menu tests. */
public final class KitchenHeartbeatTest {
    private static int checks;
    private static void check(boolean condition, String name) { checks++; if (!condition) throw new AssertionError(name); }
    public static void main(String[] args) {
        UUID alice = new UUID(0, 1), bob = new UUID(0, 2);
        var stages = List.of(new CulinaryRoute.Stage("cut", 20, 2, "forge:tools/knives"), new CulinaryRoute.Stage("mix", 10, 1, ""));
        PreparationState state = new PreparationState();
        for (int packet = 0; packet < 100; packet++) state.pulse(alice, 0, true, stages);
        check(!state.tick(alice, 0, true, stages) && state.repetition() == 0, "burst cannot prepay duration");
        for (int tick = 1; tick <= 20; tick++) {
            if (tick % 5 == 0) state.pulse(alice, tick, true, stages);
            state.tick(alice, tick, true, stages);
        }
        check(state.stage() == 0 && state.repetition() == 1 && !state.active(), "one action per duration");
        check(!state.tick(alice, 1000, true, stages) && state.repetition() == 1, "idle cannot progress");
        state.pulse(alice, 1000, true, stages);
        check(!state.tick(alice, 1007, true, stages) && !state.active(), "missing heartbeat interrupts");
        state.pulse(alice, 1010, true, stages);
        check(!state.tick(alice, 1015, false, stages) && !state.active(), "tool reach connection invalidation interrupts");
        state.pulse(alice, 1020, true, stages);
        state.pulse(bob, 1025, true, stages);
        check(state.elapsed(1025) == 0, "new actor cannot steal active interval");
        state.interrupt();
        PreparationState resumed = new PreparationState(state.stage(), state.repetition());
        check(resumed.repetition() == 1 && !resumed.active() && resumed.elapsed(5000) == 0, "bundle preserves completed work only");
        for (int tick = 2000; tick <= 2020; tick++) { if (tick % 5 == 0) resumed.pulse(bob, tick, true, stages); resumed.tick(bob, tick, true, stages); }
        check(resumed.stage() == 1 && resumed.repetition() == 0, "second repetition advances stage");
        for (int tick = 2030; tick <= 2040; tick++) { if (tick % 5 == 0) resumed.pulse(bob, tick, true, stages); resumed.tick(bob, tick, true, stages); }
        check(resumed.finished(stages), "final stage completes");
        check(!resumed.pulse(alice, 3000, true, stages), "completed recipe cannot accrue transferable work");
        PreparationState backwards = new PreparationState(); backwards.pulse(alice, 100, true, stages);
        backwards.pulse(alice, 90, true, stages);
        check(backwards.elapsed(90) == 0, "backwards clock restarts interval");
        System.out.println("Kitchen heartbeat controls PASS: " + checks);
    }
}
