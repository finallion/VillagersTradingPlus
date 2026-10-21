package com.lion.villagertradingplus.tradeoffers.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class TradeControlsPacket {

    public static final Identifier CHANNEL = new Identifier("villagertradingplus", "trade_controls");

    private TradeControlsPacket() {}

    public record Payload(boolean allowSetLevel, boolean allowReroll, boolean allowView, int maxLevel) {
    }

    public static PacketByteBuf write(Payload payload) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(payload.allowSetLevel());
        buf.writeBoolean(payload.allowReroll());
        buf.writeBoolean(payload.allowView());
        buf.writeVarInt(payload.maxLevel());
        return buf;
    }

    public static Payload read(PacketByteBuf buf) {
        boolean allowSetLevel = buf.readBoolean();
        boolean allowReroll = buf.readBoolean();
        boolean allowView = buf.readBoolean();
        int maxLevel = buf.readVarInt();
        return new Payload(allowSetLevel, allowReroll, allowView, maxLevel);
    }
}
