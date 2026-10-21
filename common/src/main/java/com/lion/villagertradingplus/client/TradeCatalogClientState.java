package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/// The reply can arrive before the panel exists.
public final class TradeCatalogClientState {

    private static final TradeCatalogPacket.Reassembler REASSEMBLER = new TradeCatalogPacket.Reassembler();

    @Nullable
    private static TradeCatalogPacket.Payload current;

    private static boolean awaitingReply;

    private static int requestedLevel;

    private TradeCatalogClientState() {}

    public static void register() {
        NetworkHelper.registerCatalogReceiver(slice -> {
            if (Minecraft.getInstance().level == null) {
                return;
            }

            TradeCatalogPacket.Payload payload =
                    REASSEMBLER.accept(slice, Minecraft.getInstance().level.registryAccess());

            if (payload != null && answersRequest(payload)) {
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

    public static void requesting(int level) {
        awaitingReply = true;
        requestedLevel = level;
    }

    private static boolean answersRequest(TradeCatalogPacket.Payload payload) {
        return !awaitingReply || payload.level() == requestedLevel || requestedLevel > payload.maxLevel();
    }

    public static void clear() {
        current = null;
        awaitingReply = false;
    }
}
