package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/// The satisfied flag is a snapshot, because weather and time conditions change.
public record ConditionInfo(Text description, boolean satisfied) {

    public void write(PacketByteBuf buf) {
        buf.writeText(this.description);
        buf.writeBoolean(this.satisfied);
    }

    public static ConditionInfo read(PacketByteBuf buf) {
        return new ConditionInfo(buf.readText(), buf.readBoolean());
    }
}
