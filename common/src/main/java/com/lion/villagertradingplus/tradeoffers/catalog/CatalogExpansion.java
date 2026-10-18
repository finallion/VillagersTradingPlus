package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory;
import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import com.lion.villagertradingplus.tradeoffers.RegistryRebindFactory;
import com.lion.villagertradingplus.tradeoffers.conditions.ParsedConditions;
import com.lion.villagertradingplus.tradeoffers.conditions.TradeCondition;
import java.util.List;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;

/// Turns a trade factory into catalogue rows. It removes the wrappers one by one, then lists the offers.
public final class CatalogExpansion {

    private CatalogExpansion() {
    }

    public static void expandTrade(TradeFactory factory, Entity merchant, CatalogBuilder out) {
        out.reset();
        expand(factory, merchant, out);
    }

    public static void expand(TradeFactory factory, Entity merchant, CatalogBuilder out) {
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
            // A condition registered without a describer gates correctly. Its row shows no reason line.
            List<ConditionInfo> infos = condition instanceof ParsedConditions parsed
                    ? parsed.describe(merchant)
                    : List.of();
            out.pushConditions(infos, condition.test(merchant));
            expand(conditional.delegate(), merchant, out);
            out.popConditions();
            return;
        }

        // This wrapper only rebinds registry entries. Walk past it to the factory that enumerates.
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
