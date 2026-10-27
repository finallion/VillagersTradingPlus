package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

public final class BootstrapSmokeLogic {

    private BootstrapSmokeLogic() {}

    public static void run() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        assertNotNull(TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(VillagerProfession.FARMER));
    }
}
