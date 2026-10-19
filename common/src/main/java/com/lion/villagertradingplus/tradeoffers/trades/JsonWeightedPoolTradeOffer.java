package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpansion;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/// A picked sub-trade can return null, for example when its condition fails.
public class JsonWeightedPoolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        List<Entry> entries = new ArrayList<>();
        int totalWeight = 0;
        for (JsonElement element : JsonFields.requireArray(json, "weighted_pool trade", "pool")) {
            JsonObject entry = element.getAsJsonObject();
            int weight = readInt(entry, "weight", 1);
            TradeOffers.Factory factory = TradeOfferManager.deserializeTrade(entry.getAsJsonObject("trade"));

            // The inner PricingTradeFactory is stripped, so the cost scale applies once.
            if (factory instanceof PricingTradeFactory pricing) {
                factory = pricing.delegate();
            }

            if (factory != null && weight > 0) {
                entries.add(new Entry(weight, factory));
                totalWeight += weight;
            }
        }
        return new Factory(entries, totalWeight);
    }

    private record Entry(int weight, TradeOffers.Factory factory) {
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final List<Entry> entries;
        private final int totalWeight;

        public Factory(List<Entry> entries, int totalWeight) {
            this.entries = entries;
            this.totalWeight = totalWeight;
        }

        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            for (Entry entry : entries) {
                out.pushWeight(entry.weight(), totalWeight);
                CatalogExpansion.expand(entry.factory(), merchant, out);
                out.popWeight();
            }
        }

        @Override
        public TradeOffer create(net.minecraft.server.world.ServerWorld world, Entity entity, Random random) {
            if (entries.isEmpty() || totalWeight <= 0) {
                return null;
            }

            int roll = random.nextInt(totalWeight);
            for (Entry entry : entries) {
                roll -= entry.weight();

                if (roll < 0) {
                    return entry.factory().create(world, entity, random);
                }
            }
            return null;
        }
    }
}
