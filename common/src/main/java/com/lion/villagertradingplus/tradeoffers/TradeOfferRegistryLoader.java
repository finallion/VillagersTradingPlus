package com.lion.villagertradingplus.tradeoffers;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class TradeOfferRegistryLoader {
    private static final Map<ResourceKey<VillagerProfession>, Int2ObjectMap<List<TradeFactory>>> COLLECTED = new LinkedHashMap<>();
    private static Map<Identifier, JsonElement> defaultFiles = Map.of();
    private static Map<Identifier, JsonElement> additionFiles = Map.of();

    public static void stageDefaultFiles(Map<Identifier, JsonElement> files) {
        defaultFiles = new TreeMap<>(files);
    }

    public static void stageAdditionFiles(Map<Identifier, JsonElement> files) {
        additionFiles = new TreeMap<>(files);
    }

    public static void finish() {
        try {
            loadVillagerFiles();
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.error("Villager trades could not be built. Keeping the previous trades.", e);
        }
        try {
            WanderingTraderTradeLoader.finish();
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.error("Wandering trader trades could not be built. Keeping the previous trades.", e);
        }
    }

    private static void loadVillagerFiles() {
        COLLECTED.clear();

        defaultFiles.forEach((identifier, json) -> {
            if (json.isJsonObject()) {
                VillagerTradingPlus.LOGGER.debug("Deserializing default trades from: {}", identifier);
                TradeOfferManager.deserializeJson(identifier, json.getAsJsonObject());
            }
        });
        Map<ResourceKey<VillagerProfession>, Int2ObjectMap<List<TradeFactory>>> defaults = drain();

        additionFiles.forEach((identifier, json) -> {
            if (json.isJsonObject()) {
                VillagerTradingPlus.LOGGER.debug("Deserializing added trades from: {}", identifier);
                TradeOfferManager.deserializeJson(identifier, json.getAsJsonObject());
            }
        });
        TradePools.publish(new TradeSnapshot(defaults, drain()));
    }

    public static void registerVillagerTrade(
            ResourceKey<VillagerProfession> profession,
            int level,
            TradeFactory trade
    ) {
        COLLECTED.computeIfAbsent(profession, key -> new Int2ObjectOpenHashMap<>())
                .computeIfAbsent(level, key -> new ArrayList<>())
                .add(trade);
    }

    private static Map<ResourceKey<VillagerProfession>, Int2ObjectMap<List<TradeFactory>>> drain() {
        Map<ResourceKey<VillagerProfession>, Int2ObjectMap<List<TradeFactory>>> copy = new LinkedHashMap<>();
        COLLECTED.forEach((profession, levels) -> {
            Int2ObjectMap<List<TradeFactory>> levelCopy = new Int2ObjectOpenHashMap<>();
            levels.int2ObjectEntrySet().forEach(entry -> levelCopy.put(entry.getIntKey(), List.copyOf(entry.getValue())));
            copy.put(profession, levelCopy);
        });
        COLLECTED.clear();
        return Collections.unmodifiableMap(copy);
    }
}
