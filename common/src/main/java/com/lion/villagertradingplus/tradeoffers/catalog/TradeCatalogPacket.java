package com.lion.villagertradingplus.tradeoffers.catalog;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class TradeCatalogPacket {

    public static final Identifier CHANNEL = new Identifier("villagertradingplus", "trade_catalog");

    private static final int MAX_PAYLOAD_BYTES = 700_000;

    private TradeCatalogPacket() {}

    public record Payload(int level, int maxLevel, List<CatalogEntry> entries, int omitted) {
    }

    public static PacketByteBuf write(int level, int maxLevel, List<CatalogEntry> entries, int alreadySkipped) {
        // Rows go to a scratch buffer. A varint count cannot be patched.
        PacketByteBuf scratch = new PacketByteBuf(Unpooled.buffer());
        int written = 0;
        for (CatalogEntry entry : entries) {
            if (scratch.writerIndex() > MAX_PAYLOAD_BYTES) {
                break;
            }

            entry.write(scratch);
            written++;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(level);
        buf.writeVarInt(maxLevel);
        buf.writeVarInt(alreadySkipped + (entries.size() - written));
        buf.writeVarInt(written);
        buf.writeBytes(scratch);
        scratch.release();
        return buf;
    }

    public static Payload read(PacketByteBuf buf) {
        int level = buf.readVarInt();
        int maxLevel = buf.readVarInt();
        int omitted = buf.readVarInt();

        int count = buf.readVarInt();
        List<CatalogEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(CatalogEntry.read(buf));
        }
        return new Payload(level, maxLevel, List.copyOf(entries), omitted);
    }
}
