package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonSellDyedArmorTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_dyed_armor trade", "sell"));
        ItemStack currency = getItemStackFromJson(JsonFields.requireObject(json, "sell_dyed_armor trade", "priceIn"));

        return new Factory(
                sell,
                currency,
                maxUses,
                experience,
                priceMultiplier,
                demand
        );
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(
                ItemStack sell,
                ItemStack currency,
                int maxUses,
                int experience,
                float multiplier,
                int demand
        ) {
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public TradeOffer create(Entity entity, Random random) {
            ItemStack stack = this.sell.copy();

            if (stack.isIn(ItemTags.DYEABLE)) {
                List<DyeItem> dyes = new ArrayList<>();

                dyes.add(randomDye(random));

                if (random.nextFloat() > 0.7F) {
                    dyes.add(randomDye(random));
                }

                if (random.nextFloat() > 0.8F) {
                    dyes.add(randomDye(random));
                }

                stack = DyedColorComponent.setColor(stack, dyes);
            }

            return new TradeOffer(
                    traded(currency),
                    Optional.empty(),
                    stack,
                    0,
                    maxUses,
                    experience,
                    multiplier,
                    demand
            );
        }

        private static DyeItem randomDye(Random random) {
            return DyeItem.byColor(DyeColor.byId(random.nextInt(16)));
        }
    }
}
