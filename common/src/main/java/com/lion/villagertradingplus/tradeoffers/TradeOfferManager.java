package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.google.gson.*;
import com.lion.villagertradingplus.tradeoffers.conditions.TradeConditions;
import com.lion.villagertradingplus.tradeoffers.trades.*;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class TradeOfferManager {
    public static final Map<String, Integer> professionMapping = new HashMap<>();
    public static final Map<Identifier, JsonTradeOffer> tradeOfferRegistry = new HashMap<>();

    static {
        professionMapping.put("novice", 1);
        professionMapping.put("apprentice", 2);
        professionMapping.put("journeyman", 3);
        professionMapping.put("expert", 4);
        professionMapping.put("master", 5);
    }

    public static void registerTradeOffers() {
        VillagerTradingPlus.LOGGER.info("Registered JSON trade offer adapter.");
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_item"), new JsonSellItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"buy_item"), new JsonBuyItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"process_item"), new JsonProcessItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_potion"), new JsonSellPotionTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_enchanted_tool"), new JsonSellEnchantedToolTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_specific_enchanted_tool"), new JsonSellSpecificEnchantedToolTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_enchanted_book"), new JsonSellEnchantedBookTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_specific_enchanted_book"), new JsonSellSpecificEnchantedBookTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_map"), new JsonSellStructureMapTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"weighted_pool"), new JsonWeightedPoolTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"buy_tagged_item"), new JsonBuyTaggedItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_tagged_item"), new JsonSellTaggedItemTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"sell_enchanted_book_from_list"), new JsonSellEnchantedBookFromListTradeOffer());
        tradeOfferRegistry.put(Identifier.of(VillagerTradingPlus.MOD_ID,"multi_input"), new JsonMultiInputTradeOffer());
    }

    /// Legacy namespace of the trade types. Datapack files that use it keep resolving.
    private static final String LEGACY_NAMESPACE = "villagersplus";

    /// Legacy ids already warned about, so each logs once.
    private static final Set<String> REPORTED_LEGACY_TYPES = ConcurrentHashMap.newKeySet();

    /// Accepts the canonical, bare and legacy spellings of a trade type.
    @Nullable
    private static JsonTradeOffer findAdapter(String type) {
        Identifier id = Identifier.tryParse(type);

        if (id == null) {
            return null;
        }

        JsonTradeOffer adapter = tradeOfferRegistry.get(id);

        if (adapter != null) {
            return adapter;
        }

        // A bare "sell_item" parses as minecraft:sell_item, which is never what the author meant.
        if (type.indexOf(':') < 0) {
            return tradeOfferRegistry.get(Identifier.of(VillagerTradingPlus.MOD_ID, id.getPath()));
        }

        if (LEGACY_NAMESPACE.equals(id.getNamespace())) {
            adapter = tradeOfferRegistry.get(Identifier.of(VillagerTradingPlus.MOD_ID, id.getPath()));

            if (adapter != null && REPORTED_LEGACY_TYPES.add(type)) {
                VillagerTradingPlus.LOGGER.warn(
                        "Trade type '{}' still uses the old namespace; it now lives at '{}:{}'. Still accepted, but please update the file.",
                        type, VillagerTradingPlus.MOD_ID, id.getPath());
            }

            return adapter;
        }

        return null;
    }

    private static Identifier sourceFile;

    public static void deserializeJson(Identifier source, JsonObject jsonRoot) {
        sourceFile = source;

        try {
            deserializeJson(jsonRoot);
        } finally {
            sourceFile = null;
        }
    }

    public static void deserializeWanderingTraderJson(Identifier source, JsonObject jsonRoot) {
        sourceFile = source;

        try {
            deserializeWanderingTraderJson(jsonRoot);
        } finally {
            sourceFile = null;
        }
    }

    private static String fileSuffix() {
        return sourceFile == null ? "" : " [file " + sourceFile + "]";
    }

    public static void deserializeJson(JsonObject jsonRoot) {
        // One unusable file costs that file, not the reload. The listener logs the file name before this call.
        try {
            Identifier professionId = Identifier.tryParse(
                    JsonFields.requireString(jsonRoot, "villager trade file", "profession")
            );

            Registries.VILLAGER_PROFESSION
                    .getOrEmpty(professionId)
                    .ifPresent(
                            villagerProfession -> deserializeTrades(
                                    jsonRoot,
                                    (integer, factory) -> TradeOfferRegistryLoader.registerVillagerTrade(
                                            villagerProfession,
                                            integer,
                                            factory
                                    )
                            )
                    );
        } catch (TradeParseException e) {
            VillagerTradingPlus.LOGGER.error("Skipping villager trade file{}: {}", fileSuffix(), e.getMessage());
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.error("Skipping malformed villager trade file{}.", fileSuffix(), e);
        }
    }

    /// Parses a wandering-trader file keyed by tier (common = 1, rare = 2). The replace key swaps out vanilla trades.
    public static void deserializeWanderingTraderJson(JsonObject jsonRoot) {
        try {
            // Inside the guard, because a non-boolean replace throws and would abort the datapack load.
            if (jsonRoot.has("replace")) {
                WanderingTraderTradeLoader.setReplace(jsonRoot.get("replace").getAsBoolean());
            }

            JsonObject trades = JsonFields.requireObject(jsonRoot, "wandering trader trade file", "trades");
            for (Map.Entry<String, JsonElement> entry : trades.entrySet()) {
                Integer level = WanderingTraderTradeLoader.LEVEL_MAPPING.get(entry.getKey());

                if (level == null) {
                    VillagerTradingPlus.LOGGER.error("Unknown wandering trader trade tier: " + entry.getKey() + " (use 'common' or 'rare').");
                    continue;
                }

                // A tier that is not an array is lost as a whole. An unusable entry costs only that entry.
                JsonArray tierTrades;
                try {
                    tierTrades = JsonFields.requireArray(trades, "tier '" + entry.getKey() + "'", entry.getKey());
                } catch (RuntimeException e) {
                    VillagerTradingPlus.LOGGER.error("Skipping wandering trader tier '{}': {}", entry.getKey(), e.getMessage());
                    continue;
                }

                for (JsonElement tradeElement : tierTrades) {
                    JsonObject trade;
                    try {
                        trade = JsonFields.asObject(tradeElement, "tier '" + entry.getKey() + "'");
                    } catch (RuntimeException e) {
                        VillagerTradingPlus.LOGGER.error("Dropped an entry from wandering trader tier '{}': {}", entry.getKey(), e.getMessage());
                        continue;
                    }

                    TradeOffers.Factory factory = deserializeTrade(trade);

                    if (factory == null) {
                        VillagerTradingPlus.LOGGER.error("Wandering trader trade type broken: " + trade);
                    } else {
                        WanderingTraderTradeLoader.add(level, factory);
                    }
                }
            }
        } catch (TradeParseException e) {
            VillagerTradingPlus.LOGGER.error("Skipping wandering trader trade file{}: {}", fileSuffix(), e.getMessage());
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.error("Skipping malformed wandering trader trade file{}.", fileSuffix(), e);
        }
    }

    private static void deserializeTrades(
            @NotNull JsonObject jsonRoot,
            BiConsumer<Integer, TradeOffers.Factory> tradeConsumer
    ) {
        JsonObject trades = JsonFields.requireObject(jsonRoot, "villager trade file", "trades");
        for (Map.Entry<String, JsonElement> entry : trades.entrySet()) {

            Integer level = professionMapping.get(entry.getKey());

            if (level == null) {
                VillagerTradingPlus.LOGGER.error(
                        "Unknown villager trade tier: "
                                + entry.getKey()
                                + " (use novice, apprentice, journeyman, expert or master)."
                );
                continue;
            }

            // A tier that is not an array is lost as a whole. An unusable entry costs only that entry.
            JsonArray tierTrades;
            try {
                tierTrades = JsonFields.requireArray(trades, "tier '" + entry.getKey() + "'", entry.getKey());
            } catch (RuntimeException e) {
                VillagerTradingPlus.LOGGER.error("Skipping tier '{}': {}", entry.getKey(), e.getMessage());
                continue;
            }

            for (JsonElement tradeElement : tierTrades) {
                JsonObject trade;
                try {
                    trade = JsonFields.asObject(tradeElement, "tier '" + entry.getKey() + "'");
                } catch (RuntimeException e) {
                    VillagerTradingPlus.LOGGER.error("Dropped an entry from tier '{}': {}", entry.getKey(), e.getMessage());
                    continue;
                }

                TradeOffers.Factory factory = deserializeTrade(trade);

                // deserializeTrade already logged why; just record which tier lost a trade.
                if (factory == null) {
                    VillagerTradingPlus.LOGGER.warn("Dropped a broken trade from tier '{}': {}", entry.getKey(), trade);
                } else {
                    tradeConsumer.accept(level, factory);
                }
            }
        }
    }

    /// Every trade passes here. It layers conditions and global pricing on the adapter. Returns null for unknown types.
    @Nullable
    public static TradeOffers.Factory deserializeTrade(JsonObject trade) {
        String type = readString(trade, "type");
        JsonTradeOffer adapter = findAdapter(type);

        if (adapter == null) {
            VillagerTradingPlus.LOGGER.warn("Skipping trade with unknown type '{}'{}.", type, fileSuffix());
            return null;
        }

        try {
            TradeOffers.Factory factory = adapter.deserialize(trade);

            if (VillagerTradingPlus.CONFIG.enable_conditional_trades && trade.has("conditions")) {
                boolean orLogic = "or".equalsIgnoreCase(readString(trade, "logic"));
                factory = new ConditionalTradeFactory(
                        factory,
                        TradeConditions.parse(trade.getAsJsonArray("conditions"), orLogic)
                );
            }

            return PricingTradeFactory.wrapIfNeeded(factory, trade);
        } catch (TradeParseException e) {
            // Skip only this trade: one bad item id must not take the rest of the datapack with it.
            VillagerTradingPlus.LOGGER.warn("Skipping '{}' trade{}: {}", type, fileSuffix(), e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // Backstop: a malformed file costs one trade, not the world. The stack trace shows an unguarded read.
            VillagerTradingPlus.LOGGER.error("Skipping malformed '{}' trade{}: {}", type, fileSuffix(), trade, e);
            return null;
        }
    }

    private static String readString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }
}
