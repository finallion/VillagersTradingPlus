package com.lion.villagertradingplus.platform.neoforge;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/// NeoForge networking. Payload types register inside `RegisterPayloadHandlersEvent`,
/// so [#init] does nothing.
public final class NetworkHelperImpl {

    /// NeoForge refuses a client with a different protocol version.
    private static final String PROTOCOL_VERSION = "1";

    private static Consumer<TradeCatalogPayload> catalogReceiver;

    private NetworkHelperImpl() {}

    public static void init() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void registerCatalogReceiver(Consumer<TradeCatalogPayload> receiver) {
        catalogReceiver = receiver;
    }

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
