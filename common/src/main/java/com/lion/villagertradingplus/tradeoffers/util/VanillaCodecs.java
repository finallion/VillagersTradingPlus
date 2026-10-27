package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public final class VanillaCodecs {

    private VanillaCodecs() {}

    public static <T> T decode(Codec<T> codec, JsonElement json, String field) {
        HolderLookup.Provider lookup = DatapackRegistries.lookup()
                .orElseThrow(() -> new TradeParseException("cannot read \"" + field + "\": no datapack registries are loaded"));
        return codec.parse(RegistryOps.create(JsonOps.INSTANCE, lookup), json)
                .getOrThrow(error -> new TradeParseException("invalid \"" + field + "\": " + error));
    }

    public static <T extends Validatable> T validateForTrade(T value, String field) {
        return Validatable.<T>validatorForContext(LootContextParamSets.VILLAGER_TRADE)
                .apply(value)
                .getOrThrow(error -> new TradeParseException("invalid \"" + field + "\": " + error));
    }
}
