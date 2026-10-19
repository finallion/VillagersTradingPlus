package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControl;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
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


// Extends MerchantEntity, because @Shadow sees only members declared on the target itself.
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin extends MerchantEntity implements TradeControl {

    public VillagerEntityMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Shadow public abstract VillagerData getVillagerData();

    @Shadow public abstract void setVillagerData(VillagerData villagerData);

    @Shadow public abstract void setExperience(int experience);

    @Shadow public abstract void reinitializeBrain(ServerWorld world);

    /// Offer counts per level (index = level 1..5), so one tier can be re-rolled in place.
    @Unique private int[] villagertradingplus$levelCounts = null;

    @ModifyConstant(method = "fillRecipes", constant = @Constant(intValue = 2))
    private int changeTradeOfferPerLevelCount(int value) {
        return VillagerTradingPlus.CONFIG.trade_offers_per_level;
    }

    // Entity data goes through ReadView and WriteView. An @Inject on the NBT methods compiles but never fires.
    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void villagertradingplus$writeLevelCounts(WriteView view, CallbackInfo ci) {
        if (villagertradingplus$levelCounts != null) {
            view.putIntArray("VillagerTradingPlusLevelCounts", villagertradingplus$levelCounts);
        }
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void villagertradingplus$readLevelCounts(ReadView view, CallbackInfo ci) {
        villagertradingplus$levelCounts =
                view.getOptionalIntArray("VillagerTradingPlusLevelCounts").orElse(null);
    }

    @Override
    public boolean villagertradingplus$supportsLevels() {
        return true;
    }

    @Override
    public void villagertradingplus$rerollAll() {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        Int2ObjectMap<TradeOffers.Factory[]> map = villagertradingplus$tradesForProfession();

        TradeOfferList offers = getOffers();
        offers.clear();

        int level = getVillagerData().level();
        int[] counts = new int[level + 1];

        if (map != null) {
            for (int tier = 1; tier <= level; tier++) {
                TradeOffers.Factory[] pool = map.get(tier);
                if (pool == null) {
                    continue;
                }
                int before = offers.size();
                fillRecipesFromPool(serverWorld, offers, pool, VillagerTradingPlus.CONFIG.trade_offers_per_level);
                counts[tier] = offers.size() - before;
            }
        }

        villagertradingplus$levelCounts = counts;
    }

    @Override
    public void villagertradingplus$rerollLevel(int targetLevel) {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        int level = getVillagerData().level();
        if (targetLevel < 1 || targetLevel > level) {
            return;
        }

        TradeOfferList offers = getOffers();
        int[] counts = villagertradingplus$levelCounts;

        // Fall back to a full re-roll if we have no reliable per-level mapping (e.g. pre-existing villager).
        if (counts == null || counts.length <= level || villagertradingplus$sum(counts, 1, level) != offers.size()) {
            villagertradingplus$rerollAll();
            return;
        }

        Int2ObjectMap<TradeOffers.Factory[]> map = villagertradingplus$tradesForProfession();
        TradeOffers.Factory[] pool = map == null ? null : map.get(targetLevel);

        int start = villagertradingplus$sum(counts, 1, targetLevel - 1);
        int oldCount = counts[targetLevel];
        for (int i = 0; i < oldCount; i++) {
            offers.remove(start);
        }

        TradeOfferList fresh = new TradeOfferList();
        if (pool != null) {
            fillRecipesFromPool(serverWorld, fresh, pool, VillagerTradingPlus.CONFIG.trade_offers_per_level);
        }
        offers.addAll(start, fresh);
        counts[targetLevel] = fresh.size();
    }

    @Override
    public void villagertradingplus$setLevel(int targetLevel) {
        targetLevel = MathHelper.clamp(targetLevel, VillagerData.MIN_LEVEL, VillagerData.MAX_LEVEL);
        setVillagerData(getVillagerData().withLevel(targetLevel));
        setExperience(VillagerData.getLowerLevelExperience(targetLevel));
        villagertradingplus$rerollAll();
        if (getEntityWorld() instanceof ServerWorld reinitWorld) {
            reinitializeBrain(reinitWorld);
        }
    }

    /// An inline profession entry has no key, so such a villager has no vanilla trade pool and the result is null.
    @Unique
    private Int2ObjectMap<TradeOffers.Factory[]> villagertradingplus$tradesForProfession() {
        return getVillagerData().profession().getKey()
                .map(TradeOffers.PROFESSION_TO_LEVELED_TRADE::get)
                .orElse(null);
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
