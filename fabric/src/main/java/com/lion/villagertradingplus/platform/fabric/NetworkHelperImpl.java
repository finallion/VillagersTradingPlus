package com.lion.villagertradingplus.platform.fabric;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

public final class NetworkHelperImpl {

    private NetworkHelperImpl() {}

    /// Both sides register payload types, because a client without the codec drops the payload as unknown.
    public static void init() {
        PayloadTypeRegistry.playS2C().register(TradeCatalogPayload.ID, TradeCatalogPayload.CODEC);
    }

    public static void sendToPlayer(ServerPlayerEntity player, CustomPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(
                TradeCatalogPayload.ID,
                (payload, context) -> context.client().execute(() -> receiver.accept(payload))
        );
    }
}
