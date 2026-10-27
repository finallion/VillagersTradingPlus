package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

public class JsonMultiInputTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);
        ItemStack inputA = getCostStackFromJson(JsonFields.requireObject(json, "multi_input trade", "input_a"));
        ItemStack inputB = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "input_b"));
        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "sell"));
        return new Factory(inputA, inputB, sell, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack inputA;
        private final ItemStack inputB;
        private final ItemStack sell;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(
                ItemStack inputA,
                ItemStack inputB,
                ItemStack sell,
                int maxUses,
                int experience,
                float multiplier,
                int demand
        ) {
            this.inputA = inputA;
            this.inputB = inputB;
            this.sell = sell;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public TradeOffer create(Entity entity, Random random) {
            return new TradeOffer(traded(inputA.copy()), tradedOrEmpty(inputB.copy()), sell.copy(), 0, maxUses, experience, multiplier, demand);
        }
    }
}
