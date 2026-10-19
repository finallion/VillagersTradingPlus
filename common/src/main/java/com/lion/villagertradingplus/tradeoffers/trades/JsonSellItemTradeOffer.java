package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class JsonSellItemTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_item trade", "sell"));
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());

        return new Factory(sell, currency, maxUses, experience, priceMultiplier, demand);
    }

    private static class Factory implements TradeFactory {
        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(ItemStack sell, ItemStack currency, int maxUses, int experience, float multiplier, int demand) {
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public MerchantOffer create(
                net.minecraft.server.level.ServerLevel world,
                Entity entity,
                net.minecraft.util.RandomSource random
        ) {
            return new MerchantOffer(traded(currency), tradedOrEmpty(net.minecraft.world.item.ItemStack.EMPTY), sell, 0, this.maxUses, this.experience, multiplier, this.demand);
        }
    }
}
