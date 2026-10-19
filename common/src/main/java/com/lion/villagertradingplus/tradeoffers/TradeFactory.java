package com.lion.villagertradingplus.tradeoffers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface TradeFactory {

    @Nullable
    MerchantOffer create(ServerLevel level, Entity trader, RandomSource random);
}
