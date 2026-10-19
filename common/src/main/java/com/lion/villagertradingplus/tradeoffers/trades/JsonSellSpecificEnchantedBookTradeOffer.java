package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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

        if (enchantmentId == null) {
            throw new TradeParseException("sell_specific_enchanted_book trade: invalid enchantment id '" + rawEnchantment + "'");
        }

        // Only the key resolves here, because the entry needs a loaded world and deserialization has none.
        RegistryKey<Enchantment> enchantmentKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(currency, enchantmentKey, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack currency;
        private final RegistryKey<Enchantment> enchantmentKey;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack currency, RegistryKey<Enchantment> enchantmentKey, int level, int maxUses, int experience, float multiplier) {
            this.currency = currency;
            this.enchantmentKey = enchantmentKey;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            return entity.getWorld().getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOptional(this.enchantmentKey)
                    .map(enchantment -> new TradeOffer(
                            traded(this.currency.copy()),
                            EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(enchantment, this.level)),
                            this.maxUses,
                            this.experience,
                            this.multiplier))
                    .orElseGet(() -> {
                        VillagerTradingPlus.LOGGER.warn("Skipping a sell_specific_enchanted_book offer: unknown enchantment '{}'", this.enchantmentKey.getValue());
                        return null;
                    });
        }
    }
}
