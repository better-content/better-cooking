package com.bettercontent.bettercooking.policy;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable policy snapshots; rejected reloads never publish a partially validated catalogue. */
public final class PolicyRepository {
    public record Snapshot(Map<String, FoodProfile> foods, Map<String, CulinaryRoute> routes,
                           Map<String, String> aliases, List<String> gaps, String qualification, String sha256, Map<String,String> acquisitionAliases) {
        public Snapshot { foods=Map.copyOf(foods); routes=Map.copyOf(routes); aliases=Map.copyOf(aliases); gaps=List.copyOf(gaps);acquisitionAliases=Map.copyOf(acquisitionAliases); }
        public FoodProfile food(String id) { return foods.get(id); }
        public boolean qualified() { return !foods.isEmpty() && qualification.equals("qualified") && gaps.isEmpty()
                && foods.values().stream().allMatch(FoodProfile::resolved) && routes.values().stream().allMatch(CulinaryRoute::resolved); }
    }
    private static volatile Snapshot current;
    private PolicyRepository() {}
    public static boolean loaded() { return current!=null; }
    public static Snapshot current() { Snapshot value=current; if(value==null) throw new IllegalStateException("Cooking policy is not loaded"); return value; }
    public static Optional<String> productionCropAlias(String id) {
        Snapshot policy=current();return policy.qualified()?Optional.ofNullable(policy.acquisitionAliases().get(id)):Optional.empty();
    }
    public static synchronized void loadBundled(boolean discoveryOnly) {
        try(InputStream stream=PolicyRepository.class.getResourceAsStream("/better_cooking/culinary_policy.json")) {
            if(stream==null) throw new IllegalStateException("Missing explicit culinary policy build input");
            Snapshot candidate=parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
            if(!discoveryOnly && !candidate.qualified())
                LogUtils.getLogger().warn("Cooking catalogue is incomplete ({} global gaps); retaining native food/recipe behavior until qualified",candidate.gaps().size());
            current=candidate;
        } catch(RuntimeException error) { throw error; } catch(Exception error) { throw new IllegalStateException("Cannot load cooking policy",error); }
    }
    public static synchronized boolean reload(String json) {
        try {
            Snapshot candidate=parse(json);
            if(!candidate.qualified()) throw new IllegalArgumentException("Unqualified replacement culinary policy");
            current=candidate; return true;
        } catch(RuntimeException error) {
            if(current==null) throw error;
            LogUtils.getLogger().error("Rejected culinary policy reload; retaining {}",current.sha256(),error); return false;
        }
    }
    public static Snapshot parse(String text) {
        JsonObject root=JsonParser.parseString(text).getAsJsonObject();
        if(!string(root,"schema","").equals("better_cooking:culinary_policy.v1")) throw new IllegalArgumentException("Unsupported culinary policy schema");
        Map<String,String> aliases=new TreeMap<>();
        root.getAsJsonObject("aliases").entrySet().forEach(entry->aliases.put(entry.getKey(),entry.getValue().getAsString()));
        for(String origin:aliases.keySet()) {
            Set<String> visited=new HashSet<>();String cursor=origin;
            while(aliases.containsKey(cursor)) { if(!visited.add(cursor)) throw new IllegalArgumentException("Culinary alias cycle: "+origin);cursor=aliases.get(cursor); }
        }
        Map<String,FoodProfile> foods=new TreeMap<>();
        for(var entry:root.getAsJsonObject("foods").entrySet()) {
            String id=entry.getKey();JsonObject value=entry.getValue().getAsJsonObject();
            JsonObject nutrition=value.has("nutrition")?value.getAsJsonObject("nutrition"):new JsonObject();
            JsonObject moisture=value.has("moisture")?value.getAsJsonObject("moisture"):new JsonObject();
            List<String> unresolved=strings(value.get("unresolved"));
            int hunger=(int)quantity(nutrition,"hunger",0,unresolved,id);
            double saturation=quantity(nutrition,"saturation_modifier",0,unresolved,id);
            double hydration=quantity(moisture,"hydration",0,unresolved,id),quenching=quantity(moisture,"quenching",0,unresolved,id);
            Map<String,Double> nutrients=new TreeMap<>();
            if(nutrition.has("nutrients")&&nutrition.get("nutrients").isJsonObject())
                nutrition.getAsJsonObject("nutrients").entrySet().forEach(n->{if(!n.getValue().isJsonNull()) nutrients.put(n.getKey(),n.getValue().getAsDouble());});
            FoodProfile profile=new FoodProfile(id,string(value,"canonical",id),string(value,"role","unresolved"),string(value,"work_class","none"),
                    integer(value,"servings",1),hunger,saturation,hydration,quenching,nutrients,string(value,"quality","fixed_standard"),unresolved,
                    string(value,"quantity_basis",string(value,"role","unresolved").equals("meal")?"per_portion":"native_intake"),nativeIntake(value));
            if(foods.put(id,profile)!=null) throw new IllegalArgumentException("Duplicate food "+id);
        }
        Map<String,CulinaryRoute> routes=new TreeMap<>();
        List<String> coverageGaps=new ArrayList<>();
        Set<String> dispositions=Set.of("replace","retain_bulk","retain_special","retain_nonfood","retain_distinct_apparatus","deferred");
        for(var entry:root.getAsJsonObject("routes").entrySet()) {
            JsonObject source=entry.getValue().getAsJsonObject();
            String disposition=string(source,"disposition","unresolved");
            if(!dispositions.contains(disposition)) coverageGaps.add("unresolved-disposition:"+entry.getKey());
            for(String gap:strings(source.get("unresolved"))) coverageGaps.add(entry.getKey()+":"+gap);
            if(!source.has("replacement")||source.get("replacement").isJsonNull()) {
                if(disposition.equals("replace")) coverageGaps.add("missing-replacement:"+entry.getKey());
                continue;
            }
            JsonObject value=source.getAsJsonObject("replacement");
            List<String> unresolved=strings(source.get("unresolved"));
            try {
            List<List<CulinaryRoute.Input>> groups=new ArrayList<>();
            for(JsonElement raw:value.getAsJsonArray("input_groups")) {
                List<CulinaryRoute.Input> alternatives=new ArrayList<>();
                for(JsonElement rawAlternative:raw.getAsJsonArray()) {
                    JsonObject input=rawAlternative.getAsJsonObject();
                    alternatives.add(new CulinaryRoute.Input(string(input,"kind","item"),string(input,"id",""),integer(input,"count",1)));
                }
                groups.add(alternatives);
            }
            List<CulinaryRoute.Stage> stages=new ArrayList<>();
            for(JsonElement raw:value.getAsJsonArray("stages")) {
                JsonObject stage=raw.getAsJsonObject();
                stages.add(new CulinaryRoute.Stage(string(stage,"action","assemble"),integer(stage,"duration_ticks",1),integer(stage,"repetitions",1),string(stage,"tool_tag","")));
            }
            JsonObject output=value.getAsJsonObject("output");
                CulinaryRoute route=new CulinaryRoute(string(value,"id",entry.getKey()),string(value,"surface","board"),groups,
                        string(output,"id",""),integer(output,"count",1),integer(value,"servings",1),string(value,"work_class","straightforward"),stages,
                        integer(value,"cook_ticks",0),List.of(entry.getKey()),unresolved);
                if(routes.put(route.id(),route)!=null) throw new IllegalArgumentException("Duplicate replacement route "+route.id());
            } catch(IllegalArgumentException error) {
                if(unresolved.isEmpty()) throw error;
                // Retain the explicit diagnostic gap, never authorize an invalid candidate shape.
                coverageGaps.add("unexecutable-candidate:"+entry.getKey()+":"+error.getMessage());
            }
        }
        List<String> gaps=strings(root.get("gaps"));gaps.addAll(coverageGaps);
        Map<String,String> acquisitionAliases=new TreeMap<>();
        if(root.has("acquisition")) {
            JsonObject acquisition=root.getAsJsonObject("acquisition");
            if(acquisition.has("rows"))for(JsonElement raw:acquisition.getAsJsonArray("rows")) {
                JsonObject row=raw.getAsJsonObject();String from=string(row,"item",""),to=string(row,"canonical","");
                if(aliases.containsKey(from)&&!to.equals(aliases.get(from)))throw new IllegalArgumentException("Acquisition conflicts with explicit ingredient alias: "+from);
                if(string(row,"status","unresolved").equals("unresolved"))gaps.add("unresolved-acquisition:"+from);
                for(String gap:strings(row.get("unresolved")))gaps.add("acquisition:"+from+":"+gap);
            }
            if(acquisition.has("harvest_redirects"))for(var entry:acquisition.getAsJsonObject("harvest_redirects").entrySet()) {
                JsonObject rule=entry.getValue().getAsJsonObject();
                if(integer(rule,"source_count_multiplier",0)!=1||!string(rule,"scope","").equals("new_native_harvest_output_only"))
                    throw new IllegalArgumentException("Unsupported acquisition transformation: "+entry.getKey());
                String target=string(rule,"target","");if(!target.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid harvest target");
                acquisitionAliases.put(entry.getKey(),target);
                if(!string(rule,"status","unresolved").equals("qualified"))gaps.add("unqualified-native-harvest:"+entry.getKey());
            }
        }
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
            return new Snapshot(foods,routes,aliases,gaps,string(root,"qualification","unqualified"),hash,acquisitionAliases);
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static FoodProfile.NativeIntake nativeIntake(JsonObject food) {
        if(!food.has("native_consumption")||!food.get("native_consumption").isJsonObject())return FoodProfile.NativeIntake.UNKNOWN;
        JsonObject nativeValues=food.getAsJsonObject("native_consumption");
        return new FoodProfile.NativeIntake(optionalDose(nativeValues,"sugar"),optionalDose(nativeValues,"alcohol"),optionalDose(nativeValues,"spice"));
    }
    private static Double optionalDose(JsonObject value,String key) { return value.has(key)&&!value.get(key).isJsonNull()?value.get(key).getAsDouble():null; }
    private static double quantity(JsonObject value,String key,double fallback,List<String> unresolved,String id) {
        if(!value.has(key)||value.get(key).isJsonNull()) { unresolved.add("missing:"+id+":"+key);return fallback; }
        double number=value.get(key).getAsDouble();if(!Double.isFinite(number)) throw new IllegalArgumentException("Nonfinite "+id+":"+key);return number;
    }
    private static String string(JsonObject value,String key,String fallback) { return value.has(key)&&!value.get(key).isJsonNull()?value.get(key).getAsString():fallback; }
    private static int integer(JsonObject value,String key,int fallback) { return value.has(key)&&!value.get(key).isJsonNull()?value.get(key).getAsInt():fallback; }
    private static List<String> strings(JsonElement value) {
        List<String> result=new ArrayList<>();if(value!=null&&value.isJsonArray()) for(JsonElement row:value.getAsJsonArray()) result.add(row.isJsonPrimitive()?row.getAsString():row.toString());return result;
    }
}
