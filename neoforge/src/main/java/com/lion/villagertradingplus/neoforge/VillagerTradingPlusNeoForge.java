package com.lion.villagertradingplus.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.VillagerTradingPlusClient;
import com.lion.villagertradingplus.platform.neoforge.DefaultTradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.NetworkHelperImpl;
import com.lion.villagertradingplus.platform.neoforge.TradeEventHandler;
import com.lion.villagertradingplus.platform.neoforge.TradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.WanderingTraderTradeOfferResourceListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@Mod(VillagerTradingPlus.MOD_ID)
public class VillagerTradingPlusNeoForge {

    /// The mod bus fires payload registration once. The game bus fires the reload listener hook on every datapack load.
    public VillagerTradingPlusNeoForge(IEventBus modEventBus) {
        VillagerTradingPlus.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            VillagerTradingPlusClient.init();
        }

        modEventBus.addListener(NetworkHelperImpl::register);
        NeoForge.EVENT_BUS.addListener(VillagerTradingPlusNeoForge::registerResourceReloader);
        NeoForge.EVENT_BUS.addListener(TradeEventHandler::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(TradeEventHandler::onWandererTrades);
    }

    private static void registerResourceReloader(AddReloadListenerEvent event) {
        event.addListener(new DefaultTradeOfferResourceListener());
        event.addListener(new TradeOfferResourceListener());
        event.addListener(new WanderingTraderTradeOfferResourceListener());
    }
}
