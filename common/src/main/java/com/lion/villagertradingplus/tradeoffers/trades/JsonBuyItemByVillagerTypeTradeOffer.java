package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.util.ItemStackSerializer;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerDataContainer;
import net.minecraft.village.VillagerType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class JsonBuyItemByVillagerTypeTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        String typesKey = json.has("buy") && !json.has("buy_by_type") ? "buy" : "buy_by_type";
        JsonObject types = JsonFields.requireObject(json, "buy_item_by_villager_type trade", typesKey);
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        ItemStack reward = json.has("reward")
                ? getItemStackFromJson(JsonFields.requireObject(json, "buy_item_by_villager_type trade", "reward"))
                : new ItemStack(Items.EMERALD);

        Map<VillagerType, ItemStack> costs = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : types.entrySet()) {
            Identifier typeId = Identifier.tryParse(entry.getKey());

            if (typeId == null || !Registries.VILLAGER_TYPE.containsId(typeId)) {
                VillagerTradingPlus.LOGGER.warn(
                        "Unknown villager type \"{}\" in {}{}, skipping that entry.",
                        entry.getKey(),
                        typesKey,
                        TradeOfferManager.fileSuffix()
                );
                continue;
            }

            JsonObject cost;

            if (entry.getValue().isJsonObject()) {
                cost = entry.getValue().getAsJsonObject();
            } else {
                cost = new JsonObject();
                cost.addProperty("item", entry.getValue().getAsString());
                cost.addProperty("count", count);
            }


            costs.put(Registries.VILLAGER_TYPE.get(typeId), ItemStackSerializer.fromJsonCost(cost));
        }

        if (costs.isEmpty()) {
            throw new TradeParseException("a buy_item_by_villager_type trade needs at least one known villager type in \"" + typesKey + "\"");
        }

        return new Factory(
                costs,
                reward,
                maxUses,
                experience,
                priceMultiplier,
                demand
        );
    }

    private static class Factory implements TradeOffers.Factory {
        private final Map<VillagerType, ItemStack> costs;
        private final ItemStack reward;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(
                Map<VillagerType, ItemStack> costs,
                ItemStack reward,
                int maxUses,
                int experience,
                float multiplier,
                int demand
        ) {
            this.costs = costs;
            this.reward = reward;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public TradeOffer create(Entity entity, Random random) {
            if (!(entity instanceof VillagerDataContainer container)) {
                return null;
            }

            ItemStack cost = this.costs.get(container.getVillagerData().getType());

            if (cost == null) {
                return null;
            }

            return new TradeOffer(
                    cost.copy(),
                    ItemStack.EMPTY,
                    reward.copy(),
                    0,
                    maxUses,
                    experience,
                    multiplier,
                    demand
            );
        }
    }
}
