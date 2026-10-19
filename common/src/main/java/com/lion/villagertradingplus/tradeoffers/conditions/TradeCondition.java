package com.lion.villagertradingplus.tradeoffers.conditions;

import net.minecraft.entity.Entity;

/// A predicate evaluated when a villager generates trade offers.
/// World-state conditions are snapshots from generation time. Location conditions stay static.
@FunctionalInterface
public interface TradeCondition {
    boolean test(Entity villager);
}
