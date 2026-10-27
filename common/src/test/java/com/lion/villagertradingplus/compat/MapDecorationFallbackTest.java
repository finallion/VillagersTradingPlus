package com.lion.villagertradingplus.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MapDecorationFallbackTest {

    @Test
    void unknownMapDecorationFallsBackToTheRedX() throws IOException {
        Path file = Path.of(System.getProperty("vtp.rootDir"))
                .resolve("common/src/main/java/com/lion/villagertradingplus/tradeoffers/trades/JsonSellStructureMapTradeOffer.java");
        String source = Files.readString(file);

        assertFalse(source.contains("TradeParseException"));
        assertTrue(source.contains("using the red X"));
    }
}
