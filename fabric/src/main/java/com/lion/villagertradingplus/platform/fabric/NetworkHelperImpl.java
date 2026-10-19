package com.lion.villagertradingplus.platform.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public final class NetworkHelperImpl {

    private NetworkHelperImpl() {
    }

    /// Fabric channels need no up-front registration.
    public static void init() {
    }

    public static void sendToPlayer(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, channel, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientReceiver(Identifier channel, Consumer<PacketByteBuf> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, responseSender) -> {
            // The buffer is recycled when the callback returns, so copy before the client thread uses it.
            PacketByteBuf copy = new PacketByteBuf(Unpooled.copiedBuffer(buf));
            client.execute(() -> receiver.accept(copy));
        });
    }
}
