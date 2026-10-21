package com.lion.villagertradingplus.tradeoffers.gui;

import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TradeControlsPayload(
        boolean allowSetLevel,
        boolean allowReroll,
        boolean allowView,
        int maxLevel
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeControlsPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "trade_controls"));

    public static final StreamCodec<FriendlyByteBuf, TradeControlsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            TradeControlsPayload::allowSetLevel,
            ByteBufCodecs.BOOL,
            TradeControlsPayload::allowReroll,
            ByteBufCodecs.BOOL,
            TradeControlsPayload::allowView,
            ByteBufCodecs.VAR_INT,
            TradeControlsPayload::maxLevel,
            TradeControlsPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
