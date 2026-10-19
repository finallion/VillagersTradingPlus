package com.lion.villagertradingplus.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.VillagerTradingPlusClient;
import com.lion.villagertradingplus.platform.neoforge.DefaultTradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.NetworkHelperImpl;
import com.lion.villagertradingplus.platform.neoforge.TradeEventHandler;
import com.lion.villagertradingplus.platform.neoforge.TradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.WanderingTraderTradeOfferResourceListener;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

@Mod(VillagerTradingPlus.MOD_ID)
public class VillagerTradingPlusNeoForge {

    /// The mod bus fires payload registration once. The game bus fires the reload listener hook on every datapack load.
    public VillagerTradingPlusNeoForge(IEventBus modEventBus) {
        VillagerTradingPlus.init();
        WanderingTraderTradeLoader.setServedByLoaderEvent(true);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            VillagerTradingPlusClient.init();
        }

        modEventBus.addListener(NetworkHelperImpl::register);
        NeoForge.EVENT_BUS.addListener(VillagerTradingPlusNeoForge::registerResourceReloader);
        NeoForge.EVENT_BUS.addListener(TradeEventHandler::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(TradeEventHandler::onWandererTrades);
    }

    // Listeners are keyed, so NeoForge can order them. The ids mirror the Fabric ids from getFabricId().
    private static void registerResourceReloader(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.of(VillagerTradingPlus.MOD_ID, "default_villager_data_loader"),
                new DefaultTradeOfferResourceListener()
        );
        event.addListener(
                Identifier.of(VillagerTradingPlus.MOD_ID, "villager_data_loader"),
                new TradeOfferResourceListener()
        );
        event.addListener(
                Identifier.of(VillagerTradingPlus.MOD_ID, "wandering_trader_data_loader"),
                new WanderingTraderTradeOfferResourceListener()
        );
    }
}
