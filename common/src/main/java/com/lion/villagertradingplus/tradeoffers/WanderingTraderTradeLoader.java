package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;
import org.apache.commons.lang3.ArrayUtils;

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

    private static Int2ObjectMap<TradeOffers.Factory[]> vanillaBaseline = null;
    private static final Map<Integer, List<TradeOffers.Factory>> accumulator = new HashMap<>();
    private static boolean replaceVanilla = false;
    private static volatile Map<Integer, List<TradeOffers.Factory>> stagedTrades = Map.of();
    private static volatile boolean stagedReplace = false;

    private WanderingTraderTradeLoader() {}

    private static void ensureBaseline() {
        if (vanillaBaseline == null) {
            vanillaBaseline = new Int2ObjectOpenHashMap<>(TradeOffers.WANDERING_TRADER_TRADES);
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

            // Vanilla picks from the rare pool with nextInt(length), so a tier must never be empty.
            if (merged.length == 0) {
                merged = vanillaBaseline.getOrDefault(level, new TradeOffers.Factory[0]);
            }

            TradeOffers.WANDERING_TRADER_TRADES.put(level, merged);
        }
    }
}
