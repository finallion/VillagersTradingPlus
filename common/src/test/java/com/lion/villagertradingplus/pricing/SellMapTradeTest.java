package com.lion.villagertradingplus.pricing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SellMapTradeTest {

    private static final Path ROOT = Path.of(System.getProperty("vtp.rootDir"));

    private static String source() throws IOException {
        return Files.readString(ROOT.resolve(
                        "common/src/main/java/com/lion/villagertradingplus/tradeoffers/trades/JsonSellStructureMapTradeOffer.java"
                ))
                .replace("\r\n", "\n");
    }

    @Test
    void vanillaFieldsAreReadWithTheVanillaCodecs() throws IOException {
        String code = source();

        assertTrue(code.contains("GIVES = \"gives\""));
        assertTrue(code.contains("GIVEN_ITEM_MODIFIER = \"given_item_modifier\""));
        assertTrue(code.contains("VanillaCodecs.decode(ItemStackTemplate.CODEC, json.get(GIVES), GIVES)"));
        assertTrue(code.contains("LootItemFunctions.CODEC,"));
        assertTrue(code.contains("VanillaCodecs.validateForTrade(modifier.value(), GIVEN_ITEM_MODIFIER)"));
    }

    @Test
    void offerRunsTheModifierLikeVanillaGetOffer() throws IOException {
        String code = source();

        assertTrue(code.contains("VanillaTradeFactory.context(level, trader, random, Optional.empty())"));
        assertTrue(code.contains("this.modifier.value().apply(this.gives.create(), context)"));
        assertTrue(code.contains("if (result.isEmpty()) {"));
        assertTrue(code.contains("result.remove(DataComponents.ADDITIONAL_TRADE_COST)"));
        assertTrue(code.contains("this.currency.getItem().getDefaultMaxStackSize()"));
        assertFalse(code.contains("MapItem."));
        assertFalse(code.contains("addTargetDecoration"));
    }

    @Test
    void structureIdBuildsTheVanillaCartographerChain() throws IOException {
        String code = source();

        assertTrue(code.contains("VANILLA_TRADE_SEARCH_RADIUS = 100"));
        assertTrue(code.contains("ExplorationMapFunction.makeExplorationMap(destination)"));
        assertTrue(code.contains(".setSearchRadius(VANILLA_TRADE_SEARCH_RADIUS)"));
        assertTrue(code.contains(".setSkipKnownStructures(true)"));
        assertTrue(code.contains("VillagerTrades.discardItemIfItsNot(VillagerTrades.anyValidMap())"));
        assertTrue(code.contains("FunctionUserBuilder.buildFunction(modifiers)"));
        assertTrue(code.contains("RENAMED_MAP_TAGS.getOrDefault(requested, requested)"));
        assertTrue(code.contains("\"minecraft:on_woodland_explorer_maps\", \"minecraft:on_woodland_mansion_maps\""));
    }

    @Test
    void vanillaPairsComeFromVanillaData() throws IOException {
        String code = source();

        for (String pair : new String[] {
                "on_woodland_mansion_maps\", new VanillaMap(Items.WOODLAND_MANSION_MAP, MapDecorationTypes.WOODLAND_MANSION",
                "on_ocean_monument_maps\", new VanillaMap(Items.OCEAN_MONUMENT_MAP, MapDecorationTypes.OCEAN_MONUMENT",
                "on_buried_trial_chambers_maps\", new VanillaMap(Items.BURIED_TRIAL_CHAMBERS_MAP, MapDecorationTypes.TRIAL_CHAMBERS",
                "on_taiga_village_maps\", new VanillaMap(Items.TAIGA_VILLAGE_MAP, MapDecorationTypes.TAIGA_VILLAGE",
                "on_desert_pyramid_maps\", new VanillaMap(Items.DESERT_PYRAMID_MAP, MapDecorationTypes.DESERT_PYRAMID",
                "on_ocean_ruin_warm_maps\", new VanillaMap(Items.WARM_OCEAN_RUINS_MAP, MapDecorationTypes.OCEAN_RUIN_WARM",
                "on_treasure_maps\", new VanillaMap(Items.BURIED_TREASURE_MAP, MapDecorationTypes.RED_X",
                "on_abandoned_camp_pale_garden\", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP"
        }) {
            assertTrue(code.contains(pair), pair);
        }
    }

    @Test
    void otherTagsGetTheRedX() throws IOException {
        String code = source();

        assertTrue(code.contains("if (vanilla != null) {\n            map.setMapDecoration(vanilla.decoration());"));
        assertTrue(code.contains("map.setMapDecoration(MapDecorationTypes.RED_X);"));
        assertTrue(code.contains("new ItemStackTemplate(item, itemName)"));
        assertTrue(code.contains("vanilla != null ? vanilla.item() : Items.FILLED_MAP"));
        assertTrue(code.contains("DataComponents.ITEM_NAME"));
        assertFalse(code.contains("CUSTOM_NAME"));
    }
}
