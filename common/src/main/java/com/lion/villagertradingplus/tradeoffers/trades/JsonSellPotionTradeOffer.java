package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JsonSellPotionTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "sell"));
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "convertible"));
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());

        return new Factory(
                buy,
                sell,
                currency,
                maxUses,
                experience,
                priceMultiplier
        );
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final ItemStack buy;
        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(
                ItemStack buy,
                ItemStack sell,
                ItemStack currency,
                int maxUses,
                int experience,
                float multiplier
        ) {
            this.buy = buy;
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            List<RegistryEntry<Potion>> list = brewable(entity.getWorld());

            if (list.isEmpty()) {
                return null;
            }

            RegistryEntry<Potion> potion = list.get(random.nextInt(list.size()));

            return new TradeOffer(
                    tradedCurrency(),
                    Optional.of(convertible()),
                    sellStack(potion),
                    this.maxUses,
                    this.experience,
                    this.multiplier
            );
        }

        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<RegistryEntry<Potion>> brewable = brewable(merchant.getWorld());
            out.pushShare(brewable.isEmpty() ? 1.0f : 1.0f / brewable.size());
            for (RegistryEntry<Potion> potion : brewable) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }

                out.add(
                        currency,
                        convertible().itemStack(),
                        sellStack(potion),
                        this.maxUses,
                        this.experience,
                        this.multiplier,
                        0
                );
            }
            out.popShare();
        }

        private static List<RegistryEntry<Potion>> brewable(World world) {
            return Registries.POTION.streamEntries()
                    .filter(entry -> !entry.value().getEffects().isEmpty()
                            && world.getBrewingRecipeRegistry().isBrewable(entry))
                    .collect(Collectors.toList());
        }

        private TradedItem convertible() {
            if (!this.buy.isOf(Items.POTION) && !this.buy.isOf(Items.SPLASH_POTION) && !this.buy.isOf(Items.LINGERING_POTION)) {
                return traded(this.buy);
            }

            return new TradedItem(this.buy.getItem(), this.buy.getCount())
                    .withComponents(
                            builder -> builder.add(
                                    DataComponentTypes.POTION_CONTENTS,
                                    new PotionContentsComponent(Potions.WATER)
                            )
                    );
        }

        private TradedItem tradedCurrency() {
            return traded(this.currency);
        }

        private ItemStack sellStack(RegistryEntry<Potion> potion) {
            ItemStack stack = new ItemStack(this.sell.getItem(), Math.max(1, this.sell.getCount()));
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potion));
            return stack;
        }
    }
}
