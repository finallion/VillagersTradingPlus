package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Vanilla lists three pools in this order: buying, rare, common.
/// The vanilla list is immutable, so `WanderingTraderMixin` serves the merged pools instead.
/// Reloads start from the vanilla snapshot, so trades never duplicate.
public final class WanderingTraderTradeLoader {

    public static final Map<String, Integer> LEVEL_MAPPING = new HashMap<>();

    static {
        LEVEL_MAPPING.put("common", 1);
        LEVEL_MAPPING.put("rare", 2);
    }

    private static final int COMMON_LEVEL = 1;
    private static final int RARE_LEVEL = 2;
    private static final int VANILLA_RARE_INDEX = 1;
    private static final int VANILLA_COMMON_INDEX = 2;
    private static final int VANILLA_POOL_COUNT = 3;

    private static Int2ObjectMap<TradeOffers.Factory[]> vanillaBaseline = null;
    private static Int2IntMap vanillaCounts = null;
    private static Pair<TradeOffers.Factory[], Integer> buyingPair = null;
    private static List<Pair<TradeOffers.Factory[], Integer>> extraPairs = List.of();

    private static final Map<Integer, List<TradeOffers.Factory>> accumulator = new HashMap<>();
    private static boolean replaceVanilla = false;
    private static volatile Map<Integer, List<TradeOffers.Factory>> stagedTrades = Map.of();
    private static volatile boolean stagedReplace = false;
    private static volatile boolean servedByLoaderEvent = false;

    private static volatile Int2ObjectMap<TradeOffers.Factory[]> mergedPools = null;

    private WanderingTraderTradeLoader() {}

    private static void ensureBaseline() {
        if (vanillaBaseline != null) {
            return;
        }

        vanillaBaseline = new Int2ObjectOpenHashMap<>();
        vanillaCounts = new Int2IntOpenHashMap();
        List<Pair<TradeOffers.Factory[], Integer>> extras = new ArrayList<>();

        List<Pair<TradeOffers.Factory[], Integer>> vanilla = TradeOffers.WANDERING_TRADER_TRADES;

        for (int index = 0; index < vanilla.size(); index++) {
            Pair<TradeOffers.Factory[], Integer> pair = vanilla.get(index);

            if (index == VANILLA_RARE_INDEX) {
                vanillaBaseline.put(RARE_LEVEL, pair.getLeft());
                vanillaCounts.put(RARE_LEVEL, pair.getRight().intValue());
            } else if (index == VANILLA_COMMON_INDEX) {
                vanillaBaseline.put(COMMON_LEVEL, pair.getLeft());
                vanillaCounts.put(COMMON_LEVEL, pair.getRight().intValue());
            } else if (index == 0) {
                buyingPair = pair;
            } else if (index >= VANILLA_POOL_COUNT) {
                extras.add(pair);
            }
        }

        extraPairs = List.copyOf(extras);
    }

    public static void begin() {
        ensureBaseline();
        accumulator.clear();
        replaceVanilla = false;
    }

    public static void setReplace(boolean replace) {
        if (replace) {
            replaceVanilla = true;
        }
    }

    public static void add(int level, TradeOffers.Factory factory) {
        accumulator.computeIfAbsent(level, k -> new ArrayList<>()).add(factory);
    }

    public static void setServedByLoaderEvent(boolean served) {
        servedByLoaderEvent = served;
    }

    public static boolean isServedByLoaderEvent() {
        return servedByLoaderEvent;
    }

    public static void applyStagedTo(int level, List<TradeOffers.Factory> target) {
        List<TradeOffers.Factory> staged = stagedTrades.getOrDefault(level, List.of());

        if (staged.isEmpty()) {
            return;
        }

        if (stagedReplace) {
            target.clear();
        }

        target.addAll(staged);
    }

    public static void apply() {
        ensureBaseline();

        Map<Integer, List<TradeOffers.Factory>> copy = new HashMap<>();
        accumulator.forEach((level, factories) -> copy.put(level, List.copyOf(factories)));
        stagedTrades = copy;
        stagedReplace = replaceVanilla;

        Set<Integer> levels = new HashSet<>(accumulator.keySet());
        vanillaBaseline.keySet().forEach(level -> levels.add((int) level));

        Int2ObjectMap<TradeOffers.Factory[]> rebuilt = new Int2ObjectOpenHashMap<>();

        for (int level : levels) {
            TradeOffers.Factory[] base = replaceVanilla
                    ? new TradeOffers.Factory[0]
                    : vanillaBaseline.getOrDefault(level, new TradeOffers.Factory[0]);

            List<TradeOffers.Factory> extra = accumulator.getOrDefault(level, List.of());
            TradeOffers.Factory[] merged = ArrayUtils.addAll(base, extra.toArray(new TradeOffers.Factory[0]));

            // Vanilla picks from the rare pool with nextInt(length). An empty tier throws.
            if (merged.length == 0) {
                merged = vanillaBaseline.getOrDefault(level, new TradeOffers.Factory[0]);
            }

            rebuilt.put(level, merged);
        }

        mergedPools = rebuilt;
    }

    private static Pair<TradeOffers.Factory[], Integer> eventPair(int index) {
        List<Pair<TradeOffers.Factory[], Integer>> live = TradeOffers.WANDERING_TRADER_TRADES;
        return index < live.size() ? live.get(index) : null;
    }

    public static TradeOffers.Factory[] poolForLevel(int level) {
        if (servedByLoaderEvent) {
            Pair<TradeOffers.Factory[], Integer> pair = eventPair(level == RARE_LEVEL ? VANILLA_RARE_INDEX : VANILLA_COMMON_INDEX);
            return pair == null ? null : pair.getLeft();
        }

        Int2ObjectMap<TradeOffers.Factory[]> pools = mergedPools;

        if (pools != null) {
            return pools.get(level);
        }

        ensureBaseline();
        return vanillaBaseline.get(level);
    }

    public static int levelCount() {
        Int2ObjectMap<TradeOffers.Factory[]> pools = mergedPools;

        if (pools != null) {
            return pools.size();
        }

        ensureBaseline();
        return vanillaBaseline.size();
    }

    public static Pair<TradeOffers.Factory[], Integer> buyingPair() {
        if (servedByLoaderEvent) {
            return eventPair(0);
        }

        ensureBaseline();
        return buyingPair;
    }

    public static int countForLevel(int level) {
        ensureBaseline();
        return level == COMMON_LEVEL
                ? VillagerTradingPlus.CONFIG.trade_offers_wandering_trader
                : vanillaCounts.getOrDefault(level, 1);
    }

    public static List<Pair<TradeOffers.Factory[], Integer>> pools() {
        ensureBaseline();
        Int2ObjectMap<TradeOffers.Factory[]> pools = mergedPools != null ? mergedPools : vanillaBaseline;

        List<Pair<TradeOffers.Factory[], Integer>> out = new ArrayList<>();

        if (buyingPair != null) {
            out.add(buyingPair);
        }

        if (pools.get(RARE_LEVEL) != null) {
            out.add(Pair.of(pools.get(RARE_LEVEL), countForLevel(RARE_LEVEL)));
        }

        if (pools.get(COMMON_LEVEL) != null) {
            out.add(Pair.of(pools.get(COMMON_LEVEL), countForLevel(COMMON_LEVEL)));
        }

        out.addAll(extraPairs);
        return out;
    }
}
