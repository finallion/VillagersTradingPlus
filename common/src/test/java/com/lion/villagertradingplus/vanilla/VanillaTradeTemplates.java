package com.lion.villagertradingplus.vanilla;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.component.SuspiciousStewEffects;

public final class VanillaTradeTemplates {

    public static final String FOLDER = "common/src/main/resources/data/villagertradingplus/vanilla_trades";

    private static final String TYPE_PREFIX = "villagertradingplus:";
    private static final String[] TIERS = {"novice", "apprentice", "journeyman", "expert", "master"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final VanillaEnv env;
    private final Map<String, JsonElement> trades;
    private final Map<String, JsonElement> sets;
    private final Map<String, JsonElement> tags;
    private final boolean typedFormat;

    public VanillaTradeTemplates(VanillaEnv env) throws IOException {
        this.env = env;
        this.trades = env.json("villager_trade");
        this.sets = env.json("trade_set");
        this.tags = env.json("tags/villager_trade");
        this.typedFormat = trades.values()
                .stream()
                .anyMatch(trade -> trade.getAsJsonObject().has("given_item_modifier"));
    }

    public Map<String, String> generate() {
        Map<String, JsonObject> files = new TreeMap<>();
        Map<String, JsonObject> professions = new TreeMap<>();
        JsonObject wandering = new JsonObject();
        wandering.addProperty("replace", true);
        JsonObject wanderingTrades = new JsonObject();
        wandering.add("trades", wanderingTrades);

        for (String setName : new TreeMap<>(sets).keySet()) {
            String owner = setName.substring(0, setName.indexOf('/'));
            String pool = setName.substring(setName.indexOf('/') + 1);
            JsonArray converted = new JsonArray();

            for (String tradeId : resolveTag(sets.get(setName).getAsJsonObject().get("trades").getAsString())) {
                converted.add(convert(tradeId));
            }

            if (owner.equals("wandering_trader")) {
                wanderingTrades.add(pool.equals("uncommon") ? "rare" : pool, converted);
                continue;
            }

            JsonObject profession = professions.computeIfAbsent(owner, name -> professionFile(name));
            int level = Integer.parseInt(pool.substring("level_".length()));
            profession.getAsJsonObject("trades").add(TIERS[level - 1], converted);
        }

        JsonObject orderedWandering = new JsonObject();
        orderedWandering.add("replace", wandering.get("replace"));
        JsonObject orderedTrades = new JsonObject();
        for (String pool : new String[] {"buying", "common", "rare"}) {
            orderedTrades.add(pool, wanderingTrades.get(pool));
        }
        orderedWandering.add("trades", orderedTrades);

        professions.forEach((name, file) -> files.put(name + ".json", file));
        files.put("wandering_trader.json", orderedWandering);

        Map<String, String> text = new LinkedHashMap<>();
        files.forEach((name, file) -> text.put(name, GSON.toJson(file) + "\n"));
        return text;
    }

    private static JsonObject professionFile(String name) {
        JsonObject file = new JsonObject();
        file.addProperty("profession", "minecraft:" + name);
        file.add("trades", new JsonObject());
        return file;
    }

    public List<String> resolveTag(String reference) {
        List<String> ids = new ArrayList<>();
        String path = Identifier.parse(reference.substring(1)).getPath();
        JsonObject tag = tags.get(path).getAsJsonObject();

        for (JsonElement value : tag.getAsJsonArray("values")) {
            String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();

            if (entry.startsWith("#")) {
                ids.addAll(resolveTag(entry));
            } else {
                ids.add(Identifier.parse(entry).getPath());
            }
        }

        return ids;
    }

    public JsonObject convert(String tradeId) {
        JsonObject trade = trades.get(tradeId).getAsJsonObject();
        List<JsonObject> modifiers = modifiers(trade);
        List<String> functions = new ArrayList<>();

        for (JsonObject modifier : modifiers) {
            String name = function(modifier);

            if (!name.equals("minecraft:filtered") && !name.equals("minecraft:set_name")) {
                functions.add(name);
            }
        }

        if (functions.isEmpty()) {
            return plain(tradeId, trade, null);
        }

        if (functions.size() > 1) {
            throw unsupported(tradeId, functions.toString());
        }

        return switch (functions.get(0)) {
            case "minecraft:set_potion" -> plain(tradeId, trade, modifiers.get(0));
            case "minecraft:enchant_with_levels" -> enchantedTool(tradeId, trade, modifiers.get(0));
            case "minecraft:enchant_randomly" -> enchantedBook(tradeId, trade, modifiers.get(0));
            case "minecraft:exploration_map" -> map(tradeId, trade, modifiers);
            case "minecraft:set_random_dyes" -> dyedArmor(tradeId, trade, modifiers.get(0));
            case "minecraft:set_stew_effect" -> stew(tradeId, trade, modifiers.get(0));
            case "minecraft:set_random_potion" -> tippedArrow(tradeId, trade, modifiers.get(0));
            default -> throw unsupported(tradeId, functions.get(0));
        };
    }

    private JsonObject plain(String tradeId, JsonObject trade, JsonObject setPotion) {
        JsonObject wants = trade.getAsJsonObject("wants");
        JsonObject gives = trade.getAsJsonObject("gives");
        JsonObject result = new JsonObject();
        require(!gives.has("components"), tradeId, "components on gives");

        JsonObject sell = stack(gives, tradeId, true);

        if (setPotion != null) {
            sell.addProperty("potion", setPotion.get("id").getAsString());
        }

        if (trade.has("additional_wants")) {
            result.addProperty("type", TYPE_PREFIX + "multi_input");
            result.add("input_a", cost(wants));
            result.add("input_b", cost(trade.getAsJsonObject("additional_wants")));
            result.add("sell", sell);
        } else if (gives.get("id").getAsString().equals("minecraft:emerald")) {
            result.addProperty("type", TYPE_PREFIX + "buy_item");
            result.add("buy", cost(wants));
            result.add("reward", sell);
        } else {
            result.addProperty("type", TYPE_PREFIX + "sell_item");
            result.add("sell", sell);
            result.add("priceIn", cost(wants));
        }

        return finish(result, trade);
    }

    private JsonObject enchantedTool(String tradeId, JsonObject trade, JsonObject modifier) {
        JsonObject levels = modifier.getAsJsonObject("levels");
        require(levels.get("type").getAsString().equals("minecraft:uniform"), tradeId, "levels type");
        require((int) levels.get("min").getAsDouble() == 5 && (int) levels.get("max").getAsDouble() == 19, tradeId, "levels 5 to 19");
        require(modifier.get("options").getAsString().equals("#minecraft:on_traded_equipment"), tradeId, "options");
        require(modifier.get("include_additional_cost_component").getAsBoolean(), tradeId, "additional cost");
        require(!trade.has("additional_wants"), tradeId, "second cost");

        JsonObject result = new JsonObject();
        result.addProperty("type", TYPE_PREFIX + "sell_enchanted_tool");
        result.add("sell", stack(trade.getAsJsonObject("gives"), tradeId, true));
        result.add("basePriceIn", cost(trade.getAsJsonObject("wants")));
        return finish(result, trade);
    }

    private JsonObject enchantedBook(String tradeId, JsonObject trade, JsonObject modifier) {
        JsonObject wants = trade.getAsJsonObject("wants");
        require(modifier.get("options").getAsString().equals("#minecraft:tradeable"), tradeId, "options");
        require(!modifier.get("only_compatible").getAsBoolean(), tradeId, "only_compatible");
        require(modifier.get("include_additional_cost_component").getAsBoolean(), tradeId, "additional cost");
        require(wants.get("id").getAsString().equals("minecraft:emerald") && count(wants) == 0, tradeId, "emerald count 0");
        require(trade.getAsJsonObject("additional_wants").get("id").getAsString().equals("minecraft:book"), tradeId, "book");
        require(trade.get("double_trade_price_enchantments").getAsString().equals("#minecraft:double_trade_price"), tradeId, "double price tag");

        JsonObject currency = new JsonObject();
        currency.addProperty("item", "minecraft:emerald");

        JsonObject result = new JsonObject();
        result.addProperty("type", TYPE_PREFIX + "sell_enchanted_book");
        result.add("currency", currency);
        return finish(result, trade);
    }

    private JsonObject dyedArmor(String tradeId, JsonObject trade, JsonObject modifier) {
        require(!trade.has("additional_wants"), tradeId, "second cost");
        JsonObject number = modifier.getAsJsonObject("number_of_dyes");
        require(number.toString().replace(".0", "").equals(
                typedFormat
                        ? "{\"type\":\"minecraft:add\",\"inputs\":[1,{\"type\":\"minecraft:binomial\",\"n\":2,\"p\":0.75}]}"
                        : "{\"type\":\"minecraft:sum\",\"summands\":[1,{\"type\":\"minecraft:binomial\",\"n\":2,\"p\":0.75}]}"
        ), tradeId, "number_of_dyes " + number);

        JsonObject result = new JsonObject();
        result.addProperty("type", TYPE_PREFIX + "sell_dyed_armor");
        result.add("sell", stack(trade.getAsJsonObject("gives"), tradeId, true));
        result.add("priceIn", cost(trade.getAsJsonObject("wants")));
        return finish(result, trade);
    }

    private JsonObject stew(String tradeId, JsonObject trade, JsonObject modifier) {
        require(!trade.has("additional_wants"), tradeId, "second cost");
        JsonArray pool = new JsonArray();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, env.registries);

        for (JsonElement effectElement : modifier.getAsJsonArray("effects")) {
            JsonObject effect = effectElement.getAsJsonObject();
            Identifier effectId = Identifier.parse(effect.get("type").getAsString());
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(effectId).orElseThrow();
            int seconds = (int) effect.get("duration").getAsDouble();
            int ticks = holder.value().isInstantaneous() ? seconds : seconds * 20;
            SuspiciousStewEffects effects = new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(holder, ticks)));
            JsonElement encoded = SuspiciousStewEffects.CODEC.encodeStart(ops, effects).getOrThrow();

