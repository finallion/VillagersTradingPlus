package com.lion.villagertradingplus.vanilla;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

final class TradeApi {

    private TradeApi() {}

    static TradeOffer create(TradeOffers.Factory factory, Entity entity, Random random) {
        return factory.create((ServerWorld) entity.getEntityWorld(), entity, random);
    }
}
