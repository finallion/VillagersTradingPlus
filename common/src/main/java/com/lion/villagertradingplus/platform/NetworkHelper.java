package com.lion.villagertradingplus.platform;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import dev.architectury.injectables.annotations.ExpectPlatform;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/// Server-to-client messaging behind `@ExpectPlatform`, like [ConfigDirectory].
public class NetworkHelper {

    /// Runs before the first send.
    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new AssertionError();
    }

    /// Client only. The receiver runs on the client thread.
    @ExpectPlatform
    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        throw new AssertionError();
    }
}
