package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

public final class VanillaTradeReader {

    public static final List<String> PROFESSIONS = List.of(
            "farmer",
            "fisherman",
            "shepherd",
            "fletcher",
            "librarian",
            "cartographer",
            "cleric",
            "armorer",
            "weaponsmith",
            "toolsmith",
            "butcher",
            "leatherworker",
            "mason"
    );

    public static final String[] TIERS = {"novice", "apprentice", "journeyman", "expert", "master"};

    /// The map factory builds its offer from the world, so the multiplier comes from the bytecode (0.2f).
    public static final float MAP_MULTIPLIER = 0.2F;

    private VanillaTradeReader() {}

    public static VillagerProfession profession(String name) {
        return Registries.VILLAGER_PROFESSION.get(Identifier.of("minecraft", name));
    }

    public static TradeOffers.Factory[] pool(String profession, int level) {
        Int2ObjectMap<TradeOffers.Factory[]> levels = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(profession(profession));
        return levels.get(level);
    }

    public static TradeOffers.Factory[] wandering(int level) {
        return TradeOffers.WANDERING_TRADER_TRADES.get(level);
    }

    public static JsonObject professionFile(String name) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("profession", "minecraft:" + name);
        JsonObject trades = new JsonObject();

        for (int level = 1; level <= 5; level++) {
            JsonArray tier = new JsonArray();

            for (TradeOffers.Factory factory : pool(name, level)) {
                tier.add(toJson(factory));
            }

            trades.add(TIERS[level - 1], tier);
        }

