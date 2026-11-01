package com.bettercontent.bettercooking.fixture.tests.discovery;

import com.bettercontent.bettercooking.fixture.NativeCatalogueCollector;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Path;

/** Separate discovery namespace: test framework success means collection completed, never qualification. */
@GameTestHolder("better_cooking_discovery")
@PrefixGameTestTemplate(false)
public final class NativeDiscoveryTests {
    private NativeDiscoveryTests() {}

    @GameTest(templateNamespace = "better_cooking", template = "native_discovery", timeoutTicks = 1200)
    public static void collectDiscoveryCatalogue(GameTestHelper helper) {
        String destination = System.getProperty("better_cooking.nativeCatalogueOutput");
        helper.assertTrue(Boolean.getBoolean("better_cooking.discoveryOnly"),"Discovery requires inert policy execution");
        helper.assertTrue(destination != null && !destination.isBlank(),"Explicit new absolute nativeCatalogueOutput required");
        try {
            NativeCatalogueCollector.collect(helper.getLevel().getServer(),null,Path.of(destination));
            System.out.println("BETTER_COOKING_NATIVE_CATALOGUE status=discovery qualification=false output="+destination);
            // Let Forge's ordinary GameTest runner finish and shut the dedicated test server down.
            helper.succeed();
        } catch (Exception failure) {
            throw new IllegalStateException("Native discovery collection failed; no qualification asserted",failure);
        }
    }
}
