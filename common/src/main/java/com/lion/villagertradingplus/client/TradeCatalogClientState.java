package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/// Holds the catalogue that the server last sent. The reply can arrive before the panel exists.
public final class TradeCatalogClientState {

    private static final TradeCatalogPacket.Reassembler REASSEMBLER = new TradeCatalogPacket.Reassembler();

    @Nullable
    private static TradeCatalogPacket.Payload current;

    private static boolean awaitingReply;

    private TradeCatalogClientState() {}

    public static void register() {
        NetworkHelper.registerCatalogReceiver(slice -> {
            // Item stacks resolve their components against the client world registries.
            if (Minecraft.getInstance().level == null) {
                return;
            }

            TradeCatalogPacket.Payload payload =
                    REASSEMBLER.accept(slice, Minecraft.getInstance().level.registryAccess());

            // The panel keeps its placeholder until the last slice arrives.
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

    /// The server clamps the tier, so a level check cannot detect the reply.
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
