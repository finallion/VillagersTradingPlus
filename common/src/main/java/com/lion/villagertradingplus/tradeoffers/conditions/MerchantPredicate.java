package com.lion.villagertradingplus.tradeoffers.conditions;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.tradeoffers.VanillaTradeFactory;
import com.lion.villagertradingplus.tradeoffers.util.VanillaCodecs;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public final class MerchantPredicate {

    public static final String FIELD = "merchant_predicate";

    private MerchantPredicate() {}

    public static ParsedConditions parse(JsonElement json) {
        LootItemCondition predicate = VanillaCodecs.decode(LootItemCondition.DIRECT_CODEC, json, FIELD);
        VanillaCodecs.validateForTrade(predicate, FIELD);
        TradeCondition condition = merchant -> merchant.level() instanceof ServerLevel level
                && predicate.test(VanillaTradeFactory.context(level, merchant, null, Optional.empty()));
        ParsedConditions.Entry entry = new ParsedConditions.Entry(
                Component.translatable("condition.villagertradingplus.generic", FIELD),
                condition
        );
        return new ParsedConditions(List.of(entry), false);
    }
}
