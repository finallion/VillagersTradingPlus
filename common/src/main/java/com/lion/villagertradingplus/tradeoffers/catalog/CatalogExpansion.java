package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory;
import com.lion.villagertradingplus.tradeoffers.TradePricing;
import com.lion.villagertradingplus.tradeoffers.RegistryRebindFactory;
import com.lion.villagertradingplus.tradeoffers.conditions.ParsedConditions;
import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import net.minecraft.entity.Entity;
import net.minecraft.village.TradeOffers;

import java.util.List;

public final class CatalogExpansion {

    private CatalogExpansion() {}

    public static void expandTrade(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        out.reset();
        out.setPriceFactor(TradePricing.priceFactor(merchant));
        expand(factory, merchant, out);
    }

    public static void expand(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        if (out.isFull()) {
            out.countSkipped(1);
            return;
        }

        if (factory instanceof ConditionalTradeFactory conditional) {
            TradeCondition condition = conditional.condition();
            List<ConditionInfo> infos = condition instanceof ParsedConditions parsed
                    ? parsed.describe(merchant)
                    : List.of();
            out.pushConditions(infos, condition.test(merchant));
            expand(conditional.delegate(), merchant, out);
            out.popConditions();
            return;
        }

        if (factory instanceof RegistryRebindFactory rebind) {
            expand(rebind.delegate(), merchant, out);
            return;
        }

        if (factory instanceof CatalogExpandable expandable) {
            expandable.expandCatalog(merchant, out);
            return;
        }

        if (VanillaEnchantedBookCatalog.expand(factory, merchant, out)) {
            return;
        }

        out.addSampled(factory, merchant);
    }
}
