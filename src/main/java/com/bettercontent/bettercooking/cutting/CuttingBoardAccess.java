package com.bettercontent.bettercooking.cutting;

/** Implemented only by the audited native board mixin; no reflective/private-field bridge. */
public interface CuttingBoardAccess {
    CuttingSession cooking$cuttingSession();
    NativeCuttingSelection.Choice cooking$cuttingChoice();
    void cooking$setCuttingChoice(NativeCuttingSelection.Choice choice);
}
