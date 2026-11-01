package com.bettercontent.bettercooking.acquisition;
import java.util.*;
public final class CropAliasPlanControls {
    public static void main(String[] args) { run(); }
    public static void run() {
        Map<String,String> authored = Map.of("kc:tomato","fd:tomato", "kc:rice", "fd:rice", "kc:rice_panicle", "fd:rice_panicle");
        int assertions=0;
        for (int count : new int[]{1,4,5,64,257,Integer.MAX_VALUE}) {
            var rewrite=CropAliasPlan.plan("kc:rice",count,id->Optional.ofNullable(authored.get(id))).orElseThrow();
            if(rewrite.count()!=count||!rewrite.canonicalId().equals("fd:rice"))throw new AssertionError("Count/identity changed"); assertions++;
        }
        for(String id:List.of("kc:unreviewed", "mod:existing_stock", "fd:rice")) {
            if(CropAliasPlan.plan(id,5,key->Optional.ofNullable(authored.get(key))).isPresent())throw new AssertionError("Unauthored conversion"); assertions++;
        }
        if(CropAliasPlan.plan("kc:rice",4,key->Optional.empty()).isPresent())throw new AssertionError("Unqualified lookup must be identity");assertions++;
        if(CropAliasPlan.plan("kc:rice",0,key->{throw new AssertionError("Empty calls authority");}).isPresent())throw new AssertionError();assertions++;
        if(CropAliasPlan.plan("fd:rice",4,key->Optional.of(key)).isPresent())throw new AssertionError("Identity alias copied");assertions++;
        System.out.println("Crop alias/count plan controls: "+assertions+" assertions passed (not native stack acceptance)");
    }
}
