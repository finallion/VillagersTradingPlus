package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

public class JsonSellPotionTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "sell"));
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "convertible"));
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());

        return new Factory(buy, sell, currency, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeFactory, CatalogExpandable {
        private final ItemStack buy;
        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack buy, ItemStack sell, ItemStack currency, int maxUses, int experience, float multiplier) {
            this.buy = buy;
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public MerchantOffer create(net.minecraft.server.level.ServerLevel world, Entity entity, net.minecraft.util.RandomSource random) {
            List<Holder<Potion>> list = brewable(entity.level());
            if (list.isEmpty()) {
                return null;
            }

            Holder<Potion> potion = list.get(random.nextInt(list.size()));

            return new MerchantOffer(waterBottle(), Optional.of(tradedCurrency()), sellStack(potion),
                    this.maxUses, this.experience, this.multiplier);
        }

        /// The potion pool is a fixed registry scan, so every brewable result gets its own row.
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<Holder<Potion>> brewable = brewable(merchant.level());
            out.pushShare(brewable.isEmpty() ? 1.0f : 1.0f / brewable.size());
            for (Holder<Potion> potion : brewable) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }
                out.add(waterBottle().itemStack(), currency, sellStack(potion), this.maxUses, this.experience,
                        this.multiplier, 0);
            }
            out.popShare();
        }

        private static List<Holder<Potion>> brewable(Level world) {
            return BuiltInRegistries.POTION.listElements()
                    .filter(entry -> !entry.value().getEffects().isEmpty()
                            && world.potionBrewing().isBrewablePotion(entry))
                    .collect(Collectors.toList());
        }

        private ItemCost waterBottle() {
            return new ItemCost(this.buy.getItem(), this.buy.getCount())
                    .withComponents(builder -> builder.expect(DataComponents.POTION_CONTENTS,
                            new PotionContents(Potions.WATER)));
        }

        private ItemCost tradedCurrency() {
            return new ItemCost(this.currency.getItem(), this.currency.getCount());
        }

        private ItemStack sellStack(Holder<Potion> potion) {
            ItemStack stack = new ItemStack(this.sell.getItem(), Math.max(1, this.sell.getCount()));
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            return stack;
        }
    }
}
