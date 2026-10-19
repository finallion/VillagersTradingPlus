package com.lion.villagertradingplus.platform.forge;

import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;

/// Forge fires the trade events once per server start, not on reload.
public final class ForgeTradeEvents {

    private static volatile boolean serverRunning;

    private ForgeTradeEvents() {
    }

    public static boolean isServerRunning() {
        return serverRunning;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ForgeTradeEvents::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener(ForgeTradeEvents::onWandererTrades);
        MinecraftForge.EVENT_BUS.addListener(ForgeTradeEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ForgeTradeEvents::onServerStopped);
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        if (TradeMerger.applyStaged(event.getType(), event.getTrades())) {
            TradeMerger.markTouched(event.getType());
        }
    }

    private static void onWandererTrades(WandererTradesEvent event) {
        WanderingTraderTradeLoader.applyStagedTo(1, event.getGenericTrades());
        WanderingTraderTradeLoader.applyStagedTo(2, event.getRareTrades());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        serverRunning = true;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        serverRunning = false;
    }
}
