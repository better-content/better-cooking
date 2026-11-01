package com.bettercontent.bettercooking.stockpot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Verifies ACTUAL Mixin AP field output; refmap JSON is NOT where @Shadow field mappings are stored. */
public final class PreparedStockpotShadowApTest {
    private static final String OWNER="com/bettercontent/bettercooking/mixin/PreparedStockpotPlayerBreakGuardMixin";
    private static final Set<String> REQUIRED=Set.of(OWNER+" level f_9244_",OWNER+" player f_9245_");
    private static void require(List<String> actual) {
        for(String line:REQUIRED)if(!actual.contains(line))throw new AssertionError("Missing actual AP field mapping: "+line);
    }
    private static void reject(List<String> bad) {
        try { require(bad); }catch(AssertionError expected){return;}
        throw new AssertionError("Incomplete/wrong AP mapping accepted");
    }
    public static void main(String[] args)throws Exception {
        require(Files.readAllLines(Path.of(args[0])));
        reject(List.of()); // Empty refmap JSON / missing AP processing cannot satisfy the obligation.
        reject(List.of(OWNER+" level f_9244_"));
        reject(List.of(OWNER+" level f_9244_",OWNER+" player f_0000_"));
        System.out.println("PASS actual AP @Shadow field mappings +3 adverse mapping controls; Root must still prove reobfuscated member names and native application");
    }
}
