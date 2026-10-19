package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/// One condition of a catalogued trade: what it requires and whether it holds currently.
/// The satisfied flag is a snapshot, because weather and time conditions change.
public record ConditionInfo(Text description, boolean satisfied) {

    public void write(RegistryByteBuf buf) {
        TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, this.description);
        buf.writeBoolean(this.satisfied);
    }

    public static ConditionInfo read(RegistryByteBuf buf) {
        return new ConditionInfo(TextCodecs.REGISTRY_PACKET_CODEC.decode(buf), buf.readBoolean());
    }
}
