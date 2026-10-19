package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/// Sells a tool with a fixed enchantment and level. sell_enchanted_tool enchants randomly instead.
public class JsonSellSpecificEnchantedToolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_specific_enchanted_tool trade", "sell"));
        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        String rawEnchantment = readString(json, "enchantment", "minecraft:fortune");
        Identifier enchantmentId = Identifier.tryParse(rawEnchantment);

        if (enchantmentId == null) {
            throw new TradeParseException("sell_specific_enchanted_tool trade: invalid enchantment id '" + rawEnchantment + "'");
        }

        // Only the key resolves here, because the entry needs a loaded world and deserialization has none.
        RegistryKey<Enchantment> enchantmentKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(sell, currency, enchantmentKey, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeOffers.Factory {
        private final ItemStack sell;
        private final ItemStack currency;
        private final RegistryKey<Enchantment> enchantmentKey;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(ItemStack sell, ItemStack currency, RegistryKey<Enchantment> enchantmentKey, int level, int maxUses, int experience, float multiplier) {
            this.sell = sell;
            this.currency = currency;
            this.enchantmentKey = enchantmentKey;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            Optional<RegistryEntry.Reference<Enchantment>> enchantment = entity.getWorld().getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOptional(this.enchantmentKey);

            if (enchantment.isEmpty()) {
                VillagerTradingPlus.LOGGER.warn("Skipping a sell_specific_enchanted_tool offer: unknown enchantment '{}'", this.enchantmentKey.getValue());
                return null;
            }

            ItemStack itemStack = this.sell.copy();
            itemStack.addEnchantment(enchantment.get(), this.level);
            return new TradeOffer(traded(this.currency.copy()), itemStack, this.maxUses, this.experience, this.multiplier);
        }
    }
}
