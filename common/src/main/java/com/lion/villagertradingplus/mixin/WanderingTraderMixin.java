package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import java.util.List;

// See VillagerEntityMixin: fillRecipesFromPool/getOffers are declared on MerchantEntity, not on the
// target itself, so the mixin must extend it rather than @Shadow them.
@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderMixin extends MerchantEntity implements TradeControl {

    public WanderingTraderMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    /**
     * Replaces the whole vanilla pool rather than patching a constant. {@code WANDERING_TRADER_TRADES}
     * is an immutable list whose entries carry their own draw count, and {@code fillRecipes} holds no
     * count constant left to modify: a {@code @ModifyConstant} fails its injection at startup.
     */
    @ModifyExpressionValue(
            method = "fillRecipes",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/village/TradeOffers;WANDERING_TRADER_TRADES:Ljava/util/List;",
                    opcode = org.objectweb.asm.Opcodes.GETSTATIC))
    private List<Pair<TradeOffers.Factory[], Integer>> villagertradingplus$mergedPools(List<Pair<TradeOffers.Factory[], Integer>> original) {
        return WanderingTraderTradeLoader.pools();
    }

    @Override
    public void villagertradingplus$rerollAll() {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        TradeOfferList offers = getOffers();
        offers.clear();

        TradeOffers.Factory[] common = WanderingTraderTradeLoader.poolForLevel(1);
        TradeOffers.Factory[] rare = WanderingTraderTradeLoader.poolForLevel(2);

        if (common != null) {
            fillRecipesFromPool(serverWorld, offers, common, VillagerTradingPlus.CONFIG.trade_offers_wandering_trader);
        }
        if (rare != null) {
            fillRecipesFromPool(serverWorld, offers, rare, 1);
        }
    }
}
