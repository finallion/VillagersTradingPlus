package com.lion.villagertradingplus.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogStructureSearchTest {

    private static final Path ROOT = Path.of(System.getProperty("vtp.rootDir")).resolve("common/src/main");

    @Test
    void catalogUnwrapsTypedWrapperFactoriesBeforeSampling() throws IOException {
        String source = Files.readString(ROOT.resolve("java/com/lion/villagertradingplus/tradeoffers/catalog/CatalogBuilder.java"));
        String mixins = Files.readString(ROOT.resolve("resources/villagertradingplus-common.mixins.json"));

        assertTrue(source.contains("factory instanceof TypedWrapperFactoryAccessor typed"));
        assertTrue(source.indexOf("TypedWrapperFactoryAccessor typed") < source.indexOf("isStructureMapFactory(factory)"));
        assertTrue(mixins.contains("\"TypedWrapperFactoryAccessor\""));
    }
}
