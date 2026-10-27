package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderMixin extends MerchantEntity implements TradeControl {

    @Unique
    private static final int villagertradingplus$BUYING_PAGE = 3;

    public WanderingTraderMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    /// Vanilla pairs carry their own draw count, so the whole list is swapped.
    @Redirect(
            method = "fillRecipes",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/village/TradeOffers;WANDERING_TRADER_TRADES:Ljava/util/List;",
                    opcode = org.objectweb.asm.Opcodes.GETSTATIC))
    private List<Pair<TradeOffers.Factory[], Integer>> villagertradingplus$mergedPools() {
        if (WanderingTraderTradeLoader.isServedByLoaderEvent()) {
            return TradeOffers.WANDERING_TRADER_TRADES;
        }

        return WanderingTraderTradeLoader.pools();
    }

    @Override
    public TradeOffers.Factory[] villagertradingplus$catalogPool(int level) {
        return level == villagertradingplus$BUYING_PAGE ? WanderingTraderTradeLoader.poolForLevel(0) : null;
    }

    @Override
    public int villagertradingplus$catalogMaxLevel() {
        return villagertradingplus$BUYING_PAGE;
    }

    @Override
    public int villagertradingplus$catalogPicks(int level) {
        return level == villagertradingplus$BUYING_PAGE ? WanderingTraderTradeLoader.countForLevel(0) : 0;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        TradeOfferList offers = getOffers();
        offers.clear();

        Pair<TradeOffers.Factory[], Integer> buying = WanderingTraderTradeLoader.buyingPair();
        TradeOffers.Factory[] common = WanderingTraderTradeLoader.poolForLevel(1);
        TradeOffers.Factory[] rare = WanderingTraderTradeLoader.poolForLevel(2);

        if (buying != null) {
            fillRecipesFromPool(offers, buying.getLeft(), buying.getRight());
        }

        if (rare != null) {
            fillRecipesFromPool(offers, rare, WanderingTraderTradeLoader.countForLevel(2));
        }

        if (common != null) {
            fillRecipesFromPool(offers, common, WanderingTraderTradeLoader.countForLevel(1));
        }

        for (Pair<TradeOffers.Factory[], Integer> extra : WanderingTraderTradeLoader.extraPairs()) {
            fillRecipesFromPool(offers, extra.getLeft(), extra.getRight());
        }
    }
}
