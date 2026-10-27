package com.lion.villagertradingplus.vanilla;

import org.junit.jupiter.api.Test;

class VtpTradeTypesTest {

    @Test
    void processItemPutsEmeraldsInSlotA() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "processItemPutsEmeraldsInSlotA");
    }

    @Test
    void sellPotionExpressesTheTippedArrowTrade() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "sellPotionExpressesTheTippedArrowTrade");
    }

    @Test
    void sellPotionKeepsTheWaterFilterOnBottles() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "sellPotionKeepsTheWaterFilterOnBottles");
    }

    @Test
    void sellDyedArmorBlendsUpToThreeDyes() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "sellDyedArmorBlendsUpToThreeDyes");
    }

    @Test
    void sellDyedArmorDyesHorseArmorLikeVanilla() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "sellDyedArmorDyesHorseArmorLikeVanilla");
    }

    @Test
    void sellDyedArmorLeavesOtherItemsAlone() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "sellDyedArmorLeavesOtherItemsAlone");
    }

    @Test
    void villagerTypeItemFollowsTheVillagerType() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "villagerTypeItemFollowsTheVillagerType");
    }

    @Test
    void villagerTypeItemRejectsUnknownTypes() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "villagerTypeItemRejectsUnknownTypes");
    }

    @Test
    void mapDecorationDefaultsToRedX() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "mapDecorationDefaultsToRedX");
    }

    @Test
    void mapNameIsAnItemName() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "mapNameIsAnItemName");
    }

    @Test
    void costsMayExceedAStackSizeOfOne() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "costsMayExceedAStackSizeOfOne");
    }

    @Test
    void resultsStayClampedToTheStackSize() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "resultsStayClampedToTheStackSize");
    }

    @Test
    void rebalancedVillagerTablesGetTheSameTrades() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "rebalancedVillagerTablesGetTheSameTrades");
    }

    @Test
    void rebalancedWorldsPickTheRebalancedTable() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "rebalancedWorldsPickTheRebalancedTable");
    }

    @Test
    void rebalancedWandererPoolsTakeCommonAndRare() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "rebalancedWandererPoolsTakeCommonAndRare");
    }

    @Test
    void villagerTypeItemSkipsOnlyTheUnknownEntry() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "villagerTypeItemSkipsOnlyTheUnknownEntry");
    }

    @Test
    void secondSlotCostsStayWithinTheStackSize() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "secondSlotCostsStayWithinTheStackSize");
    }

    @Test
    void rebalancedTableOfALateProfessionDoesNotGrow() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "rebalancedTableOfALateProfessionDoesNotGrow");
    }

    @Test
    void rebalancedWandererBuyingPoolIsACatalogPage() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "rebalancedWandererBuyingPoolIsACatalogPage");
    }
}
