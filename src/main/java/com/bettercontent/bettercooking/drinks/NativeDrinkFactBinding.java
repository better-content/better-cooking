package com.bettercontent.bettercooking.drinks;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Function;

/** Pure atomic identity gate. Digests are supplied by Root's byte verifier, NEVER pack-ID hashes. */
public final class NativeDrinkFactBinding<T> {
    public static final String SCHEMA="better_cooking:native_drink_ingredients.v2";
    private record State<T>(String inputs,String snapshot,Map<String,T> entries) {}
    private State<T> state;
    private static boolean sha(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
    public synchronized void observeRuntimeInputs(String currentInputsSha){
        if(!sha(currentInputsSha)){state=null;throw new IllegalArgumentException("Verified current byte identity required");}
        if(state!=null&&!state.inputs().equals(currentInputsSha))state=null;
    }
    public synchronized void clear(){state=null;}
    public record BoundEntry<T>(T value,String runtimeInputsSha256,String nativeSnapshotSha256) {}
    public synchronized Optional<BoundEntry<T>> findBound(String canonicalStack){
        if(state==null||!state.entries().containsKey(canonicalStack))return Optional.empty();
        return Optional.of(new BoundEntry<>(state.entries().get(canonicalStack),state.inputs(),state.snapshot()));
    }
    public synchronized Optional<T> find(String canonicalStack){return findBound(canonicalStack).map(BoundEntry::value);}
    public synchronized void install(JsonObject manifest,String currentInputsSha,String actualSnapshotSha,
                                     Function<JsonObject,Map<String,T>> parser){
        observeRuntimeInputs(currentInputsSha); // Changed definitions invalidate old defaults even if JSON is bad.
        if(!sha(actualSnapshotSha))throw new IllegalArgumentException("Actual raw snapshot SHA required");
        if(manifest==null||!manifest.has("schema")||!manifest.get("schema").isJsonPrimitive()
                ||!SCHEMA.equals(manifest.get("schema").getAsString())||!manifest.has("binding")||!manifest.get("binding").isJsonObject())
            throw new IllegalArgumentException("Invalid/unbound ingredient manifest");
        JsonObject binding=manifest.getAsJsonObject("binding");
        String inputs=text(binding,"runtime_inputs_sha256"),snapshot=text(binding,"native_snapshot_sha256");
        if(!sha(inputs)||!sha(snapshot))throw new IllegalArgumentException("Missing verified binding hashes");
        if(!inputs.equals(currentInputsSha)||!snapshot.equals(actualSnapshotSha)){
            state=null;throw new IllegalArgumentException("Ingredient facts/runtime or raw-snapshot byte identity mismatch");
        }
        Map<String,T> candidate=Map.copyOf(Objects.requireNonNull(parser.apply(manifest)));
        // All parsing/validation happens BEFORE publication. Invalid JSON alone with unchanged
        // current identity leaves the prior validated immutable state intact.
        state=new State<>(inputs,snapshot,candidate);
    }
    private static String text(JsonObject object,String field){
        if(!object.has(field)||!object.get(field).isJsonPrimitive()||!object.getAsJsonPrimitive(field).isString())
            throw new IllegalArgumentException("Missing binding "+field);
        return object.get(field).getAsString();
    }
}
