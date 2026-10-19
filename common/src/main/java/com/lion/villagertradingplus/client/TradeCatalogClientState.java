package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

public final class TradeCatalogClientState {

    private static final TradeCatalogPacket.Reassembler REASSEMBLER = new TradeCatalogPacket.Reassembler();

    @Nullable
    private static TradeCatalogPacket.Payload current;

    private static boolean awaitingReply;

    private TradeCatalogClientState() {}

    public static void register() {
        NetworkHelper.registerCatalogReceiver(slice -> {
            if (MinecraftClient.getInstance().world == null) {
                return;
            }

            TradeCatalogPacket.Payload payload =
                    REASSEMBLER.accept(slice, MinecraftClient.getInstance().world.getRegistryManager());

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
