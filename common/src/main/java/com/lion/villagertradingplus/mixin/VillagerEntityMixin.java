package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.LevelCounts;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin extends MerchantEntity implements TradeControl {

    public VillagerEntityMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Shadow public abstract VillagerData getVillagerData();

    @Shadow public abstract void setVillagerData(VillagerData villagerData);

    @Shadow public abstract void setExperience(int experience);

    @Shadow public abstract void reinitializeBrain(ServerWorld world);

    @Unique private int[] villagertradingplus$levelCounts = null;
    @Unique private int villagertradingplus$offersBeforeFill;

    @ModifyConstant(method = "fillRecipes", constant = @Constant(intValue = 2))
    private int changeTradeOfferPerLevelCount(int value) {
        return VillagerTradingPlus.CONFIG.trade_offers_per_level;
    }

    @Inject(method = "fillRecipes", at = @At("HEAD"))
    private void villagertradingplus$markFillStart(CallbackInfo ci) {
        villagertradingplus$offersBeforeFill = this.offers == null ? 0 : this.offers.size();
    }

    @Inject(method = "fillRecipes", at = @At("TAIL"))
    private void villagertradingplus$recordFilledTier(CallbackInfo ci) {
        int level = getVillagerData().getLevel();
        int before = villagertradingplus$offersBeforeFill;
        int[] known = villagertradingplus$levelCounts;
        int[] base;

        if (level == 1 || (known != null && known.length >= level && villagertradingplus$sum(known, 1, level - 1) == before)) {
            base = known;
        } else {
            base = villagertradingplus$inferCounts(level - 1, before);

            if (base == null) {
                return;
            }
        }

        int[] updated = new int[level + 1];

        if (base != null) {
            System.arraycopy(base, 0, updated, 0, Math.min(base.length, level));
        }

        updated[level] = this.offers.size() - before;
        villagertradingplus$levelCounts = updated;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void villagertradingplus$writeLevelCounts(NbtCompound nbt, CallbackInfo ci) {
        if (villagertradingplus$levelCounts != null) {
            nbt.putIntArray("VillagerTradingPlusLevelCounts", villagertradingplus$levelCounts);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void villagertradingplus$readLevelCounts(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("VillagerTradingPlusLevelCounts")) {
            villagertradingplus$levelCounts = LevelCounts.clamp(nbt.getIntArray("VillagerTradingPlusLevelCounts"));
        }
    }

    @Override
    public boolean villagertradingplus$supportsLevels() {
        return true;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        VillagerProfession profession = getVillagerData().getProfession();
        Int2ObjectMap<TradeOffers.Factory[]> map = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(profession);

        TradeOfferList offers = getOffers();
        offers.clear();

        int level = getVillagerData().getLevel();
        int[] counts = new int[level + 1];

        if (map != null) {
            for (int tier = 1; tier <= level; tier++) {
                TradeOffers.Factory[] pool = map.get(tier);

                if (pool == null) {
                    continue;
                }

                int before = offers.size();
                fillRecipesFromPool(offers, pool, VillagerTradingPlus.CONFIG.trade_offers_per_level);
                counts[tier] = offers.size() - before;
            }
        }

        villagertradingplus$levelCounts = counts;
    }

    @Override
    public void villagertradingplus$rerollLevel(int targetLevel) {
        villagertradingplus$rerollLevelChecked(targetLevel);
    }

    @Override
    public boolean villagertradingplus$rerollLevelChecked(int targetLevel) {
        int level = getVillagerData().getLevel();

        if (targetLevel < 1 || targetLevel > level) {
            return true;
        }

        TradeOfferList offers = getOffers();
        int[] counts = villagertradingplus$resolveCounts(level, offers.size());

        if (counts == null) {
            return false;
        }

        villagertradingplus$levelCounts = counts;

        VillagerProfession profession = getVillagerData().getProfession();
        Int2ObjectMap<TradeOffers.Factory[]> map = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(profession);
        TradeOffers.Factory[] pool = map == null ? null : map.get(targetLevel);

        int start = villagertradingplus$sum(counts, 1, targetLevel - 1);
        int oldCount = counts[targetLevel];
        for (int i = 0; i < oldCount; i++) {
            offers.remove(start);
        }

        TradeOfferList fresh = new TradeOfferList();

        if (pool != null) {
            fillRecipesFromPool(fresh, pool, VillagerTradingPlus.CONFIG.trade_offers_per_level);
        }

        offers.addAll(start, fresh);
        counts[targetLevel] = fresh.size();
        return true;
    }

    @Override
    public void villagertradingplus$setLevel(int targetLevel) {
        targetLevel = MathHelper.clamp(targetLevel, VillagerData.MIN_LEVEL, VillagerData.MAX_LEVEL);
        setVillagerData(getVillagerData().withLevel(targetLevel));
        setExperience(VillagerData.getLowerLevelExperience(targetLevel));
        villagertradingplus$rerollAll();

        if (getWorld() instanceof ServerWorld serverWorld) {
            reinitializeBrain(serverWorld);
        }
    }

    @Unique
    private int[] villagertradingplus$resolveCounts(int level, int offerCount) {
        int[] counts = villagertradingplus$levelCounts;

        if (counts != null && counts.length > level && villagertradingplus$sum(counts, 1, level) == offerCount) {
            return counts;
        }

        return villagertradingplus$inferCounts(level, offerCount);
    }

    @Unique
    private int[] villagertradingplus$inferCounts(int upToLevel, int offerCount) {
        Int2ObjectMap<TradeOffers.Factory[]> map = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(getVillagerData().getProfession());
        int[] inferred = new int[upToLevel + 1];

        for (int tier = 1; tier <= upToLevel; tier++) {
            TradeOffers.Factory[] pool = map == null ? null : map.get(tier);
            inferred[tier] = pool == null ? 0 : Math.min(VillagerTradingPlus.CONFIG.trade_offers_per_level, pool.length);
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
