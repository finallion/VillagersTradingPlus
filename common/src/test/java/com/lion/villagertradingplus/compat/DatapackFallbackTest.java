package com.lion.villagertradingplus.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatapackFallbackTest {

    private static final Path JAVA = Path.of(System.getProperty("vtp.rootDir"))
            .resolve("common/src/main/java/com/lion/villagertradingplus/tradeoffers");

    @Test
    void unknownMapDecorationFallsBackToTheRedX() throws IOException {
        String source = Files.readString(JAVA.resolve("trades/JsonSellStructureMapTradeOffer.java"));

        assertFalse(source.contains("TradeParseException"));
        assertTrue(source.contains("using the red X"));
        assertTrue(source.contains("return MapIcon.Type.RED_X;"));
    }

    @Test
    void villagerTypeWithoutNamespaceIsNormalized() throws IOException {
        String source = Files.readString(JAVA.resolve("conditions/TradeConditions.java"));

        assertTrue(source.contains("types.add(id == null ? type : id.toString())"));
    }
}
