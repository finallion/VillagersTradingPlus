package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;

/// Sells an enchanted book with a fixed enchantment and level. sell_enchanted_book picks randomly instead.
public class JsonSellSpecificEnchantedBookTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        String rawEnchantment = readString(json, "enchantment", "minecraft:unbreaking");
        Identifier enchantmentId = Identifier.tryParse(rawEnchantment);
        Enchantment enchantment = enchantmentId == null ? null : Registries.ENCHANTMENT.get(enchantmentId);

        if (enchantment == null) {
            throw new TradeParseException("sell_specific_enchanted_book trade: unknown enchantment '" + rawEnchantment + "'");
        }

        int level = readInt(json, "level", 1);

        return new Factory(currency, enchantment, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack currency;
        private final Enchantment enchantment;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack currency, Enchantment enchantment, int level, int maxUses, int experience, float multiplier) {
            this.currency = currency;
            this.enchantment = enchantment;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(this.enchantment, this.level));
            return new TradeOffer(this.currency.copy(), book, this.maxUses, this.experience, this.multiplier);
        }
    }
}
