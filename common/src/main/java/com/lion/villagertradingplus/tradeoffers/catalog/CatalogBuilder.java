package com.lion.villagertradingplus.tradeoffers.catalog;

import com.lion.villagertradingplus.mixin.SellMapFactoryAccessor;
import com.lion.villagertradingplus.tradeoffers.PricingTradeFactory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
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

public final class CatalogBuilder {

    /// The 1 MiB CustomPayloadS2CPacket limit bounds the row count.
    public static final int MAX_ENTRIES = 512;

    private static final long CATALOG_SEED = 0L;

    private static final float VANILLA_MAP_PRICE_MULTIPLIER = 0.2f;

    private record ConditionGroup(List<ConditionInfo> infos, boolean satisfied) {
    }

    private final List<CatalogEntry> entries = new ArrayList<>();

    private final Deque<int[]> weightStack = new ArrayDeque<>();
    private final List<ConditionGroup> conditionStack = new ArrayList<>();

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

    public void pushWeight(int weight, int totalWeight) {
        this.weightStack.addLast(new int[]{weight, totalWeight});
        pushShare(totalWeight > 0 ? (float) weight / totalWeight : 1.0f);
    }

    public void popWeight() {
        this.weightStack.pollLast();
        popShare();
    }

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

    public void add(
            ItemStack firstBuy,
            ItemStack secondBuy,
            ItemStack sell,
            int maxUses,
            int villagerExperience,
            float priceMultiplier,
            int demand
    ) {
        int price = firstBuy.getCount();
        add(firstBuy, secondBuy, sell, maxUses, villagerExperience, priceMultiplier, demand, price, price);
    }

    public void add(
            ItemStack firstBuy,
            ItemStack secondBuy,
            ItemStack sell,
            int maxUses,
            int villagerExperience,
            float priceMultiplier,
            int demand,
            int rawMinPrice,
            int rawMaxPrice
    ) {
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

        this.entries.add(
                new CatalogEntry(
                        shownFirstBuy,
                        secondBuy.copy(),
                        sell.copy(),
                        weight,
                        totalWeight,
                        this.composedShare,
                        this.tierPoolSize,
                        this.tierPicks,
                        maxUses,
                        villagerExperience,
                        priceMultiplier,
                        demand,
                        minPrice,
                        maxPrice,
                        this.flatConditions,
                        this.conditionsMet
                )
        );
    }

    public void addOffer(TradeOffer offer) {
        int price = offer.getOriginalFirstBuyItem().getCount();
        addOffer(offer, price, price);
    }

    public void addOffer(TradeOffer offer, int rawMinPrice, int rawMaxPrice) {
        add(
                offer.getOriginalFirstBuyItem(),
                offer.getDisplayedSecondBuyItem(),
                offer.getSellItem(),
                offer.getMaxUses(),
                offer.getMerchantExperience(),
                offer.getPriceMultiplier(),
                offer.getDemandBonus(),
                rawMinPrice,
                rawMaxPrice
        );
    }

    public void addSampled(TradeOffers.Factory factory, Entity merchant) {
        if (isStructureMapFactory(factory)) {
            addStaticMapRow(factory);
            return;
        }

        TradeOffer offer = factory.create(merchant, Random.create(CATALOG_SEED));

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

    /// Create runs a structure search, so the row is built from the factory fields only.
    private void addStaticMapRow(TradeOffers.Factory factory) {
        if (!(factory instanceof SellMapFactoryAccessor accessor)) {
            countSkipped(1);
            return;
        }

        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponentTypes.ITEM_NAME, Text.translatable(accessor.villagertradingplus$nameKey()));

        add(
                new ItemStack(Items.EMERALD, accessor.villagertradingplus$price()),
                new ItemStack(Items.COMPASS),
                map,
                accessor.villagertradingplus$maxUses(),
                accessor.villagertradingplus$experience(),
                VANILLA_MAP_PRICE_MULTIPLIER,
                0
        );
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
