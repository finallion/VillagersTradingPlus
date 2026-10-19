package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.VillagerTradingPlus;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

/// Wire format of a catalogue, sent as a sequence of [TradeCatalogPayload] slices.
public final class TradeCatalogPacket {

    public static final int MAX_SLICE_BYTES = 32 * 1024;

    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;

    private TradeCatalogPacket() {
    }

    public record Payload(int level, int maxLevel, List<CatalogEntry> entries, int omitted) {
    }

    public static List<TradeCatalogPayload> write(RegistryAccess registries, int level, int maxLevel,
                                                  List<CatalogEntry> entries, int alreadySkipped) {
        RegistryFriendlyByteBuf scratch = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        int written = 0;
        for (CatalogEntry entry : entries) {
            if (scratch.writerIndex() > MAX_TOTAL_BYTES) {
                break;
            }
            entry.write(scratch);
            written++;
        }

        RegistryFriendlyByteBuf body = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
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
        // An empty catalogue still sends one slice, because the client waits for the first slice.
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

    /// Joins the slices of one catalogue. Runs on the client thread only.
    public static final class Reassembler {

        private final List<byte[]> received = new ArrayList<>();
        private int expected;
        private int size;

        /// A slice with index 0 always starts over.
        @Nullable
        public Payload accept(TradeCatalogPayload slice, RegistryAccess registries) {
            if (slice.index() == 0) {
                reset();
                this.expected = slice.count();
            } else if (slice.index() != this.received.size() || slice.count() != this.expected) {
                VillagerTradingPlus.LOGGER.warn(
                        "Discarding an out-of-sequence trade catalogue slice ({} of {}, expected {} of {}).",
                        slice.index(), slice.count(), this.received.size(), this.expected);
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

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(all), registries);
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

    private static Payload read(RegistryFriendlyByteBuf buf) {
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
