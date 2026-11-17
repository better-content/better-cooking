package com.bettercontent.bettercooking;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Objects;

/** Server-session replay guard for actual committed serving callbacks, not item/tick dedup.
 * One token is allocated by the native Finish event or verified placed-bite debit; a
 * second distinct paid serving (even same item and tick) has a distinct token.
 * The native callback cannot be retried after 128 subsequent commits/reconnect. */
final class CompletionTokenLedger {
    private static final int RECENT = 128;
    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
    private final ArrayDeque<Object> order = new ArrayDeque<>();

    boolean claim(Object committedUse) {
        Objects.requireNonNull(committedUse, "Actual committed serving token required");
        if (seen.containsKey(committedUse)) return false;
        seen.put(committedUse, Boolean.TRUE);
        order.addLast(committedUse);
        if (order.size() > RECENT) seen.remove(order.removeFirst());
        return true;
    }
}
