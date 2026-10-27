package com.lion.villagertradingplus.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Shadows the platform stub in tests, which run without the Architectury transformer.
public class ConfigDirectory {

    public static Path getConfigDirectory() {
        try {
            return Files.createTempDirectory("vtp-test-config");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
