package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

public final class TradeTables {

    private TradeTables() {}

    public static boolean rebalanced(World world) {
        return world != null && world.getEnabledFeatures().contains(FeatureFlags.TRADE_REBALANCE);
    }

    public static Int2ObjectMap<TradeOffers.Factory[]> villagerLevels(World world, VillagerProfession profession) {
        if (rebalanced(world)) {
            Int2ObjectMap<TradeOffers.Factory[]> levels = TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(profession);

            if (levels != null) {
                return levels;
            }
        }

        return TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(profession);
    }
}
