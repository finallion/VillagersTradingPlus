package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

public final class ConditionalTradeFactory implements TradeOffers.Factory {

    private final TradeOffers.Factory delegate;
    private final TradeCondition condition;

    public ConditionalTradeFactory(TradeOffers.Factory delegate, TradeCondition condition) {
        this.delegate = delegate;
        this.condition = condition;
    }

    @Override
    public TradeOffer create(net.minecraft.server.world.ServerWorld world, Entity entity, Random random) {
        if (!condition.test(entity)) {
            return null;
        }

        return delegate.create(world, entity, random);
    }

    public TradeOffers.Factory delegate() {
        return this.delegate;
    }

    public TradeCondition condition() {
        return this.condition;
    }
}
