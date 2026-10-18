package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/// `satisfied` is a snapshot, because weather and time change.
public record ConditionInfo(Component description, boolean satisfied) {

    public void write(RegistryFriendlyByteBuf buf) {
        ComponentSerialization.STREAM_CODEC.encode(buf, this.description);
        buf.writeBoolean(this.satisfied);
    }

    public static ConditionInfo read(RegistryFriendlyByteBuf buf) {
        return new ConditionInfo(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean());
    }
}
