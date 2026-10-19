package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory;
import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import com.lion.villagertradingplus.tradeoffers.RegistryRebindFactory;
import com.lion.villagertradingplus.tradeoffers.conditions.ParsedConditions;
import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import net.minecraft.entity.Entity;
import net.minecraft.village.TradeOffers;

import java.util.List;

/// Turns a trade factory into catalogue rows.
/// TradeOfferManager.deserializeTrade wraps every factory recursively. The walk peels the wrappers one by one.
/// It reaches past ConditionalTradeFactory, so a trade with a failing condition is listed and marked.
public final class CatalogExpansion {

    private CatalogExpansion() {
    }

    public static void expandTrade(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        out.reset();
        expand(factory, merchant, out);
    }

    public static void expand(TradeOffers.Factory factory, Entity merchant, CatalogBuilder out) {
        if (out.isFull()) {
            out.countSkipped(1);
            return;
        }

        if (factory instanceof PricingTradeFactory pricing) {
            out.pushPriceFactor(pricing.priceFactor(merchant));
            expand(pricing.delegate(), merchant, out);
            out.popPriceFactor();
            return;
        }

        if (factory instanceof ConditionalTradeFactory conditional) {
            TradeCondition condition = conditional.condition();
            // Third-party conditions without a describer still gate, but the row shows no reason line.
            List<ConditionInfo> infos = condition instanceof ParsedConditions parsed
                    ? parsed.describe(merchant)
                    : List.of();
            out.pushConditions(infos, condition.test(merchant));
            expand(conditional.delegate(), merchant, out);
            out.popConditions();
            return;
        }

        // Transparent to the catalogue: the rows below must still reach the enumerating factory.
        if (factory instanceof RegistryRebindFactory rebind) {
            expand(rebind.delegate(), merchant, out);
            return;
        }

        if (factory instanceof CatalogExpandable expandable) {
            expandable.expandCatalog(merchant, out);
            return;
        }

        out.addSampled(factory, merchant);
    }
}
