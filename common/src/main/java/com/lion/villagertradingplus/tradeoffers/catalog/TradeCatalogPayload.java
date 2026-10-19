package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// One slice of a catalogue. A payload packet may not exceed 1 MiB before compression.
public record TradeCatalogPayload(int index, int count, byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeCatalogPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "trade_catalog"));

    /// Bounded on decode, because the length prefix comes from the wire.
    public static final StreamCodec<FriendlyByteBuf, TradeCatalogPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            TradeCatalogPayload::index,
            ByteBufCodecs.VAR_INT,
            TradeCatalogPayload::count,
            ByteBufCodecs.byteArray(TradeCatalogPacket.MAX_SLICE_BYTES),
            TradeCatalogPayload::data,
            TradeCatalogPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
