package com.lion.villagertradingplus.platform.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/// NeoForge networking. Payload types register in RegisterPayloadHandlersEvent on the mod bus, so init() does nothing.
public final class NetworkHelperImpl {

    /// Bump it when the payload format changes incompatibly. NeoForge refuses mismatched clients.
    private static final String PROTOCOL_VERSION = "1";

    private static Consumer<TradeCatalogPayload> catalogReceiver;

    private NetworkHelperImpl() {
    }

    /// Registration happens in register(RegisterPayloadHandlersEvent).
    public static void init() {
    }

    public static void sendToPlayer(ServerPlayerEntity player, CustomPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        catalogReceiver = receiver;
    }

    /// Both sides register the handler, because NeoForge rejects a client without it. Only a client gets a receiver.
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(TradeCatalogPayload.ID, TradeCatalogPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Consumer<TradeCatalogPayload> receiver = catalogReceiver;
                    if (receiver != null) {
                        receiver.accept(payload);
                    }
                }));
        VillagerTradingPlus.LOGGER.debug("Registered the trade catalogue payload.");
    }
}
