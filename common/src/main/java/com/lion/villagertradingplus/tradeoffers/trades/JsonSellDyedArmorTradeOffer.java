package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import com.lion.villagertradingplus.tradeoffers.VanillaTradeFactory;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.VanillaCodecs;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import org.jetbrains.annotations.NotNull;

public class JsonSellDyedArmorTradeOffer extends JsonTradeOffer {
    private static final String OWNER = "sell_dyed_armor trade";

    /// The item modifier of the vanilla leatherworker trades: one dye, plus a binomial 2 / 0.75 roll for more.
    private static final String VANILLA_DYES = "{\"function\":\"minecraft:set_random_dyes\",\"number_of_dyes\":"
            + "{\"type\":\"minecraft:sum\",\"summands\":[1,{\"type\":\"minecraft:binomial\",\"n\":2,\"p\":0.75}]}}";

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, OWNER, "sell"));
        ItemStack currency = getItemStackFromJson(JsonFields.requireObject(json, OWNER, "priceIn"));
        LootItemFunction dyes = VanillaCodecs.decode(
                LootItemFunctions.ROOT_CODEC,
                JsonParser.parseString(VANILLA_DYES),
                "number_of_dyes"
        );

        return new Factory(
                sell,
                currency,
                dyes,
                maxUses,
                experience,
                priceMultiplier,
                demand
        );
    }

    private static class Factory implements TradeFactory {
        private final ItemStack sell;
        private final ItemStack currency;
        private final LootItemFunction dyes;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(
                ItemStack sell,
                ItemStack currency,
                LootItemFunction dyes,
                int maxUses,
                int experience,
                float multiplier,
                int demand
        ) {
            this.sell = sell;
            this.currency = currency;
            this.dyes = dyes;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public MerchantOffer create(ServerLevel level, Entity trader, RandomSource random) {
            LootContext context = VanillaTradeFactory.context(level, trader, random, Optional.empty());
            ItemStack result = this.dyes.apply(this.sell.copy(), context);

            if (result.isEmpty() || !result.has(DataComponents.DYED_COLOR)) {
                return null;
            }

            return new MerchantOffer(
                    traded(this.currency),
                    tradedOrEmpty(ItemStack.EMPTY),
                    result,
                    0,
                    this.maxUses,
                    this.experience,
                    this.multiplier,
                    this.demand
            );
        }
    }
}
