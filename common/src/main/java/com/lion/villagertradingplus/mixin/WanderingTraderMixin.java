package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradePricing;
import com.lion.villagertradingplus.tradeoffers.TradeTables;
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
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderMixin extends MerchantEntity implements TradeControl {
    @Unique private int villagertradingplus$fillStart;

    public WanderingTraderMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @ModifyConstant(method = "fillRecipes", constant = @Constant(intValue = 5))
    private int changeTradeOfferCount(int value) {
        return VillagerTradingPlus.CONFIG.trade_offers_wandering_trader;
    }

    @Inject(method = "fillRecipes", at = @At("HEAD"))
    private void villagertradingplus$markFillStart(CallbackInfo ci) {
        villagertradingplus$fillStart = getOffers().size();
    }

    /// The rare offer is added without fillRecipesFromPool, so the trader prices its whole fill here.
    @Inject(method = "fillRecipes", at = @At("TAIL"))
    private void villagertradingplus$priceFill(CallbackInfo ci) {
        TradePricing.applyFrom(getOffers(), villagertradingplus$fillStart, this);
    }

    /// The experiment's fill is a private branch of fillRecipes, so the mod takes over its pools here.
    @Inject(method = "fillRebalancedRecipes", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$fillRebalanced(CallbackInfo ci) {
        for (Pair<TradeOffers.Factory[], Integer> pool : WanderingTraderTradeLoader.rebalancedPools()) {
            fillRecipesFromPool(getOffers(), pool.getLeft(), pool.getRight());
        }

        TradePricing.applyFrom(getOffers(), villagertradingplus$fillStart, this);
        ci.cancel();
    }

    @Override
    public TradeOffers.Factory[] villagertradingplus$catalogPool(int level) {
        return TradeTables.rebalanced(getWorld()) ? WanderingTraderTradeLoader.rebalancedPool(level) : null;
    }

    @Override
    public int villagertradingplus$catalogMaxLevel() {
        return TradeTables.rebalanced(getWorld()) ? WanderingTraderTradeLoader.REBALANCED_BUYING_PAGE : 0;
    }

    @Override
    public int villagertradingplus$catalogPicks(int level) {
        return TradeTables.rebalanced(getWorld()) ? WanderingTraderTradeLoader.rebalancedPicks(level) : 0;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        TradeOfferList offers = getOffers();
        offers.clear();

        if (TradeTables.rebalanced(getWorld())) {
            for (Pair<TradeOffers.Factory[], Integer> pool : WanderingTraderTradeLoader.rebalancedPools()) {
                fillRecipesFromPool(offers, pool.getLeft(), pool.getRight());
            }

            TradePricing.applyFrom(offers, 0, this);
            return;
        }

        TradeOffers.Factory[] common = TradeOffers.WANDERING_TRADER_TRADES.get(1);
        TradeOffers.Factory[] rare = TradeOffers.WANDERING_TRADER_TRADES.get(2);

        if (common != null) {
            fillRecipesFromPool(offers, common, VillagerTradingPlus.CONFIG.trade_offers_wandering_trader);
        }

        if (rare != null) {
            fillRecipesFromPool(offers, rare, 1);
        }

        TradePricing.applyFrom(offers, 0, this);
    }
}
