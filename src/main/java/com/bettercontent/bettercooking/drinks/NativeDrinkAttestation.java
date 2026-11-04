package com.bettercontent.bettercooking.drinks;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Pure closed projection verifier. Root owns physical CURRENT byte-domain and connection proof.
 * No I/O, native registries, installation, player, effects, resource lookup or fabricated defaults. */
public final class NativeDrinkAttestation {
    private static final Set<String> GROUPS=Set.of("proteins","grains","fruits","vegetables","dairy");
    private static final Set<String> DOSES=Set.of("complete","nutrients","sugar","alcohol","spice");
    private NativeDrinkAttestation() {}
    public static final class Validated {
        private final JsonObject manifest;private final String rawSnapshotSha256;
        private Validated(JsonObject manifest,String rawSnapshotSha256){this.manifest=manifest.deepCopy();this.rawSnapshotSha256=rawSnapshotSha256;}
        public JsonObject manifest(){return manifest.deepCopy();}
        public String rawSnapshotSha256(){return rawSnapshotSha256;}
    }
    public static Validated verify(byte[] rawSnapshot,JsonObject projected,String currentRuntimeDigest){
        if(rawSnapshot==null||rawSnapshot.length==0||rawSnapshot.length>64*1024*1024)throw bad("Raw snapshot bounds");
        byte[] snapshotBytes=rawSnapshot.clone();
        sha(currentRuntimeDigest);if(projected==null)throw bad("Missing projection");
        JsonObject manifest=projected.deepCopy();
        if(!NativeDrinkFactBinding.SCHEMA.equals(text(manifest,"schema")))throw bad("Only explicit v2 input-template schema supported");
        JsonObject binding=object(manifest,"binding");
        if(!binding.keySet().equals(Set.of("runtime_inputs_sha256","native_snapshot_sha256")))throw bad("Unexpected binding fields");
        String hash=hash(snapshotBytes);
        if(!currentRuntimeDigest.equals(text(binding,"runtime_inputs_sha256"))||!hash.equals(text(binding,"native_snapshot_sha256")))throw bad("Actual runtime/raw bytes mismatch");
        if(manifest.has("native_snapshot_raw_sha256")&&!hash.equals(text(manifest,"native_snapshot_raw_sha256")))throw bad("Spoofed secondary raw hash");
        JsonObject snapshot=parse(snapshotBytes);
        JsonObject context=object(snapshot,"native_drink_ingredients_context");
        if(!NativeDrinkFactBinding.SCHEMA.equals(text(context,"schema"))||!"observed_getters_unbound_runtime".equals(text(context,"status"))
                ||bool(context,"qualification")||bool(context,"auto_install"))throw bad("Invalid collector context");
        Map<String,JsonObject> original=rows(snapshot),candidate=rows(manifest);
        if(!original.keySet().equals(candidate.keySet()))throw bad("Missing/extra projected input rows");
        for(String id:original.keySet())if(!exact(original.get(id),candidate.get(id)))throw bad("Altered actual input row "+id);
        return new Validated(manifest,hash);
    }
    private static Map<String,JsonObject> rows(JsonObject root){
        JsonElement value=root.get("native_drink_ingredients");if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()==0||value.getAsJsonArray().size()>512)throw bad("Input row bounds/type");
        Map<String,JsonObject> result=new TreeMap<>();
        for(JsonElement raw:value.getAsJsonArray()){
            if(!raw.isJsonObject())throw bad("Row type");JsonObject row=raw.getAsJsonObject();
            if(!row.keySet().equals(Set.of("id","observation_kind","template_basis","input_template","item_class","is_edible","food_properties","nutrients","sugar","alcohol","spice","paid_intake","gaps")))throw bad("Missing/extra collector row fields");
            String id=text(row,"id");text(row,"item_class");bool(row,"is_edible");object(row,"gaps");
            JsonElement food=row.get("food_properties");if(!food.isJsonNull()){
                if(!food.isJsonObject()||!food.getAsJsonObject().keySet().equals(Set.of("hunger","saturation_modifier")))throw bad("Malformed raw food properties");
                for(String field:List.of("hunger","saturation_modifier"))number(food.getAsJsonObject().get(field));
                try{food.getAsJsonObject().get("hunger").getAsBigDecimal().toBigIntegerExact();}catch(ArithmeticException invalid){throw bad("Fractional raw hunger");}
            }
            if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||row.has("default_stack")||!"observed_template".equals(text(row,"observation_kind")))throw bad("Ambiguous/invalid input template");
            boolean water=id.equals("minecraft:water_bucket");
            if(!(water?"provider_purity3_variant":"native_default").equals(text(row,"template_basis")))throw bad("Template basis drift");
            Snbt.check(text(row,"input_template"),id,water);
            JsonObject paid=object(row,"paid_intake");boolean complete=bool(paid,"complete");
            Set<String> keys=new HashSet<>(DOSES);if(!complete)keys.add("reason");
            if(!paid.keySet().equals(keys))throw bad("Incomplete/extra paid-intake keys");
            if(complete){
                JsonObject nutrients=object(paid,"nutrients");
                for(var entry:nutrients.entrySet()){if(!GROUPS.contains(entry.getKey()))throw bad("Nonordinary nutrient");number(entry.getValue());}
                for(String field:List.of("sugar","alcohol","spice"))number(paid.get(field));
            }else{
                text(paid,"reason");for(String field:List.of("nutrients","sugar","alcohol","spice"))if(!paid.get(field).isJsonNull())throw bad("Unknown dose not null");
            }
            // Raw getters can differ from paid milk physiology, but must be actual typed fields.
            JsonElement nutrients=row.get("nutrients");if(nutrients==null)throw bad("Missing raw nutrients");
            if(!nutrients.isJsonNull()){if(!nutrients.isJsonObject())throw bad("Raw nutrient type");for(var entry:nutrients.getAsJsonObject().entrySet())number(entry.getValue());}
            for(String field:List.of("sugar","alcohol","spice")){JsonElement n=row.get(field);if(n==null)throw bad("Missing raw dose");if(!n.isJsonNull())number(n);}
            if(result.putIfAbsent(id,row)!=null)throw bad("Duplicate input ID");
        }
        return result;
    }
    private static void number(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw bad("Dose is not number");
        String token=value.getAsString();BigDecimal number;
        try{number=new BigDecimal(token);}catch(NumberFormatException invalid){throw bad("Nonfinite dose");}
        if(token.startsWith("-")||number.compareTo(BigDecimal.valueOf(10000))>0)throw bad("Negative/oversized dose");
    }
    private static boolean exact(JsonElement a,JsonElement b){
        if(a.isJsonObject()&&b.isJsonObject()){
            if(!a.getAsJsonObject().keySet().equals(b.getAsJsonObject().keySet()))return false;
            for(var e:a.getAsJsonObject().entrySet())if(!exact(e.getValue(),b.getAsJsonObject().get(e.getKey())))return false;return true;
        }
        if(a.isJsonArray()&&b.isJsonArray()){
            if(a.getAsJsonArray().size()!=b.getAsJsonArray().size())return false;
            for(int i=0;i<a.getAsJsonArray().size();i++)if(!exact(a.getAsJsonArray().get(i),b.getAsJsonArray().get(i)))return false;return true;
        }
        if(a.isJsonPrimitive()&&b.isJsonPrimitive()){
            JsonPrimitive x=a.getAsJsonPrimitive(),y=b.getAsJsonPrimitive();
            if(x.isNumber()&&y.isNumber())return new BigDecimal(x.getAsString()).compareTo(new BigDecimal(y.getAsString()))==0;
            return x.isNumber()==y.isNumber()&&x.isString()==y.isString()&&x.isBoolean()==y.isBoolean()&&x.getAsString().equals(y.getAsString());
        }
        return a.isJsonNull()&&b.isJsonNull();
    }
    private static JsonObject parse(byte[] bytes){
        try{
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try(JsonReader reader=new JsonReader(new StringReader(text))){reader.setLenient(false);JsonElement root=read(reader,0,new int[]{0});
                if(!root.isJsonObject()||reader.peek()!=JsonToken.END_DOCUMENT)throw bad("Raw root/trailing data");return root.getAsJsonObject();}
        }catch(IOException invalid){throw new IllegalArgumentException("Malformed raw JSON/UTF8",invalid);}
    }
    private static JsonElement read(JsonReader r,int depth,int[] nodes)throws IOException{
        if(depth>64||++nodes[0]>2_000_000)throw bad("Raw JSON structural bounds");
        return switch(r.peek()){
            case BEGIN_OBJECT->{JsonObject o=new JsonObject();r.beginObject();while(r.hasNext()){String key=r.nextName();if(o.has(key))throw bad("Duplicate raw JSON key");o.add(key,read(r,depth+1,nodes));}r.endObject();yield o;}
            case BEGIN_ARRAY->{JsonArray a=new JsonArray();r.beginArray();while(r.hasNext())a.add(read(r,depth+1,nodes));r.endArray();yield a;}
            case STRING->new JsonPrimitive(r.nextString());
            case NUMBER->{String n=r.nextString();try{new BigDecimal(n);}catch(NumberFormatException bad){throw bad("Invalid JSON number");}yield JsonParser.parseString(n);}
            case BOOLEAN->new JsonPrimitive(r.nextBoolean());
            case NULL->{r.nextNull();yield JsonNull.INSTANCE;}
            default->throw bad("Invalid JSON token");
        };
    }
    private static JsonObject object(JsonObject o,String k){JsonElement v=o.get(k);if(v==null||!v.isJsonObject())throw bad("Missing object "+k);return v.getAsJsonObject();}
    private static String text(JsonObject o,String k){JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().isEmpty())throw bad("Missing string "+k);return v.getAsString();}
    private static boolean bool(JsonObject o,String k){JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw bad("Missing boolean "+k);return v.getAsBoolean();}
    private static void sha(String s){if(s==null||!s.matches("[0-9a-f]{64}"))throw bad("Current actual runtime SHA required");}
    private static String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}

    /** Bounded SNBT syntax/type reader: no registry or Minecraft bootstrap; no regex tag stripping. */
    private static final class Snbt {
        private record Value(char type,Object data) {}
        private final String s;private int at,nodes;
        private Snbt(String s){this.s=s;}
        static void check(String text,String id,boolean water){
            if(text.length()>65536)throw bad("SNBT size");Snbt p=new Snbt(text);Value root=p.value(0);p.space();
            if(p.at!=text.length()||root.type!='C')throw bad("SNBT root/trailing data");
            @SuppressWarnings("unchecked") Map<String,Value> map=(Map<String,Value>)root.data;
            if(!Set.of("id","Count","tag","ForgeCaps").containsAll(map.keySet()))throw bad("Non-native serialized stack field");
            for(String compound:List.of("tag","ForgeCaps"))if(map.containsKey(compound)&&map.get(compound).type!='C')throw bad("Stack compound field type");
            Value item=map.get("id"),count=map.get("Count");
            if(item==null||item.type!='S'||!id.equals(item.data)||count==null||count.type!='b'||new BigDecimal(count.data.toString()).compareTo(BigDecimal.ONE)!=0)throw bad("SNBT item/count1 byte");
            if(water){Value tag=map.get("tag");if(tag==null||tag.type!='C')throw bad("Missing actual purity3 tag");
                @SuppressWarnings("unchecked") Map<String,Value> tags=(Map<String,Value>)tag.data;Value purity=tags.get("Purity");
                if(purity==null||purity.type!='i'||new BigDecimal(purity.data.toString()).compareTo(BigDecimal.valueOf(3))!=0)throw bad("Not provider purity3 variant");}
        }
        private void space(){while(at<s.length()&&Character.isWhitespace(s.charAt(at)))at++;}
        private char peek(){space();if(at>=s.length())throw bad("Truncated SNBT");return s.charAt(at);}
        private void take(char c){if(peek()!=c)throw bad("Malformed SNBT delimiter");at++;}
        private String word(){space();int start=at;while(at<s.length()&&"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._+-".indexOf(s.charAt(at))>=0)at++;if(start==at)throw bad("Invalid SNBT token");return s.substring(start,at);}
        private String string(){char quote=peek();if(quote!='\"'&&quote!='\'')return word();at++;StringBuilder out=new StringBuilder();boolean escape=false;
            while(at<s.length()){char c=s.charAt(at++);if(escape){if(c!=quote&&c!='\\')throw bad("SNBT escape");out.append(c);escape=false;}else if(c=='\\')escape=true;else if(c==quote)return out.toString();else out.append(c);}throw bad("Unclosed SNBT string");}
        private Value value(int depth){
            if(depth>24||++nodes>4096)throw bad("SNBT structural bounds");char c=peek();
            if(c=='{'){at++;Map<String,Value> map=new HashMap<>();if(peek()=='}'){at++;return new Value('C',map);}while(true){String k=string();take(':');if(map.putIfAbsent(k,value(depth+1))!=null)throw bad("Duplicate SNBT key");char end=peek();at++;if(end=='}')break;if(end!=',')throw bad("SNBT compound delimiter");}return new Value('C',map);}
            if(c=='['){at++;space();char array=0;if(at+1<s.length()&&"BIL".indexOf(s.charAt(at))>=0&&s.charAt(at+1)==';'){array=s.charAt(at);at+=2;}List<Value> list=new ArrayList<>();if(peek()==']'){at++;return new Value(array=='L'?'A':array==0?'L':array,list);}while(true){Value v=value(depth+1);if(array!=0&&v.type!=(array=='B'?'b':array=='I'?'i':'l'))throw bad("Typed array element");if(!list.isEmpty()&&list.get(0).type!=v.type)throw bad("Mixed SNBT list");list.add(v);char end=peek();at++;if(end==']')break;if(end!=',')throw bad("SNBT list delimiter");}return new Value(array=='L'?'A':array==0?'L':array,list);}
            if(c=='\"'||c=='\'')return new Value('S',string());String token=word();
            if(token.equalsIgnoreCase("true")||token.equalsIgnoreCase("false"))return new Value('b',token.equalsIgnoreCase("true")?"1":"0");
            char suffix=Character.toLowerCase(token.charAt(token.length()-1));String number=token;
            if("bslfd".indexOf(suffix)>=0)number=token.substring(0,token.length()-1);else suffix=token.indexOf('.')>=0||token.toLowerCase(Locale.ROOT).contains("e")?'d':'i';
            try{
                new BigDecimal(number);
                if("bsil".indexOf(suffix)>=0){
                    if(!number.matches("[-+]?(?:0|[1-9][0-9]*)"))return new Value('S',token);
                    switch(suffix){case 'b'->Byte.parseByte(number);case 's'->Short.parseShort(number);case 'i'->Integer.parseInt(number);case 'l'->Long.parseLong(number);default->throw bad("Numeric type");}
                }else if(suffix=='f'&&!Float.isFinite(Float.parseFloat(number))||suffix=='d'&&!Double.isFinite(Double.parseDouble(number)))throw bad("Nonfinite SNBT number");
                return new Value(suffix,number);
            }catch(NumberFormatException nonnumeric){return new Value('S',token);}
        }
    }
}
