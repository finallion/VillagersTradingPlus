package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.TradePools;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerEntityMixin extends AbstractVillager implements TradeControl {

    public VillagerEntityMixin(EntityType<? extends AbstractVillager> entityType, Level world) {
        super(entityType, world);
    }

    @Shadow public abstract VillagerData getVillagerData();

    @Shadow public abstract void setVillagerData(VillagerData villagerData);

    @Shadow public abstract void setVillagerXp(int experience);

    @Shadow public abstract void refreshBrain(ServerLevel world);

    @Unique private int[] villagertradingplus$levelCounts = null;
    @Unique private int villagertradingplus$offersBeforeFill;

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$drawFromPool(ServerLevel level, CallbackInfo ci) {
        Holder<VillagerProfession> profession = getVillagerData().profession();

        if (!TradePools.drawsFromPool(profession)) {
            return;
        }

        MerchantOffers offers = getOffers();
        int tier = getVillagerData().level();
        int before = offers.size();
        int added = TradePools.addVillagerOffers(level, this, profession, tier, offers);
        villagertradingplus$recordTier(level, tier, before, added);
        ci.cancel();
    }

    @Inject(method = "updateTrades", at = @At("HEAD"))
    private void villagertradingplus$markFillStart(ServerLevel level, CallbackInfo ci) {
        villagertradingplus$offersBeforeFill = getOffers().size();
    }

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void villagertradingplus$recordVanillaTier(ServerLevel level, CallbackInfo ci) {
        villagertradingplus$recordTier(
                level,
                getVillagerData().level(),
                villagertradingplus$offersBeforeFill,
                getOffers().size() - villagertradingplus$offersBeforeFill
        );
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void villagertradingplus$writeLevelCounts(ValueOutput view, CallbackInfo ci) {
        if (villagertradingplus$levelCounts != null) {
            view.putIntArray("VillagerTradingPlusLevelCounts", villagertradingplus$levelCounts);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void villagertradingplus$readLevelCounts(ValueInput view, CallbackInfo ci) {
        villagertradingplus$levelCounts =
                view.getIntArray("VillagerTradingPlusLevelCounts").orElse(null);
    }

    @Override
    public boolean villagertradingplus$supportsLevels() {
        return true;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        if (!(level() instanceof ServerLevel serverWorld)) {
            return;
        }

        MerchantOffers offers = getOffers();
        offers.clear();

        int level = getVillagerData().level();
        int[] counts = new int[Math.max(level, VillagerData.MAX_VILLAGER_LEVEL) + 1];
        for (int tier = 1; tier <= level; tier++) {
            counts[tier] = villagertradingplus$addTier(serverWorld, offers, tier);
        }
        villagertradingplus$levelCounts = counts;
    }

    @Override
    public void villagertradingplus$rerollLevel(int targetLevel) {
        villagertradingplus$rerollLevelChecked(targetLevel);
    }

    @Override
    public boolean villagertradingplus$rerollLevelChecked(int targetLevel) {
        if (!(level() instanceof ServerLevel serverWorld)) {
            return true;
        }

        int level = getVillagerData().level();

        if (targetLevel < 1 || targetLevel > level) {
            return true;
        }

        MerchantOffers offers = getOffers();
        int[] counts = villagertradingplus$resolveCounts(serverWorld, level, offers.size());

        if (counts == null) {
            return false;
        }

        villagertradingplus$levelCounts = counts;

        int start = villagertradingplus$sum(counts, 1, targetLevel - 1);
        int oldCount = counts[targetLevel];
        for (int i = 0; i < oldCount; i++) {
            offers.remove(start);
        }

        MerchantOffers fresh = new MerchantOffers();
        villagertradingplus$addTier(serverWorld, fresh, targetLevel);
        offers.addAll(start, fresh);
        counts[targetLevel] = fresh.size();
        return true;
    }

    @Override
    public void villagertradingplus$setLevel(int targetLevel) {
        targetLevel = Mth.clamp(targetLevel, VillagerData.MIN_VILLAGER_LEVEL, VillagerData.MAX_VILLAGER_LEVEL);
        setVillagerData(getVillagerData().withLevel(targetLevel));
        setVillagerXp(VillagerData.getMinXpPerLevel(targetLevel));
        villagertradingplus$rerollAll();

        if (level() instanceof ServerLevel reinitWorld) {
            refreshBrain(reinitWorld);
        }
    }

    @Unique
    private int villagertradingplus$addTier(ServerLevel level, MerchantOffers target, int tier) {
        Holder<VillagerProfession> profession = getVillagerData().profession();

        if (TradePools.drawsFromPool(profession)) {
            return TradePools.addVillagerOffers(level, this, profession, tier, target);
        }

        ResourceKey<TradeSet> set = profession.value().getTrades(tier);
        int before = target.size();

        if (set != null) {
            addOffersFromTradeSet(level, target, set);
        }

        return target.size() - before;
    }

    @Unique
    private void villagertradingplus$recordTier(ServerLevel level, int tier, int before, int added) {
        int[] known = villagertradingplus$levelCounts;
        int[] base;

        if (tier == 1 || (known != null && known.length >= tier && villagertradingplus$sum(known, 1, tier - 1) == before)) {
            base = known;
        } else {
            base = villagertradingplus$inferCounts(level, tier - 1, before);

            if (base == null) {
                return;
            }
        }

        int[] updated = new int[Math.max(tier, VillagerData.MAX_VILLAGER_LEVEL) + 1];

        if (base != null) {
            System.arraycopy(base, 0, updated, 0, Math.min(base.length, tier));
        }

        updated[tier] = added;
        villagertradingplus$levelCounts = updated;
    }

    @Unique
    private int[] villagertradingplus$resolveCounts(ServerLevel level, int villagerLevel, int offerCount) {
        int[] counts = villagertradingplus$levelCounts;

        if (counts != null
                && counts.length > villagerLevel
                && villagertradingplus$sum(counts, 1, villagerLevel) == offerCount) {
            return counts;
        }

        return villagertradingplus$inferCounts(level, villagerLevel, offerCount);
    }

    @Unique
    private int[] villagertradingplus$inferCounts(ServerLevel level, int upToLevel, int offerCount) {
        Holder<VillagerProfession> profession = getVillagerData().profession();
        int[] inferred = new int[Math.max(upToLevel, VillagerData.MAX_VILLAGER_LEVEL) + 1];

        for (int tier = 1; tier <= upToLevel; tier++) {
            inferred[tier] = Math.min(
                    TradePools.villagerPicks(level, this, profession, tier),
                    TradePools.forVillager(level, profession, tier).size()
            );
        }

        return villagertradingplus$sum(inferred, 1, upToLevel) == offerCount ? inferred : null;
    }

    @Unique
    private static int villagertradingplus$sum(int[] counts, int fromInclusive, int toInclusive) {
        int total = 0;
        for (int i = fromInclusive; i <= toInclusive && i < counts.length; i++) {
            total += counts[i];
        }
        return total;
    }
}
