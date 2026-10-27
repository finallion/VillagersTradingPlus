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
    void villagerTypeItemSkipsOnlyTheUnknownEntry() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "villagerTypeItemSkipsOnlyTheUnknownEntry");
    }

    @Test
    void secondSlotCostsStayWithinTheStackSize() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "secondSlotCostsStayWithinTheStackSize");
    }

    @Test
    void mapDecorationDefaultsToRedX() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "mapDecorationDefaultsToRedX");
    }

    @Test
    void costsMayExceedAStackSizeOfOne() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "costsMayExceedAStackSizeOfOne");
    }

    @Test
    void resultsStayClampedToTheStackSize() throws Throwable {
        KnotHarness.run(VtpTradeTypesLogic.class, "resultsStayClampedToTheStackSize");
    }
}
