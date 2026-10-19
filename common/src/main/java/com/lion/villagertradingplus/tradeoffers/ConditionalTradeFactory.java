package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

/// Wraps a trade factory and yields its offer only when the condition passes. A null offer drops the trade.
public final class ConditionalTradeFactory implements TradeOffers.Factory {

    private final TradeOffers.Factory delegate;
    private final TradeCondition condition;

    public ConditionalTradeFactory(TradeOffers.Factory delegate, TradeCondition condition) {
        this.delegate = delegate;
        this.condition = condition;
    }

    @Override
    public TradeOffer create(Entity entity, Random random) {
        if (!condition.test(entity)) {
            return null;
        }

        return delegate.create(entity, random);
    }

    /// The catalogue reads past the gate on purpose and marks gated trades.
    public TradeOffers.Factory delegate() {
        return this.delegate;
    }

    public TradeCondition condition() {
        return this.condition;
    }
}
