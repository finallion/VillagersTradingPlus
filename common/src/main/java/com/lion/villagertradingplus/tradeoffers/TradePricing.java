package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

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
                merchant.getWorld().getTimeOfDay()
        );
    }

    public static float multiplierScale() {
        return VillagerTradingPlus.CONFIG.trade_price_multiplier_scale;
    }

    public static TradeOffer apply(TradeOffer offer, Entity merchant) {
        float factor = priceFactor(merchant);
        float multiplier = multiplierScale();

        if (factor == 1.0f && multiplier == 1.0f) {
            return offer;
        }

        ItemStack first = offer.getOriginalFirstBuyItem().copy();

        if (factor != 1.0f) {
            first.setCount(scaleCount(first.getCount(), Math.max(first.getMaxCount(), first.getCount()), factor));
        }

        return new TradeOffer(
                first,
                offer.getSecondBuyItem(),
                offer.getSellItem(),
                offer.getUses(),
                offer.getMaxUses(),
                offer.getMerchantExperience(),
                offer.getPriceMultiplier() * multiplier,
                offer.getDemandBonus()
        );
    }

    public static void applyFrom(TradeOfferList offers, int fromIndex, Entity merchant) {
        for (int i = Math.max(0, fromIndex); i < offers.size(); i++) {
            offers.set(i, apply(offers.get(i), merchant));
        }
    }
}