        root.add("trades", trades);
        return root;
    }

    public static JsonObject wanderingFile() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("replace", true);
        JsonObject trades = new JsonObject();

        for (int level = 1; level <= 2; level++) {
            JsonArray tier = new JsonArray();

            for (TradeOffers.Factory factory : wandering(level)) {
                tier.add(toJson(factory));
            }

            trades.add(level == 1 ? "common" : "rare", tier);
        }

        root.add("trades", trades);
        return root;
    }

    public static TradeOffer create(TradeOffers.Factory factory, VillagerType type, long seed) throws Exception {
        return factory.create(new StubVillager(type, TestWorld.get(false).world), Random.create(seed));
    }

    public static JsonObject toJson(TradeOffers.Factory factory) throws Exception {
        String name = factory.getClass().getSimpleName();
        JsonObject trade = new JsonObject();

        switch (name) {
            case "BuyItemFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                require(offer.getSecondBuyItem().isEmpty(), name + " has a second slot");
                require(offer.getFirstBuyItem().components().equals(net.minecraft.predicate.ComponentPredicate.EMPTY), name + " has a component predicate");
                trade.addProperty("type", "villagertradingplus:buy_item");
                trade.add("buy", item(offer.getFirstBuyItem()));
                trade.add("reward", item(offer.getSellItem()));
                stats(trade, offer);
            }
            case "SellItemFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                require(offer.getSecondBuyItem().isEmpty(), name + " has a second slot");
                require(plain(offer.getSellItem()), name + " result carries components");
                trade.addProperty("type", "villagertradingplus:sell_item");
                trade.add("sell", item(offer.getSellItem()));
                trade.add("priceIn", item(offer.getFirstBuyItem()));
                stats(trade, offer);
            }
            case "ProcessItemFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                require(plain(offer.getSellItem()), name + " result carries components");
                trade.addProperty("type", "villagertradingplus:process_item");
                trade.add("convertible", item(offer.getSecondBuyItem().get()));
                trade.add("priceIn", item(offer.getFirstBuyItem()));
                trade.add("sell", item(offer.getSellItem()));
                stats(trade, offer);
            }
            case "SellSuspiciousStewFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                ItemStack stew = offer.getSellItem();
                JsonObject sell = item(stew);
                sell.add("components", componentsJson(stew.getComponentChanges()));
                trade.addProperty("type", "villagertradingplus:sell_item");
                trade.add("sell", sell);
                trade.add("priceIn", item(offer.getFirstBuyItem()));
                stats(trade, offer);
            }
            case "EnchantBookFactory" -> {
                require(EnchantmentTags.TRADEABLE.equals(field(factory, "possibleEnchantments")), name + " uses another enchantment tag");
                require((int) field(factory, "minLevel") == 0, name + " clamps the minimum level");
                require((int) field(factory, "maxLevel") == Integer.MAX_VALUE, name + " clamps the maximum level");
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                JsonObject currency = new JsonObject();
                currency.addProperty("item", id(offer.getFirstBuyItem()));
                trade.addProperty("type", "villagertradingplus:sell_enchanted_book");
                trade.add("currency", currency);
                stats(trade, offer);
            }
            case "SellEnchantedToolFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                JsonObject sell = new JsonObject();
                sell.addProperty("item", id(offer.getSellItem()));
                JsonObject base = new JsonObject();
                base.addProperty("item", id(offer.getFirstBuyItem()));
                base.addProperty("count", (int) field(factory, "basePrice"));
                trade.addProperty("type", "villagertradingplus:sell_enchanted_tool");
                trade.add("sell", sell);
                trade.add("basePriceIn", base);
                stats(trade, offer);
            }
            case "SellDyedArmorFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                JsonObject sell = new JsonObject();
                sell.addProperty("item", id(offer.getSellItem()));
                trade.addProperty("type", "villagertradingplus:sell_dyed_armor");
                trade.add("sell", sell);
                trade.add("priceIn", item(offer.getFirstBuyItem()));
                stats(trade, offer);
            }
            case "SellPotionHoldingItemFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                JsonObject sell = new JsonObject();
                sell.addProperty("item", id(offer.getSellItem()));
                sell.addProperty("count", offer.getSellItem().getCount());
                trade.addProperty("type", "villagertradingplus:sell_potion");
                trade.add("sell", sell);
                trade.add("convertible", item(offer.getSecondBuyItem().get()));
                trade.add("priceIn", item(offer.getFirstBuyItem()));
                stats(trade, offer);
            }
            case "SellMapFactory" -> {
                JsonObject compass = new JsonObject();
                compass.addProperty("item", "minecraft:compass");
                compass.addProperty("count", 1);
                JsonObject price = new JsonObject();
                price.addProperty("item", "minecraft:emerald");
                price.addProperty("count", (int) field(factory, "price"));
                TagKey<?> tag = (TagKey<?>) field(factory, "structure");
                RegistryEntry<?> decoration = (RegistryEntry<?>) field(factory, "decoration");
                trade.addProperty("type", "villagertradingplus:sell_map");
                trade.addProperty("structure_id", tag.id().toString());
                trade.addProperty("name", (String) field(factory, "nameKey"));
                trade.addProperty("decoration", decoration.getKey().get().getValue().getPath());
                trade.add("buy", compass);
                trade.add("priceIn", price);
                trade.addProperty("max_uses", (int) field(factory, "maxUses"));
                trade.addProperty("villager_experience", (int) field(factory, "experience"));
                trade.add("price_multiplier", new JsonPrimitive(MAP_MULTIPLIER));
            }
            case "TypeAwareBuyForOneEmeraldFactory" -> {
                Map<String, String> byType = new LinkedHashMap<>();
                TradeOffer any = null;

                for (VillagerType type : Registries.VILLAGER_TYPE) {
                    TradeOffer offer = create(factory, type, 0);
                    byType.put(Registries.VILLAGER_TYPE.getId(type).toString(), id(offer.getFirstBuyItem()));
                    any = offer;
                }

                JsonObject types = new JsonObject();
                byType.forEach(types::addProperty);
                trade.addProperty("type", "villagertradingplus:buy_item_by_villager_type");
                trade.addProperty("count", any.getFirstBuyItem().count());
                trade.add("buy_by_type", types);
                trade.add("reward", item(any.getSellItem()));
                stats(trade, any);
            }
            default -> throw new IllegalStateException("unmapped vanilla factory " + factory.getClass().getName());
        }

        return trade;
    }

    private static JsonElement componentsJson(ComponentChanges changes) throws Exception {
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, TestWorld.get(false).registries);
        return ComponentChanges.CODEC.encodeStart(ops, changes).getOrThrow();
    }

    private static boolean plain(ItemStack stack) {
        return stack.getComponentChanges().isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static String id(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String id(TradedItem traded) {
        return Registries.ITEM.getId(traded.item().value()).toString();
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject object = new JsonObject();
        object.addProperty("item", id(stack));
        object.addProperty("count", stack.getCount());
        return object;
    }

    private static JsonObject item(TradedItem traded) {
        JsonObject object = new JsonObject();
        object.addProperty("item", id(traded));
        object.addProperty("count", traded.count());
        return object;
    }

    private static void stats(JsonObject trade, TradeOffer offer) {
        trade.addProperty("max_uses", offer.getMaxUses());
        trade.addProperty("villager_experience", offer.getMerchantExperience());
        trade.add("price_multiplier", new JsonPrimitive(offer.getPriceMultiplier()));
    }

    public static List<TradeOffers.Factory> all() {
        List<TradeOffers.Factory> list = new ArrayList<>();

        for (String profession : PROFESSIONS) {
            for (int level = 1; level <= 5; level++) {
                list.addAll(List.of(pool(profession, level)));
            }
        }

        for (int level = 1; level <= 2; level++) {
            list.addAll(List.of(wandering(level)));
        }

        return list;
    }
}
