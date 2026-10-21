package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.mixin.EnchantBookFactoryAccessor;
import java.util.List;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.village.TradeOffers;

public final class VanillaEnchantedBookCatalog {

    private static final int MAX_USES = 12;
    private static final float PRICE_MULTIPLIER = 0.2f;

    private VanillaEnchantedBookCatalog() {}

    public static boolean expand(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        if (!(factory instanceof EnchantBookFactoryAccessor accessor)) {
            return false;
        }

        List<RegistryEntry<Enchantment>> available = merchant.getWorld()
                .getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT)
                .streamEntries()
                .filter(entry -> entry.isIn(accessor.villagertradingplus$possibleEnchantments()))
                .<RegistryEntry<Enchantment>>map(entry -> entry)
                .toList();

        for (RegistryEntry<Enchantment> enchantment : available) {
            int firstLevel = Math.max(enchantment.value().getMinLevel(), accessor.villagertradingplus$minLevel());
            int lastLevel = Math.min(enchantment.value().getMaxLevel(), accessor.villagertradingplus$maxLevel());

            if (lastLevel < firstLevel) {
                continue;
            }

            out.pushShare(1.0f / (available.size() * (lastLevel - firstLevel + 1)));

            for (int level = firstLevel; level <= lastLevel; level++) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }

                int minPrice = clampPrice(2 + 3 * level, enchantment);
                int maxPrice = clampPrice(6 + 13 * level, enchantment);

                out.add(
                        new ItemStack(Items.EMERALD, minPrice),
                        new ItemStack(Items.BOOK),
                        EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level)),
                        MAX_USES,
                        accessor.villagertradingplus$experience(),
                        PRICE_MULTIPLIER,
                        0,
                        minPrice,
                        maxPrice
                );
            }

            out.popShare();
        }

        return true;
    }

    private static int clampPrice(int price, RegistryEntry<Enchantment> enchantment) {
        return Math.min(enchantment.isIn(EnchantmentTags.DOUBLE_TRADE_PRICE) ? price * 2 : price, 64);
    }
}
