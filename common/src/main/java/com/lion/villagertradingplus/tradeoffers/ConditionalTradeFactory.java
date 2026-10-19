package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;

public final class ConditionalTradeFactory implements TradeFactory {

    private final TradeFactory delegate;
    private final TradeCondition condition;

    public ConditionalTradeFactory(TradeFactory delegate, TradeCondition condition) {
        this.delegate = delegate;
        this.condition = condition;
    }

    @Override
    public MerchantOffer create(net.minecraft.server.level.ServerLevel world, Entity entity, RandomSource random) {
        if (!condition.test(entity)) {
            return null;
        }

        return delegate.create(world, entity, random);
    }

    public TradeFactory delegate() {
        return this.delegate;
    }

    public TradeCondition condition() {
        return this.condition;
    }
}
