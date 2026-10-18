package com.lion.villagertradingplus.tradeoffers;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/// Lets a vanilla trade from a trade set take part in a VTP pool.
public final class VanillaTradeFactory implements TradeFactory {

    private final Holder<VillagerTrade> trade;
    private final Optional<Identifier> randomSequence;

    public VanillaTradeFactory(Holder<VillagerTrade> trade, Optional<Identifier> randomSequence) {
        this.trade = trade;
        this.randomSequence = randomSequence;
    }


    @Override
    public MerchantOffer create(ServerLevel level, Entity trader, RandomSource random) {
        return trade.value().getOffer(context(level, trader, random, randomSequence));
    }

    /// Builds the loot context that vanilla builds before it rolls a trade set.
    public static LootContext context(ServerLevel level, Entity trader, RandomSource random, Optional<Identifier> randomSequence) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, trader.position())
                .withParameter(LootContextParams.THIS_ENTITY, trader)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE);
        return new LootContext.Builder(params).withOptionalRandomSource(random).create(randomSequence);
    }
}
