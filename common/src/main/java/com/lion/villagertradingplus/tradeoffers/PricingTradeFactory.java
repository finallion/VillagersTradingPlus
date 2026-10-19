package com.lion.villagertradingplus.tradeoffers;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

/// Applies global price tuning to any produced offer: a currency count scale and optional time-of-day variance.
/// The price is fixed at generation, so the variance differs between offers.
public final class PricingTradeFactory implements TradeOffers.Factory {

    private final TradeOffers.Factory delegate;

    private PricingTradeFactory(TradeOffers.Factory delegate) {
        this.delegate = delegate;
    }

    /// Wraps only when pricing tuning is active.
    public static TradeOffers.Factory wrapIfNeeded(TradeOffers.Factory delegate, JsonObject trade) {
        boolean costScale = VillagerTradingPlus.CONFIG.trade_cost_scale != 1.0f;
        boolean timeOfDay = VillagerTradingPlus.CONFIG.enable_time_of_day_pricing;
        return (costScale || timeOfDay) ? new PricingTradeFactory(delegate) : delegate;
    }

    @Override
    public TradeOffer create(Entity entity, Random random) {
        TradeOffer offer = delegate.create(entity, random);

        if (offer == null) {
            return null;
        }

        float factor = priceFactor(entity);

        if (factor == 1.0f) {
            return offer;
        }

        ItemStack first = offer.getOriginalFirstBuyItem().copy();
        first.setCount(scaleCount(first.getCount(), first.getMaxCount(), factor));

        return new TradeOffer(
                first,
                offer.getSecondBuyItem(),
                offer.getSellItem(),
                offer.getUses(),
                offer.getMaxUses(),
                offer.getMerchantExperience(),
                offer.getPriceMultiplier(),
                offer.getDemandBonus()
        );
    }

    /// Exposed so the catalogue can enumerate the delegate and re-apply pricing.
    public TradeOffers.Factory delegate() {
        return this.delegate;
    }

    /// Shared so catalogue rows use the same pricing as real offers.
    public static int scaleCount(int count, int maxCount, float factor) {
        return MathHelper.clamp(Math.round(count * factor), 1, maxCount);
    }

    public float priceFactor(Entity entity) {
        float factor = VillagerTradingPlus.CONFIG.trade_cost_scale;

        if (VillagerTradingPlus.CONFIG.enable_time_of_day_pricing) {
            float variance = VillagerTradingPlus.CONFIG.time_of_day_price_variance;
            long timeOfDay = entity.getWorld().getTimeOfDay() % 24000L;
            double swing = Math.sin(2.0 * Math.PI * (timeOfDay / 24000.0));
            factor *= (float) (1.0 + variance * swing);
        }

        return factor;
    }
}
