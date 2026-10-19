package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.gen.structure.Structure;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/// Accumulates catalogue rows while CatalogExpansion walks a tier's factory pool.
/// Row metadata spans nesting levels, so the builder keeps three scope stacks.
/// The stacks hold conditions, pool weight and price factor. Each row snapshots them.
/// Expanders push on the way down and pop on the way back up.
public final class CatalogBuilder {

    /// The 1 MiB CustomPayloadS2CPacket limit bounds the row count.
    public static final int MAX_ENTRIES = 512;

    /// The fixed seed keeps fallback rows stable between opens.
    private static final long CATALOG_SEED = 0L;

    private record ConditionGroup(List<ConditionInfo> infos, boolean satisfied) {
    }

    private final List<CatalogEntry> entries = new ArrayList<>();

    /// {weight, totalWeight} of each enclosing weighted_pool, innermost last.
    private final Deque<int[]> weightStack = new ArrayDeque<>();
    private final List<ConditionGroup> conditionStack = new ArrayList<>();

    /// Saved composed values, restored on pop. Snapshots avoid float drift from division.
    private final Deque<Float> shareStack = new ArrayDeque<>();
    private final Deque<Float> priceFactorStack = new ArrayDeque<>();

    private final int tierPoolSize;
    private final int tierPicks;

    private float composedShare = 1.0f;
    private float composedPriceFactor = 1.0f;
    private int skipped;

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

    /// The weight is shown verbatim and folded into the row probability.
    public void pushWeight(int weight, int totalWeight) {
        this.weightStack.addLast(new int[]{weight, totalWeight});
        pushShare(totalWeight > 0 ? (float) weight / totalWeight : 1.0f);
    }

    public void popWeight() {
        this.weightStack.pollLast();
        popShare();
    }

    /// Narrows the row probability without a JSON weight, for uniform splits such as one enchantment of forty.
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

    /// Nested gates must all open, so a row lists the conditions of every enclosing group.
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

    /// Factors compose multiplicatively, so the catalogue matches a generated offer at any nesting depth.
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

    /// Raw prices are before the pricing factor. The displayed stack shows the low end.
    public void add(ItemStack firstBuy, ItemStack secondBuy, ItemStack sell,
                    int maxUses, int villagerExperience, float priceMultiplier, int demand,
                    int rawMinPrice, int rawMaxPrice) {
        if (isFull()) {
            this.skipped++;
            return;
        }

        int minPrice = scale(rawMinPrice, firstBuy.getMaxCount());
        int maxPrice = scale(rawMaxPrice, firstBuy.getMaxCount());

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

    public void addOffer(TradeOffer offer) {
        int price = offer.getOriginalFirstBuyItem().getCount();
        addOffer(offer, price, price);
    }

    public void addOffer(TradeOffer offer, int rawMinPrice, int rawMaxPrice) {
        add(offer.getOriginalFirstBuyItem(), offer.getDisplayedSecondBuyItem(), offer.getSellItem(),
                offer.getMaxUses(), offer.getMerchantExperience(), offer.getPriceMultiplier(),
                offer.getDemandBonus(), rawMinPrice, rawMaxPrice);
    }

    /// Rolls the factory once with a fixed seed, so the row stays stable when create is random.
    public void addSampled(TradeOffers.Factory factory, Entity merchant) {
        if (isStructureMapFactory(factory)) {
            countSkipped(1);
            return;
        }

        if (!(merchant.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
            return;
        }
        TradeOffer offer = factory.create(world, merchant, Random.create(CATALOG_SEED));
        if (offer != null) {
            addOffer(offer);
        }
    }

    /// Vanilla map trades search for a structure when rolled. The factory class is private, so match its tag field.
    private static boolean isStructureMapFactory(TradeOffers.Factory factory) {
        Class<?> type = factory.getClass();

        if (type.getEnclosingClass() != TradeOffers.class) {
            return false;
        }

        for (Field field : type.getDeclaredFields()) {
            if (field.getType() == TagKey.class
                    && field.getGenericType() instanceof ParameterizedType tagType
                    && tagType.getActualTypeArguments()[0] == Structure.class) {
                return true;
            }
        }

        return false;
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
