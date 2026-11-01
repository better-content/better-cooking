package com.bettercontent.bettercooking.policy;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** The product's display version is not necessarily Forge's actual advertised mod version. */
class PinnedDependencyMetadataTest {
    @Test void farmersDelightRangeMatchesActualPinnedJarMetadataExactly() throws Exception {
        String path=System.getProperty("better_cooking.pinnedFarmersDelight");
        assertNotNull(path,"The actual explicitly bound upstream JAR is a required test input");
        String actual;
        try(JarFile jar=new JarFile(Path.of(path).toFile())) {
            String toml=new String(jar.getInputStream(jar.getJarEntry("META-INF/mods.toml")).readAllBytes(),StandardCharsets.UTF_8);
            var version=Pattern.compile("(?m)^version\\s*=\\s*\"([^\"]+)\"").matcher(toml);
            assertTrue(version.find(),"Pinned upstream must advertise its actual mod version");actual=version.group(1);
            if(actual.equals("${file.jarVersion}"))actual=jar.getManifest().getMainAttributes().getValue("Implementation-Version");
        }
        assertEquals("1.20.1-1.3.1",actual,"Version drift requires source/native review, not a widened dependency range");
        try(var input=getClass().getResourceAsStream("/META-INF/mods.toml")) {
            assertNotNull(input);String own=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            var dependency=Pattern.compile("(?s)\\[\\[dependencies\\.better_cooking]]\\s*modId=\"farmersdelight\".*?versionRange=\"([^\"]+)\"").matcher(own);
            assertTrue(dependency.find());assertEquals("["+actual+"]",dependency.group(1));
            assertNotEquals("[1.3.1,1.3.2)",dependency.group(1),"This incorrect range caused the retained first native startup failure");
        }
    }
}
