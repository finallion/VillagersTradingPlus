package com.lion.villagertradingplus.tradeoffers;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.google.gson.*;
import com.lion.villagertradingplus.tradeoffers.conditions.TradeConditions;
import com.lion.villagertradingplus.tradeoffers.trades.*;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

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
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_item"), new JsonSellItemTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"buy_item"), new JsonBuyItemTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"process_item"), new JsonProcessItemTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_potion"), new JsonSellPotionTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_enchanted_tool"), new JsonSellEnchantedToolTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_specific_enchanted_tool"), new JsonSellSpecificEnchantedToolTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_enchanted_book"), new JsonSellEnchantedBookTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_specific_enchanted_book"), new JsonSellSpecificEnchantedBookTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_map"), new JsonSellStructureMapTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"weighted_pool"), new JsonWeightedPoolTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"buy_tagged_item"), new JsonBuyTaggedItemTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_tagged_item"), new JsonSellTaggedItemTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"sell_enchanted_book_from_list"), new JsonSellEnchantedBookFromListTradeOffer());
        tradeOfferRegistry.put(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID,"multi_input"), new JsonMultiInputTradeOffer());
    }

    /// Alias namespace that type ids accept.
    private static final String LEGACY_NAMESPACE = "villagersplus";

    /// Alias type ids that already logged a warning, so a big datapack logs each one once and not per trade.
    private static final Set<String> REPORTED_LEGACY_TYPES = ConcurrentHashMap.newKeySet();

    /// Accepts `villagertradingplus:x`, a bare `x` and the alias `villagersplus:x`.
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

        // A bare id resolves to the minecraft namespace, which is not the intended type.
        if (type.indexOf(':') < 0) {
            return tradeOfferRegistry.get(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, id.getPath()));
        }

        if (LEGACY_NAMESPACE.equals(id.getNamespace())) {
            adapter = tradeOfferRegistry.get(Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, id.getPath()));
            if (adapter != null && REPORTED_LEGACY_TYPES.add(type)) {
                VillagerTradingPlus.LOGGER.warn(
                        "Trade type '{}' still uses the old namespace; it now lives at '{}:{}'. Still accepted, but please update the file.",
                        type, VillagerTradingPlus.MOD_ID, id.getPath());
            }
            return adapter;
        }

        return null;
    }

    public static void deserializeJson(JsonObject jsonRoot) {
        // One unusable file costs that file, not the reload.
        try {
            Identifier professionId = Identifier.tryParse(
                    JsonFields.requireString(jsonRoot, "villager trade file", "profession"));

            // An unknown profession id would register trades that no villager ever offers.
            if (professionId == null || BuiltInRegistries.VILLAGER_PROFESSION.getOptional(professionId).isEmpty()) {
                VillagerTradingPlus.LOGGER.error("Unknown villager profession in trade file: {}", professionId);
                return;
            }

            ResourceKey<VillagerProfession> profession =
                    ResourceKey.create(Registries.VILLAGER_PROFESSION, professionId);
            deserializeTrades(jsonRoot, (integer, factory)
                    -> TradeOfferRegistryLoader.registerVillagerTrade(profession, integer, factory));
        } catch (TradeParseException e) {
            VillagerTradingPlus.LOGGER.error("Skipping villager trade file: {}", e.getMessage());
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.error("Skipping malformed villager trade file.", e);
        }
    }

    public static void deserializeWanderingTraderJson(JsonObject jsonRoot) {
        try {
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

                // A tier that is not an array is dropped whole. A bad entry drops only itself.
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

                    TradeFactory factory = deserializeTrade(trade);
                    if (factory == null) {
                        VillagerTradingPlus.LOGGER.error("Wandering trader trade type broken: " + trade);
                    } else {
                        WanderingTraderTradeLoader.add(level, factory);
                    }
                }
            }
        } catch (TradeParseException e) {
            VillagerTradingPlus.LOGGER.error("Skipping wandering trader trade file: {}", e.getMessage());
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.error("Skipping malformed wandering trader trade file.", e);
        }
    }

    private static void deserializeTrades(@NotNull JsonObject jsonRoot, BiConsumer<Integer, TradeFactory> tradeConsumer) {
        JsonObject trades = JsonFields.requireObject(jsonRoot, "villager trade file", "trades");
        for (Map.Entry<String, JsonElement> entry : trades.entrySet()) {

            Integer level = professionMapping.get(entry.getKey());
            if (level == null) {
                VillagerTradingPlus.LOGGER.error("Unknown villager trade tier: " + entry.getKey()
                        + " (use novice, apprentice, journeyman, expert or master).");
                continue;
            }

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

                TradeFactory factory = deserializeTrade(trade);

                // deserializeTrade already logged why; just record which tier lost a trade.
                if (factory == null) {
                    VillagerTradingPlus.LOGGER.warn("Dropped a broken trade from tier '{}': {}", entry.getKey(), trade);
                } else {
                    tradeConsumer.accept(level, factory);
                }
            }
        }
    }

    /// Every trade passes here, nested trades included.
    @Nullable
    public static TradeFactory deserializeTrade(JsonObject trade) {
        String type = readString(trade, "type");
        JsonTradeOffer adapter = findAdapter(type);
        if (adapter == null) {
            VillagerTradingPlus.LOGGER.warn("Skipping trade with unknown type '{}'.", type);
            return null;
        }

        try {
            // Wrap innermost, so `weighted_pool` still finds the PricingTradeFactory to strip.
            TradeFactory factory = RegistryRebindFactory.wrap(adapter.deserialize(trade));

            if (VillagerTradingPlus.CONFIG.enable_conditional_trades && trade.has("conditions")) {
                boolean orLogic = "or".equalsIgnoreCase(readString(trade, "logic"));
                factory = new ConditionalTradeFactory(factory,
                        TradeConditions.parse(trade.getAsJsonArray("conditions"), orLogic));
            }

            return PricingTradeFactory.wrapIfNeeded(factory, trade);
        } catch (TradeParseException e) {
            // Skip this trade only. One bad item id drops no other trade.
            VillagerTradingPlus.LOGGER.warn("Skipping '{}' trade: {}", type, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // Backstop for unchecked reads. A stack trace marks a defect.
            VillagerTradingPlus.LOGGER.error("Skipping malformed '{}' trade: {}", type, trade, e);
            return null;
        }
    }

    private static String readString(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }
}
