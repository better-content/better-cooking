package com.bettercontent.bettercooking.policy;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CulinaryPolicyTest {
    private static FoodProfile food(String role,int hunger,double saturation,int servings) {
        return new FoodProfile("test:meal","test:meal",role,"substantial",servings,hunger,saturation,2,2.5,Map.of("proteins",.2),"carry_preparation",List.of());
    }
    private static String policy(String aliases,String routes,String gaps) {
        return """
          {"schema":"better_cooking:culinary_policy.v1","qualification":"qualified",
           "aliases":%s,"foods":{"test:meal":{"canonical":"test:meal","role":"meal","work_class":"substantial","servings":1,
             "nutrition":{"hunger":8,"saturation_modifier":0.7,"nutrients":{"proteins":0.2}},"moisture":{"hydration":2,"quenching":2.5},"quality":"carry_preparation","unresolved":[]}},
           "routes":%s,"gaps":%s}
          """.formatted(aliases,routes,gaps);
    }
    @Test void explicitBundledPolicyLoadsForHonestDiscoveryWithoutDiscardingFoodRows() throws Exception {
        try(var input=PolicyRepository.class.getResourceAsStream("/better_cooking/culinary_policy.json")) {
            assertNotNull(input);String text=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var parsed=PolicyRepository.parse(text);var raw=com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            assertEquals(raw.getAsJsonObject("foods").size(),parsed.foods().size());
            if(!raw.get("qualification").getAsString().equals("qualified"))assertFalse(parsed.qualified());
        }
    }
    @Test void incompleteBundledCatalogueLoadsWithoutClaimingGameplayCoverage() {
        PolicyRepository.loadBundled(false);
        assertFalse(PolicyRepository.current().qualified());
        assertFalse(PolicyRepository.current().gaps().isEmpty());
        assertFalse(PolicyRepository.current().foods().isEmpty());
        assertFalse(PolicyRepository.current().routes().isEmpty());
    }
    @Test void allFourQualityTiersUseModerateMultipliers() {
        assertEquals(.75,CuisineQuality.POOR.multiplier());assertEquals(1,CuisineQuality.STANDARD.multiplier());
        assertEquals(1.15,CuisineQuality.EXCELLENT.multiplier());assertEquals(1.3,CuisineQuality.SUPERB.multiplier());
    }
    @Test void nativeQualityIdsAreStableAndUnknownDefaultsStandard() {
        assertEquals(CuisineQuality.SUPERB,CuisineQuality.fromNative(0));assertEquals(CuisineQuality.POOR,CuisineQuality.fromNative(3));
        assertEquals(CuisineQuality.STANDARD,CuisineQuality.fromNative(123));
    }
    @Test void saturationTotalScalesOnceNotSquared() {
        var result=ServingProfile.resolve(food("meal",8,.7,1),CuisineQuality.SUPERB,Map.of("proteins",.2),.3,.4,.5);
        assertEquals(10,result.hunger());assertEquals(8*.7*2*1.3,result.hunger()*result.saturationModifier()*2,1e-6);
        assertEquals(.26,result.nutrients().get("proteins"),1e-9);
    }
    @Test void qualityNeverMultipliesHydrationOrActualDoses() {
        var result=ServingProfile.resolve(food("meal",8,.7,1),CuisineQuality.SUPERB,Map.of("proteins",.2),.3,.4,.5);
        assertEquals(2,result.hydration());assertEquals(2.5,result.quenching());assertEquals(.3,result.sugar());assertEquals(.4,result.alcohol());assertEquals(.5,result.spice());
    }
    @Test void unscaledProvenanceRemainsAvailableAcrossFurtherStages() {
        var first=ServingProfile.resolve(food("meal",8,.7,1),CuisineQuality.SUPERB,Map.of("proteins",.2),0,0,0);
        var second=ServingProfile.resolve(food("meal",8,.7,1),CuisineQuality.SUPERB,first.baseNutrients(),0,0,0);
        assertEquals(first.nutrients(),second.nutrients());assertEquals(.2,second.baseNutrients().get("proteins"));
    }
    @Test void ordinaryStaplesRetainBaseNutritionEvenWithLegacyQualityTag() {
        var result=ServingProfile.resolve(food("staple",5,.6,1),CuisineQuality.POOR,Map.of("proteins",.2),0,0,0);
        assertEquals(5,result.hunger());assertEquals(.6,result.saturationModifier(),1e-6);assertEquals(.2,result.nutrients().get("proteins"));
    }
    @Test void inheritedPoorCannotBeLaundered() {
        for(CuisineQuality quality:CuisineQuality.values())assertEquals(CuisineQuality.POOR,CuisineQuality.worst(quality,CuisineQuality.POOR));
    }
    @Test void signedSpecialFoodNativePropertiesAreNotInventedZeros() {
        var result=ServingProfile.resolve(food("special",-2,-.1,1),CuisineQuality.STANDARD,Map.of(),0,0,0);
        assertEquals(-2,result.hunger());assertEquals(-.1,result.saturationModifier(),1e-6);
        assertThrows(IllegalArgumentException.class,()->food("meal",-2,.1,1));
    }
    @Test void parseMapsNotArraysAndRetainsExplicitValues() {
        var result=PolicyRepository.parse(policy("{}","{}","[]"));assertTrue(result.qualified());assertEquals(8,result.food("test:meal").hunger());
    }
    @Test void runtimeRoutesUseExecutableReplacementIdsNotRawSourceKeys() {
        String routes="""
          {"test:original_recipe":{"disposition":"replace","unresolved":[],"replacement":{
            "id":"test:prepared_recipe","surface":"board","input_groups":[[{"kind":"item","id":"minecraft:carrot","count":1}]],
            "output":{"id":"test:meal","count":1},"servings":1,"work_class":"straightforward",
            "stages":[{"action":"assemble","duration_ticks":20,"repetitions":1,"tool_tag":"forge:tools/knives"}],"cook_ticks":0}}}
          """;
        var parsed=PolicyRepository.parse(policy("{}",routes,"[]"));
        assertTrue(parsed.routes().containsKey("test:prepared_recipe"));
        assertFalse(parsed.routes().containsKey("test:original_recipe"));
        assertEquals(List.of("test:original_recipe"),parsed.routes().get("test:prepared_recipe").replaces());
    }
    @Test void aliasCyclesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->PolicyRepository.parse(policy("{\"test:a\":\"test:b\",\"test:b\":\"test:a\"}","{}","[]")));
    }
    @Test void unresolvedRoutesWithoutReplacementCannotHideFromQualification() {
        var result=PolicyRepository.parse(policy("{}","{\"test:route\":{\"disposition\":\"unresolved\",\"unresolved\":[]}}","[]"));assertFalse(result.qualified());
    }
    @Test void missingReplacementCannotQualify() {
        assertFalse(PolicyRepository.parse(policy("{}","{\"test:route\":{\"disposition\":\"replace\",\"unresolved\":[]}}","[]")).qualified());
    }
    @Test void retainedRouteWithEmbeddedGapCannotQualify() {
        assertFalse(PolicyRepository.parse(policy("{}","{\"test:route\":{\"disposition\":\"retain_bulk\",\"unresolved\":[\"unknown_count\"]}}","[]")).qualified());
    }
    @Test void missingMoistureDoesNotQuietlyQualifyAsDry() {
        var value=policy("{}","{}","[]").replace("\"hydration\":2","\"hydration\":null");assertFalse(PolicyRepository.parse(value).qualified());
    }
    @Test void invalidReloadKeepsPreviousQualifiedSnapshotAtomically() {
        assertTrue(PolicyRepository.reload(policy("{}","{}","[]")));var previous=PolicyRepository.current();
        assertFalse(PolicyRepository.reload(policy("{}","{}","[\"unresolved\"]")));assertSame(previous,PolicyRepository.current());
    }
    @Test void profileMapsCannotBeMutatedBehindValidatedSnapshot() {
        var snapshot=PolicyRepository.parse(policy("{}","{}","[]"));assertThrows(UnsupportedOperationException.class,()->snapshot.foods().clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.food("test:meal").nutrients().put("proteins",99D));
    }
}
