package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VanillaTradeTemplatesTest {

    private static final Path ROOT = Path.of(System.getProperty("vtp.rootDir"));

    @Test
    void templateFilesAreTheConvertedVanillaData() throws IOException {
        Path folder = ROOT.resolve(VanillaTradeTemplates.FOLDER);
        Map<String, String> generated = new VanillaTradeTemplates(VanillaEnv.get()).generate();

        if (Boolean.parseBoolean(System.getProperty("vtp.writeTemplates"))) {
            Files.createDirectories(folder);
            for (Map.Entry<String, String> file : generated.entrySet()) {
                Files.writeString(folder.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
            }
        }

        assertEquals(14, generated.size(), "13 professions and the wandering trader");

        for (Map.Entry<String, String> file : generated.entrySet()) {
            Path path = folder.resolve(file.getKey());
            assertTrue(Files.exists(path), "missing template " + file.getKey());
            assertEquals(file.getValue(), Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n"), file.getKey());
        }

        try (var files = Files.list(folder)) {
            assertEquals(generated.size(), files.count(), "no stray files in the template folder");
        }
    }
}
