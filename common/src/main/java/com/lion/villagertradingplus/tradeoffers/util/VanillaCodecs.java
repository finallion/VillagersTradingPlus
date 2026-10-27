package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

public final class VanillaCodecs {

    private VanillaCodecs() {}

    public static <T> T decode(Codec<T> codec, JsonElement json, String field) {
        RegistryWrapper.WrapperLookup lookup = DatapackRegistries.lookup()
                .orElseThrow(() -> new TradeParseException("cannot read \"" + field + "\": no datapack registries are loaded"));
        return codec.parse(RegistryOps.of(JsonOps.INSTANCE, lookup), json)
                .getOrThrow(error -> new TradeParseException("invalid \"" + field + "\": " + error));
    }
}
