package com.lion.villagertradingplus.tradeoffers.conditions;

import com.lion.villagertradingplus.tradeoffers.catalog.ConditionInfo;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/// The parsed conditions block: the combined predicate and a description of each condition.
/// The descriptions let the catalogue show why a trade is gated and whether the gate is open.
public record ParsedConditions(List<Entry> entries, boolean orLogic) implements TradeCondition {

    public record Entry(Text description, TradeCondition condition) {
    }

    @Override
    public boolean test(Entity villager) {
        if (this.orLogic) {
            for (Entry entry : this.entries) {
                if (entry.condition().test(villager)) {
                    return true;
                }
            }
            // An empty OR block gates nothing, matching the AND case below.
            return this.entries.isEmpty();
        }

        for (Entry entry : this.entries) {
            if (!entry.condition().test(villager)) {
                return false;
            }
        }
        return true;
    }

    /// Evaluates each condition on its own for the catalogue.
    public List<ConditionInfo> describe(Entity merchant) {
        List<ConditionInfo> infos = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            infos.add(new ConditionInfo(entry.description(), entry.condition().test(merchant)));
        }
        return List.copyOf(infos);
    }
}
