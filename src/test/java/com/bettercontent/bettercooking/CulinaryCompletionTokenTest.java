package com.bettercontent.bettercooking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Source-only replay control. Real native event/placed-bite delivery remains UNRUN. */
class CulinaryCompletionTokenTest {
    @Test void sameCommittedServingCanOnlyMintOneAwardSequence() {
        var ledger = new CompletionTokenLedger();
        Object committedNativeFinish = new Object();
        assertTrue(ledger.claim(committedNativeFinish));
        assertFalse(ledger.claim(committedNativeFinish), "Duplicate callback cannot create a fresh hydration/spice/nutrition transaction");
    }

    @Test void twoPaidPortionsOfTheSameItemInTheSameTickRemainDistinct() {
        var ledger = new CompletionTokenLedger();
        // These represent two ACTUAL separate native Finish/bite debit events, not
        // an item-ID/stack/tick heuristic that would discard the second portion.
        Object first = new Object(), second = new Object();
        assertTrue(ledger.claim(first));
        assertTrue(ledger.claim(second));
        assertFalse(ledger.claim(first));
        assertFalse(ledger.claim(second));
    }

    @Test void tokenIsMandatoryAndIdentityBased() {
        var ledger = new CompletionTokenLedger();
        assertThrows(NullPointerException.class, () -> ledger.claim(null));
        Object first = new String("paid serving"), second = new String("paid serving");
        assertNotSame(first, second);
        assertTrue(ledger.claim(first));
        assertTrue(ledger.claim(second), "Equal-looking paid items are not the same committed use");
    }
}
