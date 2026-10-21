package com.lion.villagertradingplus.platform.fabric;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControlsPayload;
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
        PayloadTypeRegistry.clientboundPlay().register(TradeControlsPayload.ID, TradeControlsPayload.CODEC);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(
                TradeCatalogPayload.ID,
                (payload, context) -> context.client().execute(() -> receiver.accept(payload))
        );
    }

    public static void registerControlsReceiver(Consumer<TradeControlsPayload> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(
                TradeControlsPayload.ID,
                (payload, context) -> context.client().execute(() -> receiver.accept(payload))
        );
    }
}
