package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

/// Holds the catalogue that the server sent last.
/// The state is static, because the packet handler and the screen do not reference each other.
/// One slot is enough, because a player has one trade screen open at a time.
public final class TradeCatalogClientState {

    /// The reply is assembled here from slices. One screen runs one transfer, so a single instance is safe.
    private static final TradeCatalogPacket.Reassembler REASSEMBLER = new TradeCatalogPacket.Reassembler();

    @Nullable
    private static TradeCatalogPacket.Payload current;

    private static boolean awaitingReply;

    private TradeCatalogClientState() {
    }

    public static void register() {
        NetworkHelper.registerCatalogReceiver(slice -> {
            // Item stacks resolve components against the client world registries.
            if (MinecraftClient.getInstance().world == null) {
                return;
            }

            TradeCatalogPacket.Payload payload =
                    REASSEMBLER.accept(slice, MinecraftClient.getInstance().world.getRegistryManager());

            // Nothing to show until the last slice lands; the panel keeps its placeholder until then.
            if (payload != null) {
                current = payload;
                awaitingReply = false;
            }
        });
    }

    @Nullable
    public static TradeCatalogPacket.Payload current() {
        return current;
    }

    /// An outstanding-request flag, because the reply level can differ from the requested level.
    public static boolean isLoading() {
        return current == null || awaitingReply;
    }

    public static void requesting() {
        awaitingReply = true;
    }

    public static void clear() {
        current = null;
        awaitingReply = false;
    }
}
