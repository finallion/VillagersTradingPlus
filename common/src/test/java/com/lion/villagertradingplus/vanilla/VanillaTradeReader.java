package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
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
        return Registries.VILLAGER_PROFESSION.get(new Identifier("minecraft", name));
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

    public static TradeOffer create(TradeOffers.Factory factory, VillagerType type, long seed) {
        return factory.create(new StubVillager(type), Random.create(seed));
    }

    public static JsonObject toJson(TradeOffers.Factory factory) throws Exception {
        String name = factory.getClass().getSimpleName();
        JsonObject trade = new JsonObject();

        switch (name) {
            case "BuyForOneEmeraldFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                require(offer.getSecondBuyItem().isEmpty(), name + " has a second slot");
                trade.addProperty("type", "villagertradingplus:buy_item");
                trade.add("buy", item(offer.getOriginalFirstBuyItem()));
                trade.add("reward", item(offer.getSellItem()));
                stats(trade, offer);
            }
            case "SellItemFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                require(offer.getSecondBuyItem().isEmpty(), name + " has a second slot");
                require(plain(offer.getSellItem()), name + " result carries NBT " + offer.getSellItem().getNbt());
                trade.addProperty("type", "villagertradingplus:sell_item");
                trade.add("sell", item(offer.getSellItem()));
                trade.add("priceIn", item(offer.getOriginalFirstBuyItem()));
                stats(trade, offer);
            }
            case "ProcessItemFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                require(plain(offer.getSellItem()), name + " result carries NBT " + offer.getSellItem().getNbt());
                trade.addProperty("type", "villagertradingplus:process_item");
                trade.add("convertible", item(offer.getSecondBuyItem()));
                trade.add("priceIn", item(offer.getOriginalFirstBuyItem()));
                trade.add("sell", item(offer.getSellItem()));
                stats(trade, offer);
            }
            case "SellSuspiciousStewFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                ItemStack stew = offer.getSellItem();
                JsonObject sell = item(stew);
                sell.addProperty("nbt", stew.getNbt().toString());
                trade.addProperty("type", "villagertradingplus:sell_item");
                trade.add("sell", sell);
                trade.add("priceIn", item(offer.getOriginalFirstBuyItem()));
                stats(trade, offer);
            }
            case "EnchantBookFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                JsonObject currency = new JsonObject();
                currency.addProperty("item", id(offer.getOriginalFirstBuyItem()));
                trade.addProperty("type", "villagertradingplus:sell_enchanted_book");
                trade.add("currency", currency);
                stats(trade, offer);
            }
            case "SellEnchantedToolFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                JsonObject sell = new JsonObject();
                sell.addProperty("item", id(offer.getSellItem()));
                JsonObject base = new JsonObject();
                base.addProperty("item", id(offer.getOriginalFirstBuyItem()));
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

                if (!(offer.getSellItem().getItem() instanceof DyeableArmorItem)) {
                    /// Vanilla leaves the leather horse armor undyed, so it is a plain sale.
                    sell.addProperty("count", 1);
                    trade.addProperty("type", "villagertradingplus:sell_item");
                    trade.add("sell", sell);
                    trade.add("priceIn", item(offer.getOriginalFirstBuyItem()));
                    stats(trade, offer);
                    break;
                }

                trade.addProperty("type", "villagertradingplus:sell_dyed_armor");
                trade.add("sell", sell);
                trade.add("priceIn", item(offer.getOriginalFirstBuyItem()));
                stats(trade, offer);
            }
            case "SellPotionHoldingItemFactory" -> {
                TradeOffer offer = create(factory, VillagerType.PLAINS, 0);
                JsonObject sell = new JsonObject();
                sell.addProperty("item", id(offer.getSellItem()));
                sell.addProperty("count", offer.getSellItem().getCount());
                trade.addProperty("type", "villagertradingplus:sell_potion");
                trade.add("sell", sell);
                trade.add("convertible", item(offer.getSecondBuyItem()));
                trade.add("priceIn", item(offer.getOriginalFirstBuyItem()));
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
                trade.addProperty("type", "villagertradingplus:sell_map");
                trade.addProperty("structure_id", tag.id().toString());
                trade.addProperty("name", (String) field(factory, "nameKey"));
                trade.addProperty("decoration", ((Enum<?>) field(factory, "iconType")).name().toLowerCase(Locale.ROOT));
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
                    byType.put(Registries.VILLAGER_TYPE.getId(type).toString(), id(offer.getOriginalFirstBuyItem()));
                    any = offer;
                }

                JsonObject types = new JsonObject();
                byType.forEach(types::addProperty);
                trade.addProperty("type", "villagertradingplus:buy_item_by_villager_type");
                trade.addProperty("count", any.getOriginalFirstBuyItem().getCount());
                trade.add("buy_by_type", types);
                trade.add("reward", item(any.getSellItem()));
                stats(trade, any);
            }
            default -> throw new IllegalStateException("unmapped vanilla factory " + factory.getClass().getName());
        }

        return trade;
    }

    /// New damageable stacks carry Damage:0, which a parsed stack carries too.
    private static boolean plain(ItemStack stack) {
        return !stack.hasNbt() || stack.getNbt().getSize() == 1 && stack.getNbt().getInt("Damage") == 0 && stack.getNbt().contains("Damage");
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

    private static JsonObject item(ItemStack stack) {
        JsonObject object = new JsonObject();
        object.addProperty("item", id(stack));
        object.addProperty("count", stack.getCount());
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
