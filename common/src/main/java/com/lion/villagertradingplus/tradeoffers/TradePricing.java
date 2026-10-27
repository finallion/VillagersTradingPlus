package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class TradePricing {

    private static final long DAY_LENGTH = 24000L;

    private TradePricing() {}

    public static int scaleCount(int count, int maxCount, float factor) {
        return Math.max(1, Math.min(maxCount, Math.round(count * factor)));
    }

    public static float factor(float costScale, boolean timeOfDayPricing, float variance, long dayTime) {
        float factor = costScale;

        if (timeOfDayPricing) {
            double swing = Math.sin(2.0 * Math.PI * ((dayTime % DAY_LENGTH) / (double) DAY_LENGTH));
            factor *= (float) (1.0 + variance * swing);
        }

        return factor;
    }

    public static float priceFactor(Entity merchant) {
        return factor(
                VillagerTradingPlus.CONFIG.trade_cost_scale,
                VillagerTradingPlus.CONFIG.enable_time_of_day_pricing,
                VillagerTradingPlus.CONFIG.time_of_day_price_variance,
                merchant.level().getOverworldClockTime()
        );
    }

    public static float multiplierScale() {
        return VillagerTradingPlus.CONFIG.trade_price_multiplier_scale;
    }

    public static MerchantOffer apply(MerchantOffer offer, Entity merchant) {
        float factor = priceFactor(merchant);
        float multiplier = multiplierScale();

        if (factor == 1.0f && multiplier == 1.0f) {
            return offer;
        }

        // Scaling a display stack drops its components. Rebuild the item cost instead.
        ItemCost first = offer.getItemCostA();

        if (factor != 1.0f) {
            first = new ItemCost(
                    first.item(),
                    scaleCount(first.count(), first.itemStack().getMaxStackSize(), factor),
                    first.components()
            );
        }

        return new MerchantOffer(
                first,
                offer.getItemCostB(),
                offer.getResult(),
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier() * multiplier,
                offer.getDemand()
        );
    }

    public static void applyFrom(MerchantOffers offers, int fromIndex, Entity merchant) {
        for (int i = Math.max(0, fromIndex); i < offers.size(); i++) {
            offers.set(i, apply(offers.get(i), merchant));
        }
    }
}
