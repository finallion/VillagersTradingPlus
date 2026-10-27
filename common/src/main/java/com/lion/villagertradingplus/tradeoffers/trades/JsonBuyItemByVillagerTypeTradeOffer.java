package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
        JsonObject buyByType = JsonFields.requireObject(json, "buy_item_by_villager_type trade", typesKey);
        ItemStack reward = getItemStackFromJson(
                JsonFields.requireObject(json, "buy_item_by_villager_type trade", "reward")
        );
        Map<RegistryKey<VillagerType>, ItemStack> buy = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : buyByType.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());

            if (id == null || Registries.VILLAGER_TYPE.getOptionalValue(id).isEmpty()) {
                VillagerTradingPlus.LOGGER.warn(
                        "Unknown villager type \"{}\" in a buy_item_by_villager_type trade{}, skipping that entry.",
                        entry.getKey(),
                        TradeOfferManager.fileSuffix()
                );
                continue;
            }

            JsonObject cost;

            if (entry.getValue().isJsonPrimitive()) {
                cost = new JsonObject();
                cost.addProperty("item", entry.getValue().getAsString());
                cost.addProperty("count", 1);
            } else {
                cost = JsonFields.asObject(entry.getValue(), "buy_item_by_villager_type trade");
            }

            buy.put(
                    RegistryKey.of(RegistryKeys.VILLAGER_TYPE, id),
                    getCostStackFromJson(cost)
            );
        }

        if (buy.isEmpty()) {
            throw new TradeParseException("a buy_item_by_villager_type trade needs at least one known villager type in \"" + typesKey + "\"");
        }

        return new Factory(
                buy,
                reward,
                maxUses,
                experience,
                priceMultiplier,
                demand
        );
    }

    private static class Factory implements TradeOffers.Factory {
        private final Map<RegistryKey<VillagerType>, ItemStack> buy;
        private final ItemStack reward;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(
                Map<RegistryKey<VillagerType>, ItemStack> buy,
                ItemStack reward,
                int maxUses,
                int experience,
                float multiplier,
                int demand
        ) {
            this.buy = buy;
            this.reward = reward;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public TradeOffer create(net.minecraft.server.world.ServerWorld world, Entity entity, Random random) {
            if (!(entity instanceof VillagerDataContainer container)) {
                return null;
            }

            RegistryKey<VillagerType> type = container.getVillagerData().type().getKey().orElse(null);
            ItemStack stack = type == null ? null : this.buy.get(type);

            if (stack == null) {
                return null;
            }

            return new TradeOffer(
                    traded(stack),
                    tradedOrEmpty(ItemStack.EMPTY),
                    reward.copy(),
                    0,
                    this.maxUses,
                    this.experience,
                    this.multiplier,
                    this.demand
            );
        }
    }
}
