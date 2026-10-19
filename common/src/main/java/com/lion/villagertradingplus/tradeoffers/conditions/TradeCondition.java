package com.lion.villagertradingplus.tradeoffers.conditions;

import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface TradeCondition {
    boolean test(Entity villager);
}
