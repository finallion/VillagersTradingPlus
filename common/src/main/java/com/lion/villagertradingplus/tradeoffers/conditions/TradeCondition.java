package com.lion.villagertradingplus.tradeoffers.conditions;

import net.minecraft.world.entity.Entity;

/// Runs when the offers of a level are generated. The [Entity] gives the level, biome and position.
@FunctionalInterface
public interface TradeCondition {
    boolean test(Entity villager);
}
