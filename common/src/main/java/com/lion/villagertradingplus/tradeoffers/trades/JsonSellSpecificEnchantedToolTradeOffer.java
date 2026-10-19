package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

/// Sells a tool with one fixed enchantment. Unlike `sell_enchanted_tool`, it does not roll.
public class JsonSellSpecificEnchantedToolTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_specific_enchanted_tool trade", "sell"));
        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        String rawEnchantment = readString(json, "enchantment", "minecraft:fortune");
        Identifier enchantmentId = Identifier.tryParse(rawEnchantment);

        if (enchantmentId == null) {
            throw new TradeParseException("sell_specific_enchanted_tool trade: invalid enchantment id '" + rawEnchantment + "'");
        }

        // Resolve only the key. The entry needs a world, and parsing has none.
        ResourceKey<Enchantment> enchantmentKey = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(sell, currency, enchantmentKey, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeFactory {
        private final ItemStack sell;
        private final ItemStack currency;
        private final ResourceKey<Enchantment> enchantmentKey;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(
                ItemStack sell,
                ItemStack currency,
                ResourceKey<Enchantment> enchantmentKey,
                int level,
                int maxUses,
                int experience,
                float multiplier
        ) {
            this.sell = sell;
            this.currency = currency;
            this.enchantmentKey = enchantmentKey;
            this.level = level;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public MerchantOffer create(
                net.minecraft.server.level.ServerLevel world,
                Entity entity,
                net.minecraft.util.RandomSource random
        ) {
            Optional<Holder.Reference<Enchantment>> enchantment = entity.level().registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .get(this.enchantmentKey);

            if (enchantment.isEmpty()) {
                VillagerTradingPlus.LOGGER.warn("Skipping a sell_specific_enchanted_tool offer: unknown enchantment '{}'", this.enchantmentKey.identifier());
                return null;
            }

            // Copy the sell stack, so name, lore and enchantments survive.
            ItemStack itemStack = this.sell.copy();
            itemStack.enchant(enchantment.get(), this.level);
            return new MerchantOffer(traded(this.currency.copy()), itemStack, this.maxUses, this.experience, this.multiplier);
        }
    }
}
