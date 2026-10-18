package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import org.jetbrains.annotations.NotNull;

public class JsonSellEnchantedToolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_enchanted_tool trade", "sell"));
        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        return new Factory(sell, currency, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeFactory, CatalogExpandable {
        /// Bounds of the `5 + nextInt(15)` enchanting power roll that also sets the price.
        private static final int MIN_POWER = 5;
        private static final int MAX_POWER = 19;

        private final ItemStack sell;
        private final ItemStack currency;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack sell, ItemStack currency, int maxUses, int experience, float multiplier) {
            this.sell = sell;
            this.currency = currency;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public MerchantOffer create(net.minecraft.server.level.ServerLevel world, Entity entity, net.minecraft.util.RandomSource random) {
            int power = MIN_POWER + random.nextInt(MAX_POWER - MIN_POWER + 1);
            // Copy the sell stack, so the JSON count, name and lore survive the enchanting pass.
            ItemStack itemStack = EnchantmentHelper.enchantItem(random, this.sell.copy(), power,
                    entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE).stream());

            return new MerchantOffer(traded(new ItemStack(currency.getItem(), price(power))), itemStack,
                    this.maxUses, this.experience, multiplier);
        }

        /// Not enumerable. The enchanting pass picks an opaque set of enchantments.
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            if (!(merchant.level() instanceof net.minecraft.server.level.ServerLevel world)) {
                return;
            }
            MerchantOffer sample = create(world, merchant, RandomSource.create(0L));
            if (sample != null) {
                out.addOffer(sample, price(MIN_POWER), price(MAX_POWER));
            }
        }

        private int price(int power) {
            return Math.min(this.currency.getCount() + power, 64);
        }
    }
}
