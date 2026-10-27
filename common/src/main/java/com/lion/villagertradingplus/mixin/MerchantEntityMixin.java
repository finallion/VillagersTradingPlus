package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.TradePricing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantEntity.class)
public abstract class MerchantEntityMixin {

    @Unique private int villagertradingplus$poolFillStart;

    @Inject(method = "fillRecipesFromPool", at = @At("HEAD"))
    private void villagertradingplus$markPoolFillStart(
            TradeOfferList recipeList,
            TradeOffers.Factory[] pool,
            int count,
            CallbackInfo ci
    ) {
        villagertradingplus$poolFillStart = recipeList.size();
    }

    @Inject(method = "fillRecipesFromPool", at = @At("TAIL"))
    private void villagertradingplus$priceDrawnOffers(
            TradeOfferList recipeList,
            TradeOffers.Factory[] pool,
            int count,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof WanderingTraderEntity) {
            return;
        }

        TradePricing.applyFrom(recipeList, villagertradingplus$poolFillStart, (Entity) (Object) this);
    }
}
