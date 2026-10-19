package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.NotNull;

/// Sells a book with one fixed enchantment. Unlike `sell_enchanted_book`, it does not roll.
public class JsonSellSpecificEnchantedBookTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJson(json.get("basePriceIn").getAsJsonObject());

        String rawEnchantment = readString(json, "enchantment", "minecraft:unbreaking");
        Identifier enchantmentId = Identifier.tryParse(rawEnchantment);

        if (enchantmentId == null) {
            throw new TradeParseException("sell_specific_enchanted_book trade: invalid enchantment id '" + rawEnchantment + "'");
        }

        // Resolve only the key. The entry needs a world, and parsing has none.
        ResourceKey<Enchantment> enchantmentKey = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
        int level = readInt(json, "level", 1);

        return new Factory(currency, enchantmentKey, level, maxUses, experience, priceMultiplier);
    }

    private static class Factory implements TradeFactory {
        private final ItemStack currency;
        private final ResourceKey<Enchantment> enchantmentKey;
        private final int level;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(
                ItemStack currency,
                ResourceKey<Enchantment> enchantmentKey,
                int level,
                int maxUses,
                int experience,
                float multiplier
        ) {
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
            return entity.level().registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .get(this.enchantmentKey)
                    .map(
                            enchantment -> new MerchantOffer(
                                    traded(this.currency.copy()),
                                    EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, this.level)),
                                    this.maxUses,
                                    this.experience,
                                    this.multiplier
                            )
                    )
                    .orElseGet(() -> {
                        VillagerTradingPlus.LOGGER.warn("Skipping a sell_specific_enchanted_book offer: unknown enchantment '{}'", this.enchantmentKey.identifier());
                        return null;
                    });
        }
    }
}
