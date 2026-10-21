package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TradeControlsPayload(
        boolean allowSetLevel,
        boolean allowReroll,
        boolean allowView,
        int maxLevel
) implements CustomPayload {

    public static final CustomPayload.Id<TradeControlsPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VillagerTradingPlus.MOD_ID, "trade_controls"));

    public static final PacketCodec<PacketByteBuf, TradeControlsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN,
            TradeControlsPayload::allowSetLevel,
            PacketCodecs.BOOLEAN,
            TradeControlsPayload::allowReroll,
            PacketCodecs.BOOLEAN,
            TradeControlsPayload::allowView,
            PacketCodecs.VAR_INT,
            TradeControlsPayload::maxLevel,
            TradeControlsPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
