package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/// Holds the catalogue that the server sent last.
/// The state is static, because the packet handler and the screen do not reference each other.
/// One slot is enough, because a player has one trade screen open at a time.
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
