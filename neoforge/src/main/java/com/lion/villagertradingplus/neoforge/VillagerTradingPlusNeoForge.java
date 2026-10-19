package com.lion.villagertradingplus.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.VillagerTradingPlusClient;
import com.lion.villagertradingplus.platform.neoforge.DefaultTradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.NetworkHelperImpl;
import com.lion.villagertradingplus.platform.neoforge.TradeOfferResourceListener;
import com.lion.villagertradingplus.platform.neoforge.WanderingTraderTradeOfferResourceListener;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

@Mod(VillagerTradingPlus.MOD_ID)
public class VillagerTradingPlusNeoForge {

    public VillagerTradingPlusNeoForge(IEventBus modEventBus) {
        VillagerTradingPlus.init();

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            VillagerTradingPlusClient.init();
        }

        modEventBus.addListener(NetworkHelperImpl::register);
        NeoForge.EVENT_BUS.addListener(VillagerTradingPlusNeoForge::registerResourceReloader);
    }

    private static void registerResourceReloader(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "default_villager_data_loader"),
                new DefaultTradeOfferResourceListener()
        );
        event.addListener(
                Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "villager_data_loader"),
                new TradeOfferResourceListener()
        );
        event.addListener(
                Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "wandering_trader_data_loader"),
                new WanderingTraderTradeOfferResourceListener()
        );
    }
}