            JsonObject components = new JsonObject();
            components.add("minecraft:suspicious_stew_effects", encoded);
            JsonObject sell = stack(trade.getAsJsonObject("gives"), tradeId, true);
            sell.add("components", components);

            JsonObject single = new JsonObject();
            single.addProperty("type", TYPE_PREFIX + "sell_item");
            single.add("sell", sell);
            single.add("priceIn", cost(trade.getAsJsonObject("wants")));

            JsonObject entry = new JsonObject();
            entry.addProperty("weight", 1);
            entry.add("trade", finish(single, trade));
            pool.add(entry);
        }

        JsonObject result = new JsonObject();
        result.addProperty("type", TYPE_PREFIX + "weighted_pool");
        result.add("pool", pool);
        return result;
    }

    private JsonObject tippedArrow(String tradeId, JsonObject trade, JsonObject modifier) {
        require(modifier.get("options").getAsString().equals("#minecraft:tradeable"), tradeId, "options");
        JsonObject first = trade.getAsJsonObject("wants");
        JsonObject second = trade.getAsJsonObject("additional_wants");
        boolean emeraldFirst = first.get("id").getAsString().equals("minecraft:emerald");
        JsonObject emerald = emeraldFirst ? first : second;
        JsonObject arrows = emeraldFirst ? second : first;
        require(emerald.get("id").getAsString().equals("minecraft:emerald"), tradeId, "emerald cost");

        JsonObject result = new JsonObject();
        result.addProperty("type", TYPE_PREFIX + "sell_potion");
        result.add("convertible", cost(arrows));
        result.add("priceIn", cost(emerald));
        result.add("sell", stack(trade.getAsJsonObject("gives"), tradeId, true));
        result.addProperty("potions", "#minecraft:tradeable");
        return finish(result, trade);
    }

    private JsonObject map(String tradeId, JsonObject trade, List<JsonObject> modifiers) {
        JsonObject result = new JsonObject();
        result.addProperty("type", TYPE_PREFIX + "sell_map");
        result.add("buy", cost(trade.getAsJsonObject("additional_wants")));
        result.add("priceIn", cost(trade.getAsJsonObject("wants")));
        require(trade.getAsJsonObject("wants").get("id").getAsString().equals("minecraft:emerald"), tradeId, "emerald cost");

        if (typedFormat) {
            result.add("gives", trade.get("gives").deepCopy());
            result.add("given_item_modifier", trade.get("given_item_modifier").deepCopy());
            return finish(result, trade);
        }

        JsonObject exploration = null;
        JsonObject name = null;

        for (JsonObject modifier : modifiers) {
            if (function(modifier).equals("minecraft:exploration_map")) {
                exploration = modifier;
            } else if (function(modifier).equals("minecraft:set_name")) {
                name = modifier;
            }
        }

        require(exploration != null, tradeId, "exploration_map");
        require(exploration.get("search_radius").getAsInt() == 100, tradeId, "search_radius");
        require(!exploration.has("zoom") && !exploration.has("skip_existing_chunks"), tradeId, "zoom or skip_existing_chunks");

        String destination = exploration.get("destination").getAsString();
        result.addProperty("structure_id", destination.startsWith("#") ? destination.substring(1) : destination);
        result.addProperty(
                "decoration",
                exploration.has("decoration") ? exploration.get("decoration").getAsString() : "minecraft:mansion"
        );

        if (name != null) {
            require(name.get("target").getAsString().equals("item_name"), tradeId, "set_name target");
            result.addProperty("name", name.getAsJsonObject("name").get("translate").getAsString());
        }

        return finish(result, trade);
    }

    /// Writes the three stats explicitly, because the VillagerTradingPlus defaults differ from vanilla.
    private JsonObject finish(JsonObject result, JsonObject trade) {
        result.addProperty("max_uses", trade.has("max_uses") ? (int) trade.get("max_uses").getAsDouble() : 4);
        result.addProperty("villager_experience", trade.has("xp") ? (int) trade.get("xp").getAsDouble() : 1);
        result.addProperty(
                "price_multiplier",
                trade.has("reputation_discount") ? trade.get("reputation_discount").getAsDouble() : 0.0
        );

        if (trade.has("merchant_predicate")) {
            result.add("merchant_predicate", trade.get("merchant_predicate").deepCopy());
        }

        return result;
    }

    /// A cost, with the count clamped to the stack size like the vanilla `TradeCost` does.
    private JsonObject cost(JsonObject wants) {
        String id = wants.get("id").getAsString();
        JsonObject cost = new JsonObject();
        cost.addProperty("item", id);
        cost.addProperty("count", Math.max(1, Math.min(count(wants), maxStack(id))));

        if (wants.has("components")) {
            cost.add("components", wants.get("components").deepCopy());
        }

        return cost;
    }

    private JsonObject stack(JsonObject source, String context, boolean result) {
        String id = source.get("id").getAsString();
        JsonObject stack = new JsonObject();
        stack.addProperty("item", id);
        int count = count(source);
        require(count >= 1 && count <= maxStack(id), context, "stack count " + count + " of " + id);
        stack.addProperty("count", count);

        if (source.has("components")) {
            stack.add("components", source.get("components").deepCopy());
        }

        return stack;
    }

    private static int count(JsonObject source) {
        if (!source.has("count")) {
            return 1;
        }

        JsonElement count = source.get("count");
        require(count.isJsonPrimitive(), source.toString(), "count provider");
        return (int) count.getAsDouble();
    }

    private static int maxStack(String id) {
        return BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElseThrow().value().getDefaultMaxStackSize();
    }

    private List<JsonObject> modifiers(JsonObject trade) {
        List<JsonObject> list = new ArrayList<>();
        JsonElement raw = trade.has("given_item_modifier") ? trade.get("given_item_modifier") : trade.get("given_item_modifiers");

        if (raw == null) {
            return list;
        }

        if (raw.isJsonArray()) {
            raw.getAsJsonArray().forEach(element -> list.add(element.getAsJsonObject()));
        } else {
            list.add(raw.getAsJsonObject());
        }

        return list;
    }

    private static String function(JsonObject modifier) {
        return modifier.has("type") ? modifier.get("type").getAsString() : modifier.get("function").getAsString();
    }

    private static void require(boolean condition, String tradeId, String what) {
        if (!condition) {
            throw unsupported(tradeId, what);
        }
    }

    private static IllegalStateException unsupported(String tradeId, String what) {
        return new IllegalStateException("Vanilla trade " + tradeId + " has a shape the converter does not know: " + what);
    }
}
