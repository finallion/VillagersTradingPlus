package com.lion.villagertradingplus.platform;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

/// Minimal server-to-client messaging. Uses the same @ExpectPlatform split as RegistryHelper and ConfigDirectory.
public class NetworkHelper {

    /// Registers every payload type the mod sends. Must run before the first send.
    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayerEntity player, CustomPayload payload) {
        throw new AssertionError();
    }

    /// Client only. The receiver runs on the client thread.
    @ExpectPlatform
    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        throw new AssertionError();
    }
}
