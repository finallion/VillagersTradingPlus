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

    private static int requestedLevel;

    private TradeCatalogClientState() {}

    public static void register() {
        NetworkHelper.registerClientReceiver(TradeCatalogPacket.CHANNEL, buf -> {
            TradeCatalogPacket.Payload payload = TradeCatalogPacket.read(buf);

            if (answersRequest(payload)) {
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
