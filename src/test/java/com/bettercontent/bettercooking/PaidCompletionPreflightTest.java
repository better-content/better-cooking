package com.bettercontent.bettercooking;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source-contract check; actual paid native Finish, partial/cancel and hydration are GameTests. */
class PaidCompletionPreflightTest {
    @Test void invalidOverlappingOrUnavailableEntitlementsNeverClaimCommittedUse() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bettercontent/bettercooking/CulinaryConsumption.java"));
        int resolve = source.indexOf("var nutrition=CulinaryNutritionApi.resolve(player,capturedServing);");
        int hydration = source.indexOf("var hydration=ServingHydration.resolve(player,capturedServing);");
        int dose = source.indexOf("double paidSpice=spiceManages(capturedServing)");
        int validation = source.indexOf("new com.bettercontent.bettersurvivalphysiology.nutrition.CommittedDietIntake.Portion(");
        int water = source.indexOf("ServingHydration.nextFoodSequence(player)");
        int token = source.indexOf(".claim(committedUse)");
        int sequence = source.indexOf("putLong(SEQUENCE,sequence)");
        int award = source.indexOf("CommittedDietIntake.commit(player,capturedServing,");
        int charge = source.indexOf("SpiceRuntime.applyCompletedUnits(player,paidSpice)");
        assertTrue(resolve > 0 && hydration > resolve && dose > hydration && validation > dose);
        assertTrue(water > validation && token > water && sequence > token);
        assertTrue(award > sequence && charge > award);
        assertTrue(source.contains("if(waterNext.isEmpty())return false"));
        assertTrue(source.contains("if(paidSpice>0)SpiceRuntime.applyCompletedUnits(player,paidSpice)"));
        assertFalse(source.contains("CulinaryNutritionApi.complete(player,capturedServing)"));
    }
}
