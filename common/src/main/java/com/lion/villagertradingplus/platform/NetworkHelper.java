package com.lion.villagertradingplus.platform;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControlsPayload;
import dev.architectury.injectables.annotations.ExpectPlatform;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class NetworkHelper {

    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerControlsReceiver(Consumer<TradeControlsPayload> receiver) {
        throw new AssertionError();
    }
}
