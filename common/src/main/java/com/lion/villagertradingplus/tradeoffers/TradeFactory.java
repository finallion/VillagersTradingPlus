package com.lion.villagertradingplus.tradeoffers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

/// Builds one offer for a trader. Returns null when the trade does not apply to this trader.
@FunctionalInterface
public interface TradeFactory {

    @Nullable
    MerchantOffer create(ServerLevel level, Entity trader, RandomSource random);
}
