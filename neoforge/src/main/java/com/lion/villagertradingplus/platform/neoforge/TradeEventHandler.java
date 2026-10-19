package com.lion.villagertradingplus.platform.neoforge;

import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/// NeoForge rebuilds the vanilla trades from these events.
public final class TradeEventHandler {

    private TradeEventHandler() {
    }

    public static void onVillagerTrades(VillagerTradesEvent event) {
        TradeMerger.applyStaged(event.getType(), event.getTrades());
    }

    public static void onWandererTrades(WandererTradesEvent event) {
        WanderingTraderTradeLoader.applyStagedTo(1, event.getGenericTrades());
        WanderingTraderTradeLoader.applyStagedTo(2, event.getRareTrades());
    }
}
