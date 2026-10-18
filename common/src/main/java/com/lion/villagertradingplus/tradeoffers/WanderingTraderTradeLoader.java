package com.lion.villagertradingplus.tradeoffers;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class WanderingTraderTradeLoader {

    /// JSON tier names mapped to the tiers of the pool: 1 is the common set, 2 the uncommon set.
    public static final Map<String, Integer> LEVEL_MAPPING = new HashMap<>();

    static {
        LEVEL_MAPPING.put("common", 1);
        LEVEL_MAPPING.put("rare", 2);
    }

    public record Snapshot(Map<Integer, List<TradeFactory>> tiers, boolean replaceVanilla) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), false);

        public boolean hasTrades() {
            return tiers.values().stream().anyMatch(list -> !list.isEmpty());
        }

        public List<TradeFactory> tier(int tier) {
            return tiers.getOrDefault(tier, List.of());
        }
    }

    private static final Map<Integer, List<TradeFactory>> collected = new HashMap<>();
    private static boolean replaceVanilla = false;
    private static Map<Identifier, JsonElement> stagedFiles = Map.of();
    private static volatile Snapshot active = Snapshot.EMPTY;

    private WanderingTraderTradeLoader() {}

    public static void stageFiles(Map<Identifier, JsonElement> files) {
        stagedFiles = new TreeMap<>(files);
    }

    public static void setReplace(boolean replace) {
        if (replace) {
            replaceVanilla = true;
        }
    }

    public static void add(int level, TradeFactory factory) {
        collected.computeIfAbsent(level, key -> new ArrayList<>()).add(factory);
    }

    public static void finish() {
        collected.clear();
        replaceVanilla = false;

        stagedFiles.forEach((identifier, json) -> {
            if (json.isJsonObject()) {
                VillagerTradingPlus.LOGGER.info("Deserializing wandering trader trades from: {}", identifier);
                TradeOfferManager.deserializeWanderingTraderJson(json.getAsJsonObject());
            }
        });

        Map<Integer, List<TradeFactory>> copy = new HashMap<>();
        collected.forEach((tier, factories) -> copy.put(tier, List.copyOf(factories)));
        active = new Snapshot(Map.copyOf(copy), replaceVanilla);
        collected.clear();
    }

    public static Snapshot snapshot() {
        return active;
    }
}
