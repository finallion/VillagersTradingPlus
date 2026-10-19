package com.lion.villagertradingplus.platform;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

public class NetworkHelper {

    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayerEntity player, CustomPayload payload) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        throw new AssertionError();
    }
}
