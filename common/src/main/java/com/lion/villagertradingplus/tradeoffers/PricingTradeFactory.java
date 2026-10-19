package com.lion.villagertradingplus.tradeoffers;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/// Applies the economy tuning to every offer. The price is fixed when the offer is generated.
public final class PricingTradeFactory implements TradeFactory {

    private final TradeFactory delegate;

    private PricingTradeFactory(TradeFactory delegate) {
        this.delegate = delegate;
    }

    public static TradeFactory wrapIfNeeded(TradeFactory delegate, JsonObject trade) {
        boolean costScale = VillagerTradingPlus.CONFIG.trade_cost_scale != 1.0f;
        boolean timeOfDay = VillagerTradingPlus.CONFIG.enable_time_of_day_pricing;
        return (costScale || timeOfDay) ? new PricingTradeFactory(delegate) : delegate;
    }

    @Override
    public MerchantOffer create(net.minecraft.server.level.ServerLevel world, Entity entity, RandomSource random) {
        MerchantOffer offer = delegate.create(world, entity, random);

        if (offer == null) {
            return null;
        }

        float factor = priceFactor(entity);

        if (factor == 1.0f) {
            return offer;
        }

        // Rebuild from the item cost, so its component predicate survives. Scaling a display stack drops it.
        ItemCost originalFirst = offer.getItemCostA();
        ItemCost first = new ItemCost(
                originalFirst.item(),
                scaleCount(originalFirst.count(), originalFirst.itemStack().getMaxStackSize(), factor),
                originalFirst.components()
        );

        return new MerchantOffer(
                first,
                offer.getItemCostB(),
                offer.getResult(),
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand()
        );
    }

    public TradeFactory delegate() {
        return this.delegate;
    }

    /// Shared so catalogue rows are priced exactly the way a real generated offer would be.
    public static int scaleCount(int count, int maxCount, float factor) {
        return Mth.clamp(Math.round(count * factor), 1, maxCount);
    }

    public float priceFactor(Entity entity) {
        float factor = VillagerTradingPlus.CONFIG.trade_cost_scale;

        if (VillagerTradingPlus.CONFIG.enable_time_of_day_pricing) {
            float variance = VillagerTradingPlus.CONFIG.time_of_day_price_variance;
            long timeOfDay = entity.level().getOverworldClockTime() % 24000L;
            double swing = Math.sin(2.0 * Math.PI * (timeOfDay / 24000.0));
            factor *= (float) (1.0 + variance * swing);
        }

        return factor;
    }
}
