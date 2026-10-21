package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.mixin.EnchantRandomlyFunctionAccessor;
import com.lion.villagertradingplus.mixin.VillagerTradeAccessor;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import com.lion.villagertradingplus.tradeoffers.VanillaTradeFactory;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;

public final class VanillaEnchantedBookCatalog {

    private static final long SAMPLE_SEED = 0L;
    private static final int MAX_PRICE = 64;

    private VanillaEnchantedBookCatalog() {}

    public static boolean expand(TradeFactory factory, Entity merchant, CatalogBuilder out) {
        if (!(factory instanceof VanillaTradeFactory vanilla)
                || !(vanilla.enchantRandomlyFunction() instanceof EnchantRandomlyFunctionAccessor function)
                || !(merchant.level() instanceof ServerLevel level)) {
            return false;
        }

        MerchantOffer sample = vanilla.create(level, merchant, RandomSource.create(SAMPLE_SEED));

        if (sample == null || !sample.getResult().is(Items.ENCHANTED_BOOK)) {
            return false;
        }

        VillagerTradeAccessor trade = (VillagerTradeAccessor) vanilla.trade();
        List<Holder<Enchantment>> available = function.villagertradingplus$options()
                .map(options -> options.stream().toList())
                .orElseGet(() -> level.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .listElements()
                        .<Holder<Enchantment>>map(entry -> entry)
                        .toList());

        for (Holder<Enchantment> enchantment : available) {
            int firstLevel = enchantment.value().getMinLevel();
            int lastLevel = enchantment.value().getMaxLevel();
            boolean doubled = trade.villagertradingplus$doubleTradePriceEnchantments()
                    .map(set -> set.contains(enchantment))
                    .orElse(false);

            out.pushShare(1.0f / (available.size() * (lastLevel - firstLevel + 1)));

            for (int bookLevel = firstLevel; bookLevel <= lastLevel; bookLevel++) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }

                int minPrice = sample.getBaseCostA().getCount();
                int maxPrice = minPrice;

                if (function.villagertradingplus$includeAdditionalCostComponent()) {
                    minPrice = clampPrice(2 + 3 * bookLevel, doubled);
                    maxPrice = clampPrice(6 + 13 * bookLevel, doubled);
                }

                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.enchant(enchantment, bookLevel);

                out.add(
                        sample.getBaseCostA().copyWithCount(minPrice),
                        sample.getCostB(),
                        book,
                        sample.getMaxUses(),
                        sample.getXp(),
                        sample.getPriceMultiplier(),
                        sample.getDemand(),
                        minPrice,
                        maxPrice
                );
            }

            out.popShare();
        }

        return true;
    }

    private static int clampPrice(int price, boolean doubled) {
        return Math.min(doubled ? price * 2 : price, MAX_PRICE);
    }
}
