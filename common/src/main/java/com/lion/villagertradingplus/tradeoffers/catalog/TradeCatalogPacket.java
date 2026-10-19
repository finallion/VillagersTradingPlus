package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.VillagerTradingPlus;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Wire format of the catalogue, sent from server to client on CHANNEL.
/// A whole tier ships in one packet. The payload limit is 1 MiB, measured before compression.
/// Rows go into a scratch buffer and are cut at MAX_PAYLOAD_BYTES, because heavy-NBT items vary in size.
/// The packet reports dropped rows to the player.
public final class TradeCatalogPacket {

    /// Payload budget per slice. It stays under the 1 MiB packet limit and keeps the framing cost small.
    public static final int MAX_SLICE_BYTES = 32 * 1024;

    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;

    private TradeCatalogPacket() {}

    public record Payload(int level, int maxLevel, List<CatalogEntry> entries, int omitted) {
    }

    /// Rows go into a scratch buffer first, because the count precedes them and a varint cannot be patched.
    public static List<TradeCatalogPayload> write(
            DynamicRegistryManager registries,
            int level,
            int maxLevel,
            List<CatalogEntry> entries,
            int alreadySkipped
    ) {
        RegistryByteBuf scratch = new RegistryByteBuf(Unpooled.buffer(), registries);
        int written = 0;
        for (CatalogEntry entry : entries) {
            if (scratch.writerIndex() > MAX_TOTAL_BYTES) {
                break;
            }

            entry.write(scratch);
            written++;
        }

        RegistryByteBuf body = new RegistryByteBuf(Unpooled.buffer(), registries);
        body.writeVarInt(level);
        body.writeVarInt(maxLevel);
        body.writeVarInt(alreadySkipped + (entries.size() - written));
        body.writeVarInt(written);
        body.writeBytes(scratch);
        scratch.release();

        byte[] all = new byte[body.readableBytes()];
        body.readBytes(all);
        body.release();

        return slice(all);
    }

    private static List<TradeCatalogPayload> slice(byte[] all) {
        // An empty catalogue is one slice, or the client waits for a transfer that never starts.
        int count = Math.max(1, (all.length + MAX_SLICE_BYTES - 1) / MAX_SLICE_BYTES);
        List<TradeCatalogPayload> slices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * MAX_SLICE_BYTES;
            int to = Math.min(all.length, from + MAX_SLICE_BYTES);
            byte[] part = new byte[to - from];
            System.arraycopy(all, from, part, 0, part.length);
            slices.add(new TradeCatalogPayload(i, count, part));
        }
        return slices;
    }

    /// Reassembles slices on the client thread. It needs no locking, because one trade screen is open at a time.
    public static final class Reassembler {

        private final List<byte[]> received = new ArrayList<>();
        private int expected;
        private int size;

        /// Index 0 restarts the transfer. Out-of-sequence slices drop the partial transfer.
        @Nullable
        public Payload accept(TradeCatalogPayload slice, DynamicRegistryManager registries) {
            if (slice.index() == 0) {
                reset();
                this.expected = slice.count();
            } else if (slice.index() != this.received.size() || slice.count() != this.expected) {
                VillagerTradingPlus.LOGGER.warn(
                        "Discarding an out-of-sequence trade catalogue slice ({} of {}, expected {} of {}).",
                        slice.index(),
                        slice.count(),
                        this.received.size(),
                        this.expected
                );
                reset();
                return emptyPayload();
            }

            this.size += slice.data().length;

            if (this.size > MAX_TOTAL_BYTES) {
                VillagerTradingPlus.LOGGER.warn("Discarding an oversized trade catalogue ({} bytes).", this.size);
                reset();
                return emptyPayload();
            }

            this.received.add(slice.data());

            if (this.received.size() < this.expected) {
                return null;
            }

            byte[] all = new byte[this.size];
            int at = 0;
            for (byte[] part : this.received) {
                System.arraycopy(part, 0, all, at, part.length);
                at += part.length;
            }
            reset();

            RegistryByteBuf buf = new RegistryByteBuf(Unpooled.wrappedBuffer(all), registries);
            try {
                return read(buf);
            } catch (Exception e) {
                VillagerTradingPlus.LOGGER.error("Could not read the trade catalogue.", e);
                return emptyPayload();
            }
        }

        private static Payload emptyPayload() {
            return new Payload(1, 1, List.of(), 0);
        }

        private void reset() {
            this.received.clear();
            this.expected = 0;
            this.size = 0;
        }
    }

    private static Payload read(RegistryByteBuf buf) {
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
