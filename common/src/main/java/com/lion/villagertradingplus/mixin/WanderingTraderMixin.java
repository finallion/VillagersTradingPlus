package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.TradePools;
import com.lion.villagertradingplus.tradeoffers.TradePricing;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin extends AbstractVillager implements TradeControl {

    public WanderingTraderMixin(EntityType<? extends AbstractVillager> entityType, Level world) {
        super(entityType, world);
    }

    @Unique private int villagertradingplus$offersBeforeFill;

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$drawFromPool(ServerLevel level, CallbackInfo ci) {
        if (!TradePools.drawsWanderingFromPool()) {
            return;
        }

        TradePools.fillWanderingTrader(level, this, getOffers());
        ci.cancel();
    }

    @Inject(method = "updateTrades", at = @At("HEAD"))
    private void villagertradingplus$markFillStart(ServerLevel level, CallbackInfo ci) {
        villagertradingplus$offersBeforeFill = getOffers().size();
    }

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void villagertradingplus$priceVanillaFill(ServerLevel level, CallbackInfo ci) {
        TradePricing.applyFrom(getOffers(), villagertradingplus$offersBeforeFill, this);
    }

    @Override
    public int villagertradingplus$catalogMaxLevel() {
        return TradePools.WANDERING_BUYING_PAGE;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        if (!(level() instanceof ServerLevel serverWorld)) {
            return;
        }

        MerchantOffers offers = getOffers();
        offers.clear();
        TradePools.fillWanderingTrader(serverWorld, this, offers);
    }
}
