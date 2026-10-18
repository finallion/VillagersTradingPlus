package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

/// The `sell` stack may carry component sugar via [ItemStackSerializer].
public class JsonMultiInputTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);
        ItemStack inputA = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "input_a"));
        ItemStack inputB = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "input_b"));
        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "multi_input trade", "sell"));
        return new Factory(inputA, inputB, sell, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeFactory {
        private final ItemStack inputA;
        private final ItemStack inputB;
        private final ItemStack sell;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack inputA, ItemStack inputB, ItemStack sell, int maxUses, int experience, float multiplier, int demand) {
            this.inputA = inputA;
            this.inputB = inputB;
            this.sell = sell;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public MerchantOffer create(net.minecraft.server.level.ServerLevel world, Entity entity, RandomSource random) {
            return new MerchantOffer(traded(inputA.copy()), tradedOrEmpty(inputB.copy()), sell.copy(), 0, maxUses, experience, multiplier, demand);
        }
    }
}
