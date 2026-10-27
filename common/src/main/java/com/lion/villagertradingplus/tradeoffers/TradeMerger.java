package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

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

    private static Map<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>> vanilla;
    private static Map<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>> rebalancedVanilla;

    private static final Set<RegistryKey<VillagerProfession>> TOUCHED = new HashSet<>();

    private static volatile Map<RegistryKey<VillagerProfession>, ? extends Int2ObjectMap<TradeOffers.Factory[]>> stagedDefaults = Map.of();
    private static volatile Map<RegistryKey<VillagerProfession>, ? extends Int2ObjectMap<TradeOffers.Factory[]>> stagedAll = Map.of();

    private TradeMerger() {}

    public static Int2ObjectMap<TradeOffers.Factory[]> tradesFor(
            World world,
            RegistryKey<VillagerProfession> profession
    ) {
        if (world.getEnabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
            Int2ObjectMap<TradeOffers.Factory[]> rebalanced =
                    TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(profession);

            if (rebalanced != null) {
                return rebalanced;
            }
        }

        return TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(profession);
    }

    public static void resetToVanilla() {
        restoreRebalanced();

        if (vanilla == null) {
            vanilla = new HashMap<>();
            TradeOffers.PROFESSION_TO_LEVELED_TRADE.forEach((profession, levels) -> vanilla.put(profession, copy(levels)));
            return;
        }

        for (RegistryKey<VillagerProfession> profession : TOUCHED) {
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
            Map<RegistryKey<VillagerProfession>, ? extends Int2ObjectMap<TradeOffers.Factory[]>> defaults
    ) {
        defaults.forEach((profession, levels) -> {
            TOUCHED.add(profession);
            TradeOffers.PROFESSION_TO_LEVELED_TRADE.put(profession, levels);

            Int2ObjectMap<TradeOffers.Factory[]> rebalanced =
                    TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(profession);

            if (rebalanced != null) {
                rebalanced.clear();
                rebalanced.putAll(levels);
            }
        });
    }

    public static void installStagedDefaults() {
        installDefaults(stagedDefaults);
    }

    public static void mergeIntoVanilla() {
        TradeOfferRegistryLoader.getRegistryForLoading().forEach((profession, loadedTrades) -> {
            TOUCHED.add(profession);
            mergeInto(
                    TradeOffers.PROFESSION_TO_LEVELED_TRADE
                            .computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>()),
                    loadedTrades
            );

            Int2ObjectMap<TradeOffers.Factory[]> rebalanced =
                    TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(profession);

            if (rebalanced != null) {
                mergeInto(rebalanced, loadedTrades);
            }
        });
    }

    private static void mergeInto(
            Int2ObjectMap<TradeOffers.Factory[]> target,
            Int2ObjectMap<TradeOffers.Factory[]> loadedTrades
    ) {
        for (Int2ObjectMap.Entry<TradeOffers.Factory[]> entry : loadedTrades.int2ObjectEntrySet()) {
            TradeOffers.Factory[] existing = target.get(entry.getIntKey());

            // distinct() compares by identity, so the defaults installed earlier are not added twice.
            target.put(entry.getIntKey(), Stream.concat(
                            existing != null ? Arrays.stream(existing) : Stream.empty(),
                            Arrays.stream(entry.getValue()))
                    .distinct()
                    .toArray(TradeOffers.Factory[]::new));
        }
    }

    public static void stageDefaults(
            Map<RegistryKey<VillagerProfession>, ? extends Int2ObjectMap<TradeOffers.Factory[]>> defaults
    ) {
        stagedDefaults = new HashMap<>(defaults);
    }

    public static void stageAdditions() {
        stagedAll = TradeOfferRegistryLoader.getRegistryForLoading();
    }

    public static boolean applyStaged(
            RegistryKey<VillagerProfession> profession,
            Int2ObjectMap<List<TradeOffers.Factory>> levels
    ) {
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

    /// NeoForge posts its events for the default table only, so the experiment table is rebuilt next to it.
    public static void applyStagedRebalanced(RegistryKey<VillagerProfession> profession) {
        Int2ObjectMap<TradeOffers.Factory[]> live =
                TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(profession);

        if (live == null) {
            return;
        }

        Int2ObjectMap<TradeOffers.Factory[]> original =
                rebalancedSnapshot().computeIfAbsent(profession, ignored -> copy(live));

        Int2ObjectMap<List<TradeOffers.Factory>> levels = new Int2ObjectOpenHashMap<>();

        for (Int2ObjectMap.Entry<TradeOffers.Factory[]> entry : original.int2ObjectEntrySet()) {
            levels.put(entry.getIntKey(), new ArrayList<>(Arrays.asList(entry.getValue())));
        }

        live.clear();

        if (!applyStaged(profession, levels)) {
            live.putAll(copy(original));
            return;
        }

        for (Int2ObjectMap.Entry<List<TradeOffers.Factory>> entry : levels.int2ObjectEntrySet()) {
            live.put(entry.getIntKey(), entry.getValue().toArray(new TradeOffers.Factory[0]));
        }
    }

    public static void markTouched(RegistryKey<VillagerProfession> profession) {
        TOUCHED.add(profession);
    }

    private static Map<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>> rebalancedSnapshot() {
        if (rebalancedVanilla == null) {
            rebalancedVanilla = new HashMap<>();
            TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.forEach(
                    (profession, levels) -> rebalancedVanilla.put(profession, copy(levels))
            );
        }

        return rebalancedVanilla;
    }

    private static void restoreRebalanced() {
        Map<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>> snapshot = rebalancedSnapshot();

        TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.forEach((profession, live) -> {
            Int2ObjectMap<TradeOffers.Factory[]> original =
                    snapshot.computeIfAbsent(profession, ignored -> copy(live));

            live.clear();
            live.putAll(copy(original));
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
