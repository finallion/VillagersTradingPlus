package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControlsPayload;
import org.jetbrains.annotations.Nullable;

public final class TradeControlsClientState {

    @Nullable
    private static TradeControlsPayload current;

    private static int version;

    private TradeControlsClientState() {}

    public static void register() {
        NetworkHelper.registerControlsReceiver(payload -> {
            current = payload;
            version++;
        });
    }

    @Nullable
    public static TradeControlsPayload current() {
        return current;
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        current = null;
        version++;
    }
}
