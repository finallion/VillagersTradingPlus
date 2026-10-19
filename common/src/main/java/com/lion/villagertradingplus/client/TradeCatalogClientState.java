package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class TradeCatalogClientState {

    @Nullable
    private static TradeCatalogPacket.Payload current;

    private static boolean awaitingReply;

    private TradeCatalogClientState() {}

    public static void register() {
        NetworkHelper.registerClientReceiver(TradeCatalogPacket.CHANNEL, buf -> {
            current = TradeCatalogPacket.read(buf);
            awaitingReply = false;
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
