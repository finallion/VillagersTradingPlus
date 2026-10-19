package com.lion.villagertradingplus.tradeoffers.conditions;

import com.lion.villagertradingplus.tradeoffers.catalog.ConditionInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public record ParsedConditions(List<Entry> entries, boolean orLogic) implements TradeCondition {

    public record Entry(Component description, TradeCondition condition) {
    }

    @Override
    public boolean test(Entity villager) {
        if (this.orLogic) {
            for (Entry entry : this.entries) {
                if (entry.condition().test(villager)) {
                    return true;
                }
            }
            // An empty OR block gates nothing.
            return this.entries.isEmpty();
        }

        for (Entry entry : this.entries) {
            if (!entry.condition().test(villager)) {
                return false;
            }
        }
        return true;
    }

    public List<ConditionInfo> describe(Entity merchant) {
        List<ConditionInfo> infos = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            infos.add(new ConditionInfo(entry.description(), entry.condition().test(merchant)));
        }
        return List.copyOf(infos);
    }
}
