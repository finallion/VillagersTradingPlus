package com.lion.villagertradingplus.client;

import com.lion.villagertradingplus.platform.NetworkHelper;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControlsPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class TradeControlsClientState {

    @Nullable
    private static TradeControlsPacket.Payload current;

    private static int version;

    private TradeControlsClientState() {}

    public static void register() {
        NetworkHelper.registerClientReceiver(TradeControlsPacket.CHANNEL, buf -> {
            current = TradeControlsPacket.read(buf);
            version++;
        });
    }

    @Nullable
    public static TradeControlsPacket.Payload current() {
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
