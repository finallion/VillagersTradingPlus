package com.lion.villagertradingplus.platform;

import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControlsPacket;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

public class NetworkHelper {

    /// Forge creates channels before the registry locks.
    public static final List<Identifier> CHANNELS = List.of(TradeCatalogPacket.CHANNEL, TradeControlsPacket.CHANNEL);

    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerClientReceiver(Identifier channel, Consumer<PacketByteBuf> receiver) {
        throw new AssertionError();
    }
}
