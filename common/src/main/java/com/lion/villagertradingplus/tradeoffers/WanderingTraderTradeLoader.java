package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import com.lion.villagertradingplus.VillagerTradingPlus;
import net.minecraft.village.TradeOffers;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Level 1 is the common pool. Level 2 is the rare pool.
/// Reloads start from the vanilla snapshot, so trades never duplicate.
public final class WanderingTraderTradeLoader {

    public static final Map<String, Integer> LEVEL_MAPPING = new HashMap<>();

    static {
        LEVEL_MAPPING.put("common", 1);
        LEVEL_MAPPING.put("rare", 2);
    }

    public static final int REBALANCED_BUYING_PAGE = 3;
    private static final int REBALANCED_BUYING_INDEX = 0;
    private static final int REBALANCED_RARE_INDEX = 1;
    private static final int REBALANCED_COMMON_INDEX = 2;

    private static Int2ObjectMap<TradeOffers.Factory[]> vanillaBaseline = null;
    private static List<Pair<TradeOffers.Factory[], Integer>> rebalancedBaseline = null;
    private static volatile List<Pair<TradeOffers.Factory[], Integer>> rebalancedPools = null;
    private static final Map<Integer, List<TradeOffers.Factory>> accumulator = new HashMap<>();
    private static boolean replaceVanilla = false;
    private static volatile Map<Integer, List<TradeOffers.Factory>> stagedTrades = Map.of();
    private static volatile boolean stagedReplace = false;

    private WanderingTraderTradeLoader() {}

    private static void ensureBaseline() {
        if (vanillaBaseline == null) {
            vanillaBaseline = new Int2ObjectOpenHashMap<>(TradeOffers.WANDERING_TRADER_TRADES);
        }

        if (rebalancedBaseline == null) {
            rebalancedBaseline = List.copyOf(TradeOffers.REBALANCED_WANDERING_TRADER_TRADES);
        }
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

    public static void stage() {
        Map<Integer, List<TradeOffers.Factory>> copy = new HashMap<>();
        accumulator.forEach((level, factories) -> copy.put(level, List.copyOf(factories)));
        stagedTrades = copy;
        stagedReplace = replaceVanilla;
        buildRebalancedPools();
    }

    public static List<Pair<TradeOffers.Factory[], Integer>> rebalancedPools() {
        List<Pair<TradeOffers.Factory[], Integer>> pools = rebalancedPools;
        return pools != null ? pools : TradeOffers.REBALANCED_WANDERING_TRADER_TRADES;
    }

    public static TradeOffers.Factory[] rebalancedPool(int level) {
        List<Pair<TradeOffers.Factory[], Integer>> pools = rebalancedPools();
        int index = rebalancedIndex(level);
        return index < pools.size() ? pools.get(index).getLeft() : null;
    }

    public static int rebalancedPicks(int level) {
        List<Pair<TradeOffers.Factory[], Integer>> pools = rebalancedPools();
        int index = rebalancedIndex(level);
        return index < pools.size() ? pools.get(index).getRight() : 0;
    }

    private static int rebalancedIndex(int level) {
        if (level == REBALANCED_BUYING_PAGE) {
            return REBALANCED_BUYING_INDEX;
        }

        return level == 1 ? REBALANCED_COMMON_INDEX : REBALANCED_RARE_INDEX;
    }

    private static void buildRebalancedPools() {
        ensureBaseline();
        List<Pair<TradeOffers.Factory[], Integer>> built = new ArrayList<>();

        for (int index = 0; index < rebalancedBaseline.size(); index++) {
            Pair<TradeOffers.Factory[], Integer> pair = rebalancedBaseline.get(index);
            int level = index == REBALANCED_COMMON_INDEX ? 1 : index == REBALANCED_RARE_INDEX ? 2 : 0;

            if (level == 0) {
                built.add(pair);
                continue;
            }

            TradeOffers.Factory[] base = replaceVanilla ? new TradeOffers.Factory[0] : pair.getLeft();
            List<TradeOffers.Factory> extra = accumulator.getOrDefault(level, List.of());
            TradeOffers.Factory[] merged = ArrayUtils.addAll(base, extra.toArray(new TradeOffers.Factory[0]));

            // An empty tier would throw in vanilla, which picks the rare offer with nextInt(length), so keep the vanilla offers.
            if (merged.length == 0) {
                merged = pair.getLeft();
            }

            int picks = level == 1 ? VillagerTradingPlus.CONFIG.trade_offers_wandering_trader : pair.getRight();
            built.add(Pair.of(merged, picks));
        }

        rebalancedPools = List.copyOf(built);
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
        stage();

        Set<Integer> levels = new HashSet<>(accumulator.keySet());
        vanillaBaseline.keySet().forEach(level -> levels.add((int) level));

        for (int level : levels) {
            TradeOffers.Factory[] base = replaceVanilla
                    ? new TradeOffers.Factory[0]
                    : vanillaBaseline.getOrDefault(level, new TradeOffers.Factory[0]);

            List<TradeOffers.Factory> extra = accumulator.getOrDefault(level, List.of());
            TradeOffers.Factory[] merged = ArrayUtils.addAll(base, extra.toArray(new TradeOffers.Factory[0]));

            // An empty tier would throw in vanilla, which picks the rare offer with nextInt(length), so keep the vanilla offers.
            if (merged.length == 0) {
                merged = vanillaBaseline.getOrDefault(level, new TradeOffers.Factory[0]);
            }

            TradeOffers.WANDERING_TRADER_TRADES.put(level, merged);
        }
    }
}
