package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/// Forge and NeoForge stage the trades and hand them to the loader trade events instead.
public final class TradeMerger {

    private static Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> vanilla;

    private static Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> rebalancedVanilla;

    private static final Set<VillagerProfession> TOUCHED = new HashSet<>();

    private static volatile Map<VillagerProfession, ? extends Int2ObjectMap<TradeOffers.Factory[]>> stagedDefaults = Map.of();
    private static volatile Map<VillagerProfession, ? extends Int2ObjectMap<TradeOffers.Factory[]>> stagedAll = Map.of();

    private TradeMerger() {}

    public static void resetToVanilla() {
        resetRebalanced();

        if (vanilla == null) {
            vanilla = new HashMap<>();
            TradeOffers.PROFESSION_TO_LEVELED_TRADE.forEach((profession, levels) -> vanilla.put(profession, copy(levels)));
            return;
        }

        for (VillagerProfession profession : TOUCHED) {
            Int2ObjectMap<TradeOffers.Factory[]> original = vanilla.get(profession);

            if (original == null) {
                TradeOffers.PROFESSION_TO_LEVELED_TRADE.remove(profession);
            } else {
                TradeOffers.PROFESSION_TO_LEVELED_TRADE.put(profession, copy(original));
            }
        }

        TOUCHED.clear();
    }

    public static void installDefaults(
            Map<VillagerProfession, ? extends Int2ObjectMap<TradeOffers.Factory[]>> defaults
    ) {
        defaults.forEach((profession, levels) -> {
            TOUCHED.add(profession);
            TradeOffers.PROFESSION_TO_LEVELED_TRADE.put(profession, levels);
        });

        installRebalancedDefaults(defaults);
    }

    public static void installStagedDefaults() {
        installDefaults(stagedDefaults);
    }

    public static void mergeIntoVanilla() {
        mergeIntoRebalanced(TradeOfferRegistryLoader.getRegistryForLoading());

        TradeOfferRegistryLoader.getRegistryForLoading().forEach((profession, loadedTrades) -> {
            TOUCHED.add(profession);
            Int2ObjectMap<TradeOffers.Factory[]> target =
                    TradeOffers.PROFESSION_TO_LEVELED_TRADE.computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>());

            for (Int2ObjectMap.Entry<TradeOffers.Factory[]> entry : loadedTrades.int2ObjectEntrySet()) {
                TradeOffers.Factory[] existing = target.get(entry.getIntKey());

                // distinct() compares by identity, so the defaults installed earlier are not added twice.
                target.put(entry.getIntKey(), Stream.concat(
                                existing != null ? Arrays.stream(existing) : Stream.empty(),
                                Arrays.stream(entry.getValue()))
                        .distinct()
                        .toArray(TradeOffers.Factory[]::new));
            }
        });
    }

    public static void stageDefaults(Map<VillagerProfession, ? extends Int2ObjectMap<TradeOffers.Factory[]>> defaults) {
        stagedDefaults = new HashMap<>(defaults);
    }

    public static void stageAdditions() {
        stagedAll = TradeOfferRegistryLoader.getRegistryForLoading();

        // NeoForge's trade events only touch the default table, so the rebalanced one is filled here.
        resetRebalanced();
        installRebalancedDefaults(stagedDefaults);
        mergeIntoRebalanced(stagedAll);
    }

    public static boolean applyStaged(VillagerProfession profession, Int2ObjectMap<List<TradeOffers.Factory>> levels) {
        Int2ObjectMap<TradeOffers.Factory[]> loaded = stagedAll.get(profession);
        boolean replacesDefaults = stagedDefaults.containsKey(profession);

        if (loaded == null && !replacesDefaults) {
            return false;
        }

        if (replacesDefaults) {
            for (List<TradeOffers.Factory> list : levels.values()) {
                list.clear();
            }
        }

        if (loaded != null) {
            for (Int2ObjectMap.Entry<TradeOffers.Factory[]> entry : loaded.int2ObjectEntrySet()) {
                List<TradeOffers.Factory> list = levels.get(entry.getIntKey());

                if (list == null) {
                    list = new ArrayList<>();
                    levels.put(entry.getIntKey(), list);
                }

                for (TradeOffers.Factory factory : entry.getValue()) {
                    if (!list.contains(factory)) {
                        list.add(factory);
                    }
                }
            }
        }

        return true;
    }

    public static void markTouched(VillagerProfession profession) {
        TOUCHED.add(profession);
    }

    /// The rebalanced table is immutable, but the level maps inside it are not.
    private static void resetRebalanced() {
        if (rebalancedVanilla == null) {
            rebalancedVanilla = new HashMap<>();
            TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.forEach((profession, levels) -> rebalancedVanilla.put(profession, copy(levels)));
            return;
        }

        TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.forEach((profession, levels) -> {
            Int2ObjectMap<TradeOffers.Factory[]> original =
                    rebalancedVanilla.computeIfAbsent(profession, ignored -> copy(levels));

            levels.clear();
            levels.putAll(copy(original));
        });
    }

    private static void installRebalancedDefaults(Map<VillagerProfession, ? extends Int2ObjectMap<TradeOffers.Factory[]>> defaults) {
        defaults.forEach((profession, levels) -> {
            Int2ObjectMap<TradeOffers.Factory[]> rebalanced = TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(profession);

            if (rebalanced != null) {
                rebalanced.clear();
                rebalanced.putAll(levels);
            }
        });
    }

    private static void mergeIntoRebalanced(Map<VillagerProfession, ? extends Int2ObjectMap<TradeOffers.Factory[]>> loaded) {
        loaded.forEach((profession, loadedTrades) -> {
            Int2ObjectMap<TradeOffers.Factory[]> target = TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(profession);

            if (target == null) {
                return;
            }

            for (Int2ObjectMap.Entry<TradeOffers.Factory[]> entry : loadedTrades.int2ObjectEntrySet()) {
                TradeOffers.Factory[] existing = target.get(entry.getIntKey());

                target.put(entry.getIntKey(), Stream.concat(
                                existing != null ? Arrays.stream(existing) : Stream.empty(),
                                Arrays.stream(entry.getValue()))
                        .distinct()
                        .toArray(TradeOffers.Factory[]::new));
            }
        });
    }

    private static Int2ObjectMap<TradeOffers.Factory[]> copy(Int2ObjectMap<TradeOffers.Factory[]> levels) {
        Int2ObjectMap<TradeOffers.Factory[]> copy = new Int2ObjectOpenHashMap<>();

        for (Int2ObjectMap.Entry<TradeOffers.Factory[]> entry : levels.int2ObjectEntrySet()) {
            copy.put(entry.getIntKey(), entry.getValue().clone());
        }

        return copy;
    }
}
