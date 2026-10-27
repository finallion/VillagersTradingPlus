package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MerchantPredicateTest {

    private static final Path ROOT = Path.of(System.getProperty("vtp.rootDir"));
    private static final Path JAVA = ROOT.resolve("common/src/main/java/com/lion/villagertradingplus/tradeoffers");
    private static final Path RESOURCES = ROOT.resolve("common/src/main/resources");

    private static String read(Path file) throws IOException {
        return Files.readString(file)
                .replace("\r\n", "\n");
    }

    @Test
    void predicateIsReadWithTheVanillaCodec() throws IOException {
        String predicate = read(JAVA.resolve("conditions/MerchantPredicate.java"));
        String codecs = read(JAVA.resolve("util/VanillaCodecs.java"));

        assertTrue(predicate.contains("FIELD = \"merchant_predicate\""));
        assertTrue(predicate.contains("VanillaCodecs.decode(LootItemCondition.DIRECT_CODEC, json, FIELD)"), "VanillaCodecs.decode(LootItemCondition.DIRECT_CODEC, json, FIELD)");
        assertTrue(predicate.contains("VanillaCodecs.validateForTrade(predicate, FIELD)"), "VanillaCodecs.validateForTrade(predicate, FIELD)");
        assertTrue(codecs.contains("RegistryOps.create(JsonOps.INSTANCE, lookup)"), "RegistryOps.create(JsonOps.INSTANCE, lookup)");
        assertTrue(codecs.contains("Validatable.<T>validatorForContext(LootContextParamSets.VILLAGER_TRADE)"), "Validatable.<T>validatorForContext(LootContextParamSets.VILLAGER_TRADE)");
    }

    @Test
    void predicateIsTestedAgainstTheMerchant() throws IOException {
        String predicate = read(JAVA.resolve("conditions/MerchantPredicate.java"));

        assertTrue(predicate.contains("VanillaTradeFactory.context(level, merchant, null, Optional.empty())"), "VanillaTradeFactory.context(level, merchant, null, Optional.empty())");
    }

    @Test
    void everyTradeTakesThePredicateWithoutTheConditionSwitch() throws IOException {
        String manager = read(JAVA.resolve("TradeOfferManager.java"));
        int predicate = manager.indexOf("            if (trade.has(MerchantPredicate.FIELD)) {");
        int conditionsEnd = manager.indexOf("TradeConditions.parse(trade.getAsJsonArray(\"conditions\"), orLogic)");

        assertTrue(predicate > conditionsEnd && conditionsEnd > 0);
        assertTrue(manager.contains("MerchantPredicate.parse(trade.get(MerchantPredicate.FIELD))"));
    }

    @Test
    void villagerTypeConditionIsGone() throws IOException {
        assertFalse(read(JAVA.resolve("conditions/TradeConditions.java")).contains("villager_type"));
        assertFalse(read(RESOURCES.resolve("assets/villagertradingplus/lang/en_us.json")).contains("villager_type"));
        assertFalse(read(RESOURCES.resolve("assets/villagertradingplus/lang/de_de.json")).contains("villager_type"));
        assertFalse(read(ROOT.resolve("README.md")).contains("villager_type"));
    }

    @Test
    void exampleUsesTheVanillaShapeOfThisVersion() throws IOException {
        String example = read(RESOURCES.resolve("data/villagertradingplus/available_trade_examples/all_conditions.json"));

        assertTrue(example.contains("\"merchant_predicate\": {"));
        assertFalse(example.contains("\"villager_type\""));
        assertTrue(example.contains("\"condition\": \"minecraft:entity_properties\""), "\"condition\": \"minecraft:entity_properties\"");
        assertTrue(example.contains("\"minecraft:villager/variant\": [ \"minecraft:desert\", \"minecraft:savanna\" ]"), "\"minecraft:villager/variant\": [ \"minecraft:desert\", \"minecraft:savanna\" ]");
    }
}
