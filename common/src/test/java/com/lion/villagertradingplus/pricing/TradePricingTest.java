package com.lion.villagertradingplus.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lion.villagertradingplus.tradeoffers.TradePricing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TradePricingTest {

    private static final Path JAVA = Path.of(System.getProperty("vtp.rootDir"))
            .resolve("common/src/main/java/com/lion/villagertradingplus");

    private static String source(String relative) throws IOException {
        return Files.readString(JAVA.resolve(relative));
    }

    @Test
    void defaultConfigChangesNoPrice() {
        for (long time = 0; time < 24000; time += 1500) {
            assertEquals(1.0f, TradePricing.factor(1.0f, false, 0.15f, time));
        }

        for (int count = 1; count <= 64; count++) {
            assertEquals(count, TradePricing.scaleCount(count, 64, 1.0f));
        }
    }

    @Test
    void costFactorTwoScalesOnce() {
        assertEquals(2.0f, TradePricing.factor(2.0f, false, 0.15f, 6000));
        assertEquals(10, TradePricing.scaleCount(5, 64, 2.0f));
        assertEquals(64, TradePricing.scaleCount(40, 64, 2.0f));
        assertEquals(1, TradePricing.scaleCount(1, 64, 0.1f));
    }

    @Test
    void timeOfDayStaysWithinTheVariance() {
        assertEquals(1.0f, TradePricing.factor(1.0f, true, 0.15f, 0), 1.0e-6f);
        assertEquals(1.15f, TradePricing.factor(1.0f, true, 0.15f, 6000), 1.0e-6f);
        assertEquals(0.85f, TradePricing.factor(1.0f, true, 0.15f, 18000), 1.0e-6f);
    }

    @Test
    void catalogUsesTheSameCountAndFactorAsTheOffer() throws IOException {
        String catalog = source("tradeoffers/catalog/CatalogBuilder.java");
        String expansion = source("tradeoffers/catalog/CatalogExpansion.java");
        String pricing = source("tradeoffers/TradePricing.java");

        assertTrue(catalog.contains("TradePricing.scaleCount(rawPrice, maxCount, this.composedPriceFactor)"));
        assertTrue(expansion.contains("out.setPriceFactor(TradePricing.priceFactor(merchant))"));
        assertTrue(pricing.contains("scaleCount(first.count()") || pricing.contains("scaleCount(first.getCount()"));
        assertTrue(pricing.contains("float factor = priceFactor(merchant)"));
    }

    @Test
    void noTradeFactoryScalesPrices() throws IOException {
        assertFalse(Files.exists(JAVA.resolve("tradeoffers/PricingTradeFactory.java")));
        assertFalse(source("tradeoffers/TradeOfferManager.java").contains("Pricing"));
        assertFalse(source("tradeoffers/trades/JsonWeightedPoolTradeOffer.java").contains("Pricing"));
        assertFalse(source("tradeoffers/catalog/CatalogExpansion.java").contains("PricingTradeFactory"));
        assertFalse(source("tradeoffers/trades/JsonTradeOffer.java").contains("trade_price_multiplier_scale"));
    }

    @Test
    void everyPoolDrawIsPricedOnce() throws IOException {
        String mixin = source("mixin/MerchantEntityMixin.java");
        String config = Files.readString(Path.of(System.getProperty("vtp.rootDir"))
                .resolve("common/src/main/resources/villagertradingplus-common.mixins.json"));

        assertTrue(mixin.contains("method = \"fillRecipesFromPool\""));
        assertEquals(1, mixin.split("TradePricing\\.applyFrom", -1).length - 1);
        assertTrue(config.contains("\"MerchantEntityMixin\""));
        assertFalse(source("mixin/VillagerEntityMixin.java").contains("TradePricing"));
    }

    @Test
    void wanderingTraderRareOfferIsPricedOnce() throws IOException {
        String mixin = source("mixin/WanderingTraderMixin.java");

        String merchant = source("mixin/MerchantEntityMixin.java");

        assertFalse(mixin.contains("@ModifyArg"));
        assertTrue(mixin.contains("@Inject(method = \"fillRecipes\", at = @At(\"TAIL\"))"));
        assertEquals(2, mixin.split("TradePricing\\.applyFrom", -1).length - 1);
        assertTrue(merchant.contains("instanceof WanderingTraderEntity"));
    }
}
