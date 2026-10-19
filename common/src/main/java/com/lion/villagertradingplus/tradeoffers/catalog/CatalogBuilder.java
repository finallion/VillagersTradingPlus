package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import com.lion.villagertradingplus.tradeoffers.VanillaTradeFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/// Collects catalogue rows while [CatalogExpansion] walks a pool.
/// A pool knows the weights and a leaf knows the items, so each row copies three scope stacks.
public final class CatalogBuilder {

    public static final int MAX_ENTRIES = 512;

    /// Fixed seed for the unenumerable-factory fallback, so those rows never change between opens.
    private static final long CATALOG_SEED = 0L;

    private record ConditionGroup(List<ConditionInfo> infos, boolean satisfied) {
    }

    private final List<CatalogEntry> entries = new ArrayList<>();

    /// {weight, totalWeight} of each enclosing weighted_pool, innermost last.
    private final Deque<int[]> weightStack = new ArrayDeque<>();
    private final List<ConditionGroup> conditionStack = new ArrayList<>();

    /// Saved values restored on pop. Division would accumulate float drift.
    private final Deque<Float> shareStack = new ArrayDeque<>();
    private final Deque<Float> priceFactorStack = new ArrayDeque<>();

    private final int tierPoolSize;
    private final int tierPicks;

    private float composedShare = 1.0f;
    private float composedPriceFactor = 1.0f;
    private int skipped;

    /// Flattened view of [#conditionStack], rebuilt only when the stack actually changes.
    private List<ConditionInfo> flatConditions = List.of();
    private boolean conditionsMet = true;

    public CatalogBuilder(int tierPoolSize, int tierPicks) {
        this.tierPoolSize = tierPoolSize;
        this.tierPicks = tierPicks;
    }

    public void reset() {
        this.weightStack.clear();
        this.conditionStack.clear();
        this.shareStack.clear();
        this.priceFactorStack.clear();
        this.composedShare = 1.0f;
        this.composedPriceFactor = 1.0f;
        refreshConditions();
    }

    public void pushWeight(int weight, int totalWeight) {
        this.weightStack.addLast(new int[]{weight, totalWeight});
        pushShare(totalWeight > 0 ? (float) weight / totalWeight : 1.0f);
    }

    public void popWeight() {
        this.weightStack.pollLast();
        popShare();
    }

    /// Narrows the probability without a JSON weight, for uniform splits such as one enchantment of forty.
    public void pushShare(float share) {
        this.shareStack.addLast(this.composedShare);
        this.composedShare *= share;
    }

    public void popShare() {
        Float saved = this.shareStack.pollLast();
        if (saved != null) {
            this.composedShare = saved;
        }
    }

    public void pushConditions(List<ConditionInfo> infos, boolean satisfied) {
        this.conditionStack.add(new ConditionGroup(infos, satisfied));
        refreshConditions();
    }

    public void popConditions() {
        if (!this.conditionStack.isEmpty()) {
            this.conditionStack.remove(this.conditionStack.size() - 1);
            refreshConditions();
        }
    }

    public void pushPriceFactor(float factor) {
        this.priceFactorStack.addLast(this.composedPriceFactor);
        this.composedPriceFactor *= factor;
    }

    public void popPriceFactor() {
        Float saved = this.priceFactorStack.pollLast();
        if (saved != null) {
            this.composedPriceFactor = saved;
        }
    }

    public void add(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell,
                    int maxUses, int villagerExperience, float priceMultiplier, int demand) {
        int price = firstBuy.getCount();
        add(firstBuy, secondBuy, sell, maxUses, villagerExperience, priceMultiplier, demand, price, price);
    }

    /// Raw prices are before the pricing factor. The stack shows the low end.
    public void add(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell,
                    int maxUses, int villagerExperience, float priceMultiplier, int demand,
                    int rawMinPrice, int rawMaxPrice) {
        if (isFull()) {
            this.skipped++;
            return;
        }

        int minPrice = scale(rawMinPrice, firstBuy.getMaxStackSize());
        int maxPrice = scale(rawMaxPrice, firstBuy.getMaxStackSize());

        ItemStack shownFirstBuy = firstBuy.copy();
        shownFirstBuy.setCount(minPrice);

        int[] innermost = this.weightStack.peekLast();
        int weight = innermost == null ? 0 : innermost[0];
        int totalWeight = innermost == null ? 0 : innermost[1];

        this.entries.add(new CatalogEntry(shownFirstBuy, secondBuy.copy(), sell.copy(),
                weight, totalWeight, this.composedShare, this.tierPoolSize, this.tierPicks,
                maxUses, villagerExperience, priceMultiplier, demand, minPrice, maxPrice,
                this.flatConditions, this.conditionsMet));
    }

    public void addOffer(MerchantOffer offer) {
        int price = offer.getBaseCostA().getCount();
        addOffer(offer, price, price);
    }

    public void addOffer(MerchantOffer offer, int rawMinPrice, int rawMaxPrice) {
        add(offer.getBaseCostA(), offer.getCostB(), offer.getResult(),
                offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier(),
                offer.getDemand(), rawMinPrice, rawMaxPrice);
    }

    /// Rolls the factory once with a fixed seed, so the row stays stable between openings.
    public void addSampled(TradeFactory factory, Entity merchant) {
        if (factory instanceof VanillaTradeFactory vanilla && vanilla.rollsStructureSearch()) {
            countSkipped(1);
            return;
        }

        if (!(merchant.level() instanceof net.minecraft.server.level.ServerLevel world)) {
            return;
        }
        MerchantOffer offer = factory.create(world, merchant, RandomSource.create(CATALOG_SEED));
        if (offer != null) {
            addOffer(offer);
        }
    }

    public boolean isFull() {
        return this.entries.size() >= MAX_ENTRIES;
    }

    public void countSkipped(int count) {
        this.skipped += Math.max(0, count);
    }

    public List<CatalogEntry> entries() {
        return this.entries;
    }

    public int skipped() {
        return this.skipped;
    }

    private void refreshConditions() {
        if (this.conditionStack.isEmpty()) {
            this.flatConditions = List.of();
            this.conditionsMet = true;
            return;
        }

        List<ConditionInfo> flat = new ArrayList<>();
        boolean met = true;
        for (ConditionGroup group : this.conditionStack) {
            flat.addAll(group.infos());
            met &= group.satisfied();
        }
        this.flatConditions = List.copyOf(flat);
        this.conditionsMet = met;
    }

    private int scale(int rawPrice, int maxCount) {
        return PricingTradeFactory.scaleCount(rawPrice, maxCount, this.composedPriceFactor);
    }
}
