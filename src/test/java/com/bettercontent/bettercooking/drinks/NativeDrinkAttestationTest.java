package com.bettercontent.bettercooking.drinks;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic protocol/adverse controls ONLY, never native getter/material/connection evidence. */
public final class NativeDrinkAttestationTest {
    private static final String CURRENT="a".repeat(64);
    private static JsonObject raw(){
        JsonObject root=new JsonObject(),context=new JsonObject();context.addProperty("schema",NativeDrinkFactBinding.SCHEMA);
        context.addProperty("status","observed_getters_unbound_runtime");context.addProperty("qualification",false);context.addProperty("auto_install",false);
        root.add("native_drink_ingredients_context",context);JsonArray rows=new JsonArray();
        JsonObject row=new JsonObject();row.addProperty("id","minecraft:water_bucket");row.addProperty("observation_kind","observed_template");row.addProperty("template_basis","provider_purity3_variant");
        row.addProperty("input_template","{Count:1b,id:\"minecraft:water_bucket\",tag:{Purity:3}}");
        row.addProperty("item_class","unit.synthetic.WaterBucket");row.addProperty("is_edible",false);row.add("food_properties",JsonNull.INSTANCE);row.add("gaps",new JsonObject());
        row.add("nutrients",new JsonObject());for(String k:new String[]{"sugar","alcohol","spice"})row.addProperty(k,0);
        JsonObject paid=new JsonObject();paid.addProperty("complete",true);paid.add("nutrients",new JsonObject());
        for(String k:new String[]{"sugar","alcohol","spice"})paid.addProperty(k,0);row.add("paid_intake",paid);rows.add(row);root.add("native_drink_ingredients",rows);return root;
    }
    private static byte[] bytes(JsonObject root){return root.toString().getBytes(StandardCharsets.UTF_8);}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw new AssertionError(impossible);}}
    private static JsonObject manifest(byte[] raw,JsonObject root){
        JsonObject m=new JsonObject(),b=new JsonObject();m.addProperty("schema",NativeDrinkFactBinding.SCHEMA);b.addProperty("runtime_inputs_sha256",CURRENT);b.addProperty("native_snapshot_sha256",hash(raw));m.add("binding",b);m.add("native_drink_ingredients",root.getAsJsonArray("native_drink_ingredients").deepCopy());return m;
    }
    private static JsonObject row(JsonObject root){return root.getAsJsonArray("native_drink_ingredients").get(0).getAsJsonObject();}
    private static void changed(Consumer<JsonObject> edit){JsonObject r=raw();byte[] b=bytes(r);JsonObject m=manifest(b,r);edit.accept(m);assertThrows(IllegalArgumentException.class,()->NativeDrinkAttestation.verify(b,m,CURRENT));}
    private static void malformed(Consumer<JsonObject> edit){JsonObject r=raw();edit.accept(r);byte[] b=bytes(r);JsonObject m=manifest(b,r);assertThrows(IllegalArgumentException.class,()->NativeDrinkAttestation.verify(b,m,CURRENT));}
    @Test void exactProjectionOwnHashAndDefensiveResult(){
        JsonObject r=raw();byte[] b=bytes(r);JsonObject m=manifest(b,r);var checked=NativeDrinkAttestation.verify(b,m,CURRENT);assertEquals(hash(b),checked.rawSnapshotSha256());
        row(m).addProperty("id","changed:outside");JsonObject copy=checked.manifest();row(copy).addProperty("id","changed:copy");assertEquals("minecraft:water_bucket",row(checked.manifest()).get("id").getAsString());
    }
    @Test void eitherHashLabelSpoofAndChangedRawBytesReject(){
        changed(m->m.getAsJsonObject("binding").addProperty("native_snapshot_sha256","b".repeat(64)));
        changed(m->m.getAsJsonObject("binding").addProperty("runtime_inputs_sha256","b".repeat(64)));
        JsonObject r=raw();byte[] b=bytes(r);JsonObject m=manifest(b,r);assertThrows(IllegalArgumentException.class,()->NativeDrinkAttestation.verify((new String(b,StandardCharsets.UTF_8)+" ").getBytes(StandardCharsets.UTF_8),m,CURRENT));
        changed(candidate->candidate.addProperty("native_snapshot_raw_sha256","b".repeat(64)));
    }
    @Test void everyDoseVectorAndCompletenessMutationRejects(){
        for(String field:new String[]{"sugar","alcohol","spice"})changed(m->row(m).getAsJsonObject("paid_intake").addProperty(field,1));
        changed(m->row(m).getAsJsonObject("paid_intake").getAsJsonObject("nutrients").addProperty("dairy",.06));
        changed(m->row(m).getAsJsonObject("paid_intake").addProperty("complete",false));
        changed(m->row(m).addProperty("spice",1));changed(m->row(m).addProperty("is_edible",true));
    }
    @Test void missingExtraDuplicateRowsAndFieldsReject(){
        changed(m->m.getAsJsonArray("native_drink_ingredients").remove(0));
        changed(m->m.getAsJsonArray("native_drink_ingredients").add(row(m).deepCopy()));
        changed(m->{JsonObject extra=row(m).deepCopy();extra.addProperty("id","minecraft:milk_bucket");extra.addProperty("template_basis","native_default");extra.addProperty("input_template","{Count:1b,id:\"minecraft:milk_bucket\"}");m.getAsJsonArray("native_drink_ingredients").add(extra);});
        changed(m->row(m).remove("paid_intake"));changed(m->row(m).addProperty("extra_unobserved",true));
    }
    @Test void changedNbtTemplateBasisAndOldWireReject(){
        changed(m->row(m).addProperty("input_template","{Count:1b,id:\"minecraft:water_bucket\",tag:{Purity:0}}"));
        changed(m->row(m).addProperty("template_basis","native_default"));changed(m->m.addProperty("schema","better_cooking:native_drink_ingredients.v1"));
        malformed(r->row(r).addProperty("default_stack",row(r).get("input_template").getAsString()));
    }
    @Test void malformedRawSnbtCountIdentityDuplicatesAndDepthReject(){
        for(String snbt:new String[]{"{Count:2b,id:\"minecraft:water_bucket\",tag:{Purity:3}}","{Count:1,id:\"minecraft:water_bucket\",tag:{Purity:3}}","{Count:1b,id:\"test:wrong\",tag:{Purity:3}}","{Count:1b,id:\"minecraft:water_bucket\",tag:{Purity:3,Purity:0}}","{Count:1b,id:\"minecraft:water_bucket\",tag:{Purity:3}","{Count:1b,id:\"minecraft:water_bucket\",tag:{Purity:3.0d}}"})
            malformed(r->row(r).addProperty("input_template",snbt));
        malformed(r->row(r).addProperty("input_template","{Count:1b,id:\"minecraft:water_bucket\",tag:{Purity:3,deep:"+"[".repeat(30)+"0"+"]".repeat(30)+"}}"));
    }
    @Test void malformedAndCoercedDosesRejectEvenWhenBothSidesAgree(){
        malformed(r->row(r).getAsJsonObject("paid_intake").addProperty("sugar","0"));
        malformed(r->row(r).getAsJsonObject("paid_intake").addProperty("alcohol",-1));
        malformed(r->row(r).getAsJsonObject("paid_intake").addProperty("spice",10001));
        malformed(r->row(r).getAsJsonObject("paid_intake").getAsJsonObject("nutrients").addProperty("spice",0));
        malformed(r->row(r).getAsJsonObject("paid_intake").add("nutrients",JsonNull.INSTANCE));
        malformed(r->row(r).getAsJsonObject("paid_intake").addProperty("complete","true"));
    }
    @Test void incompleteRemainsNullNotZeroAndMustMatchReason(){
        JsonObject r=raw(),paid=row(r).getAsJsonObject("paid_intake");paid.addProperty("complete",false);paid.addProperty("reason","actual API unavailable");for(String k:new String[]{"nutrients","sugar","alcohol","spice"})paid.add(k,JsonNull.INSTANCE);
        byte[] b=bytes(r);JsonObject m=manifest(b,r);NativeDrinkAttestation.verify(b,m,CURRENT);row(m).getAsJsonObject("paid_intake").addProperty("sugar",0);assertThrows(IllegalArgumentException.class,()->NativeDrinkAttestation.verify(b,m,CURRENT));
    }
    @Test void numericFormattingPreservesExactValueButNeverRoundsDifferentDoses(){
        JsonObject r=raw();row(r).getAsJsonObject("paid_intake").add("sugar",JsonParser.parseString("0.1"));byte[] b=bytes(r);JsonObject m=manifest(b,r);
        row(m).getAsJsonObject("paid_intake").add("sugar",JsonParser.parseString("1e-1"));NativeDrinkAttestation.verify(b,m,CURRENT);
        row(m).getAsJsonObject("paid_intake").add("sugar",JsonParser.parseString("0.10000000000000001"));assertThrows(IllegalArgumentException.class,()->NativeDrinkAttestation.verify(b,m,CURRENT));
    }
    @Test void duplicateRawKeysMalformedUtf8AndTrailingRootReject(){
        JsonObject r=raw();byte[] good=bytes(r);JsonObject m=manifest(good,r);
        for(byte[] b:new byte[][]{r.toString().replace("\"qualification\":false","\"qualification\":false,\"qualification\":false").getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xc3,0x28},(r+" {}").getBytes(StandardCharsets.UTF_8)}){
            m.getAsJsonObject("binding").addProperty("native_snapshot_sha256",hash(b));assertThrows(IllegalArgumentException.class,()->NativeDrinkAttestation.verify(b,m,CURRENT));
        }
    }
}
