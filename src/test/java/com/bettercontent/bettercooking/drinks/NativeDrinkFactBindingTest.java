package com.bettercontent.bettercooking.drinks;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Pure atomic identity-gate controls, not byte attestation or fake Minecraft ingredient facts. */
public final class NativeDrinkFactBindingTest {
    private static final String INPUT="a".repeat(64), OTHER="b".repeat(64), SNAPSHOT="c".repeat(64);
    private static JsonObject manifest(String input,String snapshot){
        JsonObject manifest=new JsonObject(),binding=new JsonObject();manifest.addProperty("schema",NativeDrinkFactBinding.SCHEMA);
        binding.addProperty("runtime_inputs_sha256",input);binding.addProperty("native_snapshot_sha256",snapshot);manifest.add("binding",binding);return manifest;
    }
    @Test void noAutoInstallAndVerifiedInstallAtomic(){
        NativeDrinkFactBinding<String> gate=new NativeDrinkFactBinding<>();assertTrue(gate.find("test-key").isEmpty());
        gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,ignored->Map.of("test-key","old"));assertEquals("old",gate.find("test-key").orElseThrow());
        gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,ignored->Map.of("other-key","new"));
        assertTrue(gate.find("test-key").isEmpty());assertEquals("new",gate.find("other-key").orElseThrow());
    }
    @Test void malformedJsonSameIdentityKeepsPriorValidatedFacts(){
        NativeDrinkFactBinding<String> gate=new NativeDrinkFactBinding<>();gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,x->Map.of("k","old"));
        assertThrows(IllegalArgumentException.class,()->gate.install(new JsonObject(),INPUT,SNAPSHOT,x->Map.of()));
        assertEquals("old",gate.find("k").orElseThrow());
        assertThrows(IllegalArgumentException.class,()->gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,x->{throw new IllegalArgumentException("invalid row");}));
        assertEquals("old",gate.find("k").orElseThrow());
    }
    @Test void changedDefinitionsClearEvenIfJsonInvalid(){
        NativeDrinkFactBinding<String> gate=new NativeDrinkFactBinding<>();gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,x->Map.of("k","old"));
        assertThrows(IllegalArgumentException.class,()->gate.install(new JsonObject(),OTHER,SNAPSHOT,x->Map.of()));assertTrue(gate.find("k").isEmpty());
    }
    @Test void eitherValidButMismatchedBindingClears(){
        for(boolean snapshotMismatch:new boolean[]{false,true}){
            NativeDrinkFactBinding<String> gate=new NativeDrinkFactBinding<>();gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,x->Map.of("k","old"));
            JsonObject wrong=manifest(snapshotMismatch?INPUT:OTHER,snapshotMismatch?OTHER:SNAPSHOT);
            assertThrows(IllegalArgumentException.class,()->gate.install(wrong,INPUT,SNAPSHOT,x->Map.of()));assertTrue(gate.find("k").isEmpty());
        }
    }
    @Test void reloadObservationAndExplicitInvalidationCloseLookup(){
        NativeDrinkFactBinding<String> gate=new NativeDrinkFactBinding<>();gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,x->Map.of("k","old"));
        gate.observeRuntimeInputs(INPUT);assertTrue(gate.find("k").isPresent());gate.observeRuntimeInputs(OTHER);assertTrue(gate.find("k").isEmpty());
        gate.install(manifest(OTHER,SNAPSHOT),OTHER,SNAPSHOT,x->Map.of("k","new"));gate.clear();assertTrue(gate.find("k").isEmpty());
    }
    @Test void unverifiedIdentityCannotRemainAvailable(){
        NativeDrinkFactBinding<String> gate=new NativeDrinkFactBinding<>();gate.install(manifest(INPUT,SNAPSHOT),INPUT,SNAPSHOT,x->Map.of("k","old"));
        assertThrows(IllegalArgumentException.class,()->gate.observeRuntimeInputs("pack-id-not-a-byte-digest"));assertTrue(gate.find("k").isEmpty());
    }
}
