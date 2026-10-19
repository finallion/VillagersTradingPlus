package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/// One slice of a trade catalogue on the wire.
/// A CustomPayloadS2CPacket may not exceed 1 MiB before compression, so the catalogue is cut into slices.
/// Slices arrive in order on a reliable channel. Index 0 starts a new catalogue and discards partial data.
/// See TradeCatalogPacket.Reassembler.
public record TradeCatalogPayload(int index, int count, byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<TradeCatalogPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VillagerTradingPlus.MOD_ID, "trade_catalog"));

    /// The decode bound is needed, because the length prefix comes from the wire.
    public static final PacketCodec<PacketByteBuf, TradeCatalogPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            TradeCatalogPayload::index,
            PacketCodecs.VAR_INT,
            TradeCatalogPayload::count,
            PacketCodecs.byteArray(TradeCatalogPacket.MAX_SLICE_BYTES),
            TradeCatalogPayload::data,
            TradeCatalogPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
