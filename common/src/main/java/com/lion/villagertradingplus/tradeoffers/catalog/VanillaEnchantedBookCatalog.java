package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.mixin.EnchantBookFactoryAccessor;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.village.TradeOffers;

public final class VanillaEnchantedBookCatalog {

    private static final int MAX_USES = 12;
    private static final float PRICE_MULTIPLIER = 0.2f;

    private VanillaEnchantedBookCatalog() {}

    public static boolean expand(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        if (!(factory instanceof EnchantBookFactoryAccessor accessor)) {
            return false;
        }

        List<Enchantment> available = Registries.ENCHANTMENT.stream()
                .filter(Enchantment::isAvailableForEnchantedBookOffer)
                .collect(Collectors.toList());

        for (Enchantment enchantment : available) {
            int levels = enchantment.getMaxLevel() - enchantment.getMinLevel() + 1;
            out.pushShare(1.0f / (available.size() * levels));

            for (int level = enchantment.getMinLevel(); level <= enchantment.getMaxLevel(); level++) {
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

    private static int clampPrice(int price, Enchantment enchantment) {
        return Math.min(enchantment.isTreasure() ? price * 2 : price, 64);
    }
}
