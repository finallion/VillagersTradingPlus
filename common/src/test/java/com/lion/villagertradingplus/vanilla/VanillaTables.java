package com.lion.villagertradingplus.vanilla;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// A copy of the vanilla trade tables taken before any code of this mod can change them.
final class VanillaTables {

    static final String[] TIERS = {"novice", "apprentice", "journeyman", "expert", "master"};
    static final String[] WANDERING_POOLS = {"buying", "rare", "common"};

    private static Map<String, Map<Integer, TradeOffers.Factory[]>> professions;
    private static List<Pair<TradeOffers.Factory[], Integer>> wandering;
    private static Map<String, Map<Integer, TradeOffers.Factory[]>> rebalanced;

    private VanillaTables() {}

    static synchronized Map<String, Map<Integer, TradeOffers.Factory[]>> professions() {
        if (professions == null) {
            professions = copy(TradeOffers.PROFESSION_TO_LEVELED_TRADE);
            rebalanced = copy(TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE);
            wandering = new ArrayList<>();

            for (Pair<TradeOffers.Factory[], Integer> pair : TradeOffers.WANDERING_TRADER_TRADES) {
                wandering.add(Pair.of(pair.getLeft().clone(), pair.getRight()));
            }
        }

        return professions;
    }

    static synchronized Map<String, Map<Integer, TradeOffers.Factory[]>> rebalanced() {
        professions();
        return rebalanced;
    }

    static synchronized List<Pair<TradeOffers.Factory[], Integer>> wandering() {
        professions();
        return wandering;
    }

    private static Map<String, Map<Integer, TradeOffers.Factory[]>> copy(
            Map<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>> source
    ) {
        Map<String, Map<Integer, TradeOffers.Factory[]>> out = new TreeMap<>();

        for (Map.Entry<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>> entry : source.entrySet()) {
            Map<Integer, TradeOffers.Factory[]> levels = new LinkedHashMap<>();

            for (int level = 1; level <= 5; level++) {
                TradeOffers.Factory[] pool = entry.getValue().get(level);

                if (pool != null) {
                    levels.put(level, pool.clone());
                }
            }

            out.put(entry.getKey().getValue().getPath(), levels);
        }

        return out;
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(target.getClass().getSimpleName() + "." + name, e);
        }
    }

    static String kind(TradeOffers.Factory factory) {
        return factory.getClass().getSimpleName();
    }

    static int count(Map<Integer, TradeOffers.Factory[]> levels) {
        return levels.values().stream().mapToInt(pool -> pool.length).sum();
    }

    static List<TradeOffers.Factory> flatten(Map<Integer, TradeOffers.Factory[]> levels) {
        List<TradeOffers.Factory> out = new ArrayList<>();
        levels.values().forEach(pool -> out.addAll(Arrays.asList(pool)));
        return out;
    }
}
