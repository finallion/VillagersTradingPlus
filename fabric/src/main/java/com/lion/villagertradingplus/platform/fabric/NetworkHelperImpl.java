package com.lion.villagertradingplus.platform.fabric;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Consumer;

public final class NetworkHelperImpl {

    private NetworkHelperImpl() {}

    /// Both sides register payload types from common init. An unregistered type is dropped as unknown.
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TradeCatalogPayload.ID, TradeCatalogPayload.CODEC);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        // The payload is a decoded immutable record. No copy is needed.
        ClientPlayNetworking.registerGlobalReceiver(
                TradeCatalogPayload.ID,
                (payload, context) -> context.client().execute(() -> receiver.accept(payload))
        );
    }
}
