package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory;
import com.lion.villagertradingplus.tradeoffers.RegistryRebindFactory;
import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import com.lion.villagertradingplus.tradeoffers.TradePricing;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class FeatureChecks {

    private static final String TIPPED_ARROW = "{\"type\":\"villagertradingplus:sell_potion\","
            + "\"convertible\":{\"item\":\"minecraft:arrow\",\"count\":5},"
            + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":2},"
            + "\"sell\":{\"item\":\"minecraft:tipped_arrow\",\"count\":5}}";

    private final List<String[]> results = new ArrayList<>();
    private final VanillaRuntime runtime = VanillaRuntime.get();
    private final Entity plains = runtime.villager(VillagerType.PLAINS);
    private final Entity desert = runtime.villager(VillagerType.DESERT);

    private FeatureChecks() {
        TradeOfferManager.registerTradeOffers();
        VanillaTables.professions();
    }

    public static List<String[]> run() {
        FeatureChecks checks = new FeatureChecks();
        checks.check("process_item puts the price in the first slot and the item in the second", checks::processSlots);
        checks.check("the cost scale acts on the price of a process_item trade", checks::processPricing);
        checks.check("sell_potion sells tipped arrows for emeralds and arrows", checks::tippedArrow);
        checks.check("sell_potion still asks for a water bottle when the item is a potion", checks::waterBottle);
        checks.check("sell_dyed_armor leaves an item that cannot be dyed alone", checks::dyedArmorOnlyDyeable);
        checks.check("buy_item_by_villager_type offers nothing for a villager type without an entry", checks::villagerType);
        checks.check("buy_item_by_villager_type rejects an unknown villager type", checks::villagerTypeUnknown);
        checks.check("sell_map reads its marker and falls back to the red X", checks::mapDecoration);
        checks.check("sell_map names the map with the item name", checks::mapName);
        checks.check("a cost may be larger than a stack and a result may not", checks::costCounts);
        checks.check("the oversized cost survives the cost scale", checks::oversizedCostPricing);
        checks.check("the second slot of a trade stays within the stack size", checks::secondSlotCosts);
        checks.check("buy_item_by_villager_type skips only the unknown villager type", checks::villagerTypeSkipsUnknown);
        checks.check("the experiment table of a late profession does not grow on reload", checks::rebalanceMissingProfession);
        checks.check("sell_enchanted_tool draws from the tag of vanilla's traded equipment", checks::toolTag);
        checks.check("the wandering trader reads a buying tier", checks::wanderingBuying);
        checks.check("a wandering trader file without a buying tier keeps the vanilla buying offers", checks::wanderingBuyingKept);
        checks.check("a wandering trader replace file swaps the buying offers only when it has some", checks::wanderingBuyingReplace);
        checks.check("the staged wandering trader buying offers reach the NeoForge list", checks::wanderingStaged);
        checks.check("replacing and adding trades changes the experiment table too", checks::rebalanceMerge);
        checks.check("a world with the experiment draws from the experiment table", checks::rebalanceSelection);
        return checks.results;
    }

    private interface Body {
        String run() throws Exception;
    }

    private void check(String name, Body body) {
        String failure;

        try {
            failure = body.run();
        } catch (Throwable e) {
            failure = "threw " + e;
        }

        results.add(new String[] {name, failure == null ? "" : failure});
    }

    private TradeOffers.Factory parse(String json) {
        TradeOffers.Factory factory = TradeOfferManager.deserializeTrade(JsonParser.parseString(json).getAsJsonObject());

        if (factory == null) {
            throw new IllegalStateException("the trade did not parse: " + json);
        }

        return factory;
    }

    private TradeOffer offer(TradeOffers.Factory factory, Entity merchant) {
        return TradeApi.create(factory, merchant, Random.create(7L));
    }

    private static boolean is(net.minecraft.village.TradedItem traded, Item item, int count) {
        return traded.item().value() == item && traded.count() == count;
    }

    private String processSlots() {
        TradeOffer offer = offer(parse("{\"type\":\"villagertradingplus:process_item\","
                + "\"convertible\":{\"item\":\"minecraft:cod\",\"count\":6},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":1},"
                + "\"sell\":{\"item\":\"minecraft:cooked_cod\",\"count\":6}}"), plains);

        if (!is(offer.getFirstBuyItem(), Items.EMERALD, 1)) {
            return "the first slot is " + offer.getFirstBuyItem();
        }

        if (offer.getSecondBuyItem().isEmpty() || !is(offer.getSecondBuyItem().get(), Items.COD, 6)) {
            return "the second slot is " + offer.getSecondBuyItem();
        }

        return null;
    }

    private String processPricing() {
        float scale = VillagerTradingPlus.CONFIG.trade_cost_scale;

        try {
            VillagerTradingPlus.CONFIG.trade_cost_scale = 3.0f;
            TradeOffer offer = offer(parse("{\"type\":\"villagertradingplus:process_item\","
                    + "\"convertible\":{\"item\":\"minecraft:cod\",\"count\":6},"
                    + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":2},"
                    + "\"sell\":{\"item\":\"minecraft:cooked_cod\",\"count\":6}}"), plains);
            TradeOffer priced = TradePricing.apply(offer, plains);

            if (!is(priced.getFirstBuyItem(), Items.EMERALD, 6) || !is(priced.getSecondBuyItem().orElseThrow(), Items.COD, 6)) {
                return "the scaled offer is " + priced.getFirstBuyItem() + " and " + priced.getSecondBuyItem();
            }

            return null;
        } finally {
            VillagerTradingPlus.CONFIG.trade_cost_scale = scale;
        }
    }

    private String tippedArrow() {
        TradeOffer offer = offer(parse(TIPPED_ARROW), plains);

        if (!is(offer.getFirstBuyItem(), Items.EMERALD, 2)) {
            return "the first slot is " + offer.getFirstBuyItem();
        }

        var second = offer.getSecondBuyItem().orElseThrow();

        if (!is(second, Items.ARROW, 5) || !second.components().isEmpty()) {
            return "the second slot is " + second + " with a predicate";
        }

        if (offer.getSellItem().getItem() != Items.TIPPED_ARROW
                || offer.getSellItem().getCount() != 5
                || offer.getSellItem().get(DataComponentTypes.POTION_CONTENTS) == null
                || offer.getSellItem().get(DataComponentTypes.POTION_CONTENTS).potion().isEmpty()) {
            return "the arrows are " + offer.getSellItem();
        }

        return null;
    }

    private String waterBottle() {
        TradeOffer offer = offer(parse("{\"type\":\"villagertradingplus:sell_potion\","
                + "\"convertible\":{\"item\":\"minecraft:potion\",\"count\":3},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":5},"
                + "\"sell\":{\"item\":\"minecraft:splash_potion\",\"count\":1}}"), plains);
        var second = offer.getSecondBuyItem().orElseThrow();

        if (!is(offer.getFirstBuyItem(), Items.EMERALD, 5) || !is(second, Items.POTION, 1)) {
            return "the slots are " + offer.getFirstBuyItem() + " and " + second;
        }

        ItemStack water = new ItemStack(Items.POTION);
        water.set(DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.WATER));
        ItemStack awkward = new ItemStack(Items.POTION);
        awkward.set(DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.AWKWARD));

        return second.matches(new ItemStack(Items.POTION, 1)) || !second.matches(copyWithCount(water, 1)) || second.matches(copyWithCount(awkward, 1))
                ? "the second slot does not ask for water"
                : null;
    }

    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private String dyedArmorOnlyDyeable() {
        TradeOffer sword = offer(parse("{\"type\":\"villagertradingplus:sell_dyed_armor\","
                + "\"sell\":{\"item\":\"minecraft:diamond_sword\",\"count\":1},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":4}}"), plains);

        if (sword.getSellItem().contains(DataComponentTypes.DYED_COLOR)) {
            return "a sword was dyed";
        }

        TradeOffer boots = offer(parse("{\"type\":\"villagertradingplus:sell_dyed_armor\","
                + "\"sell\":{\"item\":\"minecraft:leather_boots\",\"count\":1},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":4}}"), plains);
        DyedColorComponent color = boots.getSellItem().get(DataComponentTypes.DYED_COLOR);

        return color == null ? "leather boots were not dyed" : null;
    }

    private String villagerType() {
        TradeOffers.Factory factory = parse("{\"type\":\"villagertradingplus:buy_item_by_villager_type\","
                + "\"buy\":{\"minecraft:desert\":{\"item\":\"minecraft:jungle_boat\",\"count\":1}},"
                + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}");
        TradeOffer forDesert = offer(factory, desert);

        if (forDesert == null || !is(forDesert.getFirstBuyItem(), Items.JUNGLE_BOAT, 1)) {
            return "the desert villager offer is " + forDesert;
        }

        if (offer(factory, plains) != null) {
            return "a plains villager got an offer";
        }

        TradeOffers.Factory longKey = parse("{\"type\":\"villagertradingplus:buy_item_by_villager_type\","
                + "\"buy_by_type\":{\"minecraft:desert\":{\"item\":\"minecraft:jungle_boat\",\"count\":1}},"
                + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}");

        if (offer(longKey, desert) == null) {
            return "buy_by_type was not accepted";
        }

        TradeOffers.Factory plain = parse("{\"type\":\"villagertradingplus:buy_item_by_villager_type\","
                + "\"buy\":{\"minecraft:desert\":\"minecraft:jungle_boat\"},"
                + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}");
        TradeOffer plainOffer = offer(plain, desert);

        if (plainOffer == null || !is(plainOffer.getFirstBuyItem(), Items.JUNGLE_BOAT, 1)) {
            return "a plain item id entry was not accepted";
        }

        return offer(factory, runtime.wanderer()) != null ? "a wandering trader got an offer" : null;
    }

    private String villagerTypeUnknown() {
        TradeOffers.Factory factory = TradeOfferManager.deserializeTrade(JsonParser.parseString(
                "{\"type\":\"villagertradingplus:buy_item_by_villager_type\","
                        + "\"buy\":{\"minecraft:nowhere\":{\"item\":\"minecraft:jungle_boat\",\"count\":1}},"
                        + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}").getAsJsonObject());

        return factory == null ? null : "an unknown villager type was accepted";
    }

    private String villagerTypeSkipsUnknown() {
        TradeOffers.Factory factory = TradeOfferManager.deserializeTrade(JsonParser.parseString(
                "{\"type\":\"villagertradingplus:buy_item_by_villager_type\","
                        + "\"buy\":{\"othermod:jungle_tropics\":{\"item\":\"minecraft:jungle_boat\",\"count\":1},"
                        + "\"minecraft:desert\":{\"item\":\"minecraft:jungle_boat\",\"count\":1}},"
                        + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}").getAsJsonObject());

        if (factory == null) {
            return "one unknown villager type dropped the whole trade";
        }

        TradeOffer forDesert = offer(factory, desert);
        return forDesert == null || !is(forDesert.getFirstBuyItem(), Items.JUNGLE_BOAT, 1)
                ? "the known villager type lost its offer"
                : null;
    }

    private static TradeOffers.Factory unwrap(TradeOffers.Factory factory) {
        TradeOffers.Factory current = factory;

        while (true) {
            if (current instanceof ConditionalTradeFactory conditional) {
                current = conditional.delegate();
            } else if (current instanceof RegistryRebindFactory rebind) {
                current = rebind.delegate();
            } else {
                return current;
            }
        }
    }

    private static String map(String extra) {
        return "{\"type\":\"villagertradingplus:sell_map\","
                + "\"structure_id\":\"minecraft:on_woodland_explorer_maps\",\"name\":\"filled_map.mansion\","
                + "\"buy\":{\"item\":\"minecraft:compass\",\"count\":1},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":14}" + extra + "}";
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private String mapDecoration() {
        Object none = field(unwrap(parse(map(""))), "decoration");
        Object mansion = field(unwrap(parse(map(",\"decoration\":\"minecraft:mansion\""))), "decoration");
        Object unknown = field(unwrap(parse(map(",\"decoration\":\"minecraft:nothing\""))), "decoration");

        if (none != MapDecorationTypes.RED_X) {
            return "the default marker is " + none;
        }

        if (mansion != MapDecorationTypes.MANSION.value() && !mansion.equals(MapDecorationTypes.MANSION)) {
            return "the mansion marker is " + mansion;
        }

        return unknown != MapDecorationTypes.RED_X ? "an unknown marker did not fall back to the red X" : null;
    }

    private String mapName() {
        try {
            Object factory = unwrap(parse(map("")));
            Method name = factory.getClass().getDeclaredMethod("nameMap", ItemStack.class);
            name.setAccessible(true);
            ItemStack stack = new ItemStack(Items.FILLED_MAP);
            name.invoke(factory, stack);
            ItemStack plain = new ItemStack(Items.FILLED_MAP);

            if (stack.get(DataComponentTypes.CUSTOM_NAME) != null) {
                return "the map has a custom name";
            }

            return stack.get(DataComponentTypes.ITEM_NAME) == null
                    || stack.get(DataComponentTypes.ITEM_NAME).equals(plain.get(DataComponentTypes.ITEM_NAME))
                    ? "the map has no item name of its own"
                    : null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private String costCounts() {
        TradeOffer books = offer(parse("{\"type\":\"villagertradingplus:buy_item\","
                + "\"buy\":{\"item\":\"minecraft:writable_book\",\"count\":2},"
                + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}"), plains);

        if (!is(books.getFirstBuyItem(), Items.WRITABLE_BOOK, 2)) {
            return "the cost is " + books.getFirstBuyItem();
        }

        TradeOffer tooMany = offer(parse("{\"type\":\"villagertradingplus:buy_item\","
                + "\"buy\":{\"item\":\"minecraft:stick\",\"count\":500},"
                + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}"), plains);

        if (tooMany.getFirstBuyItem().count() != 99) {
            return "a cost of 500 became " + tooMany.getFirstBuyItem().count();
        }

        TradeOffer result = offer(parse("{\"type\":\"villagertradingplus:sell_item\","
                + "\"sell\":{\"item\":\"minecraft:ender_pearl\",\"count\":40},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":1}}"), plains);

        return result.getSellItem().getCount() != 16 ? "a result of 40 pearls became " + result.getSellItem().getCount() : null;
    }

    private String secondSlotCosts() {
        TradeOffer potion = offer(parse("{\"type\":\"villagertradingplus:sell_potion\","
                + "\"convertible\":{\"item\":\"minecraft:potion\",\"count\":3},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":5},"
                + "\"sell\":{\"item\":\"minecraft:splash_potion\",\"count\":1}}"), plains);

        if (potion.getSecondBuyItem().isEmpty() || potion.getSecondBuyItem().get().count() != 1) {
            return "the potion in the second slot is " + potion.getSecondBuyItem();
        }

        TradeOffer process = offer(parse("{\"type\":\"villagertradingplus:process_item\","
                + "\"convertible\":{\"item\":\"minecraft:diamond_sword\",\"count\":2},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":1},"
                + "\"sell\":{\"item\":\"minecraft:diamond\",\"count\":1}}"), plains);

        if (process.getSecondBuyItem().isEmpty() || process.getSecondBuyItem().get().count() != 1) {
            return "the sword in the second slot is " + process.getSecondBuyItem();
        }

        TradeOffer multi = offer(parse("{\"type\":\"villagertradingplus:multi_input\","
                + "\"input_a\":{\"item\":\"minecraft:writable_book\",\"count\":2},"
                + "\"input_b\":{\"item\":\"minecraft:writable_book\",\"count\":2},"
                + "\"sell\":{\"item\":\"minecraft:emerald\",\"count\":1}}"), plains);

        if (multi.getFirstBuyItem().count() != 2) {
            return "the first slot of a multi_input trade is " + multi.getFirstBuyItem();
        }

        return multi.getSecondBuyItem().isEmpty() || multi.getSecondBuyItem().get().count() != 1
                ? "the second slot of a multi_input trade is " + multi.getSecondBuyItem()
                : null;
    }

    private String oversizedCostPricing() {
        float scale = VillagerTradingPlus.CONFIG.trade_cost_scale;

        try {
            VillagerTradingPlus.CONFIG.trade_cost_scale = 1.5f;
            TradeOffer offer = TradePricing.apply(offer(parse("{\"type\":\"villagertradingplus:buy_item\","
                    + "\"buy\":{\"item\":\"minecraft:writable_book\",\"count\":2},"
                    + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}"), plains), plains);

            return offer.getFirstBuyItem().count() != 3 ? "two books at 1.5 became " + offer.getFirstBuyItem().count() : null;
        } finally {
            VillagerTradingPlus.CONFIG.trade_cost_scale = scale;
        }
    }

    /// Vanilla's two tags have the same entries, so only the source tells which one is read.
    private String toolTag() throws java.io.IOException {
        java.nio.file.Path source = java.nio.file.Path.of(System.getProperty("vtp.rootDir"))
                .resolve("common/src/main/java/com/lion/villagertradingplus/tradeoffers/trades/JsonSellEnchantedToolTradeOffer.java");
        String text = java.nio.file.Files.readString(source);

        return text.contains("EnchantmentTags.ON_TRADED_EQUIPMENT") && !text.contains("IN_ENCHANTING_TABLE")
                ? null
                : "the tool trade does not read #minecraft:on_traded_equipment";
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static String buy(String item) {
        return "{\"type\":\"villagertradingplus:buy_item\",\"buy\":{\"item\":\"" + item + "\",\"count\":1},"
                + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}";
    }

    private List<Pair<TradeOffers.Factory[], Integer>> wandering(String file) {
        WanderingTraderTradeLoader.begin();
        TradeOfferManager.deserializeWanderingTraderJson(Identifier.of("vtp_test", "wandering"), json(file));
        WanderingTraderTradeLoader.apply();
        return WanderingTraderTradeLoader.pools();
    }

    private String wanderingBuying() {
        var pools = wandering("{\"trades\":{\"buying\":[" + buy("minecraft:stick") + "]}}");
        int vanilla = VanillaTables.wandering().get(0).getLeft().length;

        if (pools.size() != 3 || pools.get(0).getLeft().length != vanilla + 1 || pools.get(0).getRight() != 2) {
            return "the buying pool has " + pools.get(0).getLeft().length + " trades and " + pools.get(0).getRight() + " draws";
        }

        return pools.get(1).getLeft().length != VanillaTables.wandering().get(1).getLeft().length
                || pools.get(2).getLeft().length != VanillaTables.wandering().get(2).getLeft().length
                ? "the other pools changed"
                : null;
    }

    private String wanderingBuyingKept() {
        var pools = wandering("{\"trades\":{\"common\":[" + buy("minecraft:stick") + "]}}");
        TradeOffers.Factory[] vanilla = VanillaTables.wandering().get(0).getLeft();

        if (pools.get(0).getLeft().length != vanilla.length || pools.get(0).getRight() != 2) {
            return "the buying pool changed";
        }

        for (int i = 0; i < vanilla.length; i++) {
            if (pools.get(0).getLeft()[i] != vanilla[i]) {
                return "buying offer " + i + " is not the vanilla one";
            }
        }

        return null;
    }

    private String wanderingBuyingReplace() {
        var pools = wandering("{\"replace\":true,\"trades\":{\"buying\":[" + buy("minecraft:stick") + "]}}");

        if (pools.get(0).getLeft().length != 1) {
            return "the replaced buying pool has " + pools.get(0).getLeft().length + " trades";
        }

        return pools.get(1).getLeft().length != VanillaTables.wandering().get(1).getLeft().length
                ? "a tier without trades lost its vanilla offers"
                : null;
    }

    private String wanderingStaged() {
        wandering("{\"trades\":{\"buying\":[" + buy("minecraft:stick") + "]}}");
        List<TradeOffers.Factory> target = new ArrayList<>(List.of(VanillaTables.wandering().get(0).getLeft()));
        WanderingTraderTradeLoader.applyStagedTo(0, target);
        return target.size() != VanillaTables.wandering().get(0).getLeft().length + 1 ? "the list has " + target.size() + " trades" : null;
    }

    private TradeOffers.Factory marker() {
        return parse(buy("minecraft:stick"));
    }

    private String rebalanceMerge() {
        try {
            RegistryKey<VillagerProfession> librarian = VillagerProfession.LIBRARIAN;
            RegistryKey<VillagerProfession> farmer = VillagerProfession.FARMER;
            TradeOfferRegistryLoader.begin();
            TradeOffers.Factory replacement = marker();
            Int2ObjectMap<TradeOffers.Factory[]> levels = new Int2ObjectOpenHashMap<>();
            levels.put(1, new TradeOffers.Factory[] {replacement});
            TradeMerger.installDefaults(java.util.Map.of(librarian, levels));

            if (TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1)[0] != replacement
                    || TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1)[0] != replacement) {
                return "a replaced profession is not in both tables";
            }

            TradeOfferRegistryLoader.begin();

            if (TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1)[0] == replacement
                    || TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length
                    != VanillaTables.rebalanced().get("librarian").get(1).length) {
                return "the experiment table was not given back";
            }

            TradeOffers.Factory added = marker();
            TradeOfferRegistryLoader.registerVillagerTrade(librarian, 2, added);
            TradeOfferRegistryLoader.registerVillagerTrade(farmer, 2, added);
            TradeMerger.mergeIntoVanilla();

            int librarianTrades = VanillaTables.rebalanced().get("librarian").get(2).length + 1;
            int farmerTrades = VanillaTables.professions().get("farmer").get(2).length + 1;

            if (TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(2).length != librarianTrades
                    || TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian).get(2).length
                    != VanillaTables.professions().get("librarian").get(2).length + 1
                    || TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(farmer).get(2).length != farmerTrades
                    || TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.containsKey(farmer)) {
                return "an added trade is not in the right tables";
            }

            return null;
        } finally {
            TradeOfferRegistryLoader.begin();
        }
    }

    @SuppressWarnings("unchecked")
    private String rebalanceMissingProfession() throws ReflectiveOperationException {
        TradeOfferRegistryLoader.begin();

        Field field = TradeMerger.class.getDeclaredField("rebalancedVanilla");
        field.setAccessible(true);
        java.util.Map<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>> snapshot =
                (java.util.Map<RegistryKey<VillagerProfession>, Int2ObjectMap<TradeOffers.Factory[]>>) field.get(null);
        Int2ObjectMap<TradeOffers.Factory[]> saved = snapshot.remove(VillagerProfession.LIBRARIAN);

        try {
            int before = TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(VillagerProfession.LIBRARIAN).get(2).length;

            for (int reload = 0; reload < 3; reload++) {
                TradeOfferRegistryLoader.begin();
                TradeOfferRegistryLoader.registerVillagerTrade(VillagerProfession.LIBRARIAN, 2, marker());
                TradeMerger.mergeIntoVanilla();

                int size = TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(VillagerProfession.LIBRARIAN).get(2).length;

                if (size != before + 1) {
                    return "reload " + reload + " leaves " + size + " trades, expected " + (before + 1);
                }
            }
        } finally {
            snapshot.put(VillagerProfession.LIBRARIAN, saved);
            TradeOfferRegistryLoader.begin();
        }

        return null;
    }

    private String rebalanceSelection() {
        var features = runtime.world.features;

        try {
            RegistryKey<VillagerProfession> librarian = VillagerProfession.LIBRARIAN;
            RegistryKey<VillagerProfession> farmer = VillagerProfession.FARMER;

            if (TradeMerger.tradesFor(runtime.world, librarian) != TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian)) {
                return "a normal world does not use the normal table";
            }

            runtime.world.features = FeatureFlags.DEFAULT_ENABLED_FEATURES.combine(FeatureFlags.FEATURE_MANAGER.featureSetOf(FeatureFlags.TRADE_REBALANCE));

            if (TradeMerger.tradesFor(runtime.world, librarian) != TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian)) {
                return "an experiment world does not use the experiment table";
            }

            return TradeMerger.tradesFor(runtime.world, farmer) != TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(farmer)
                    ? "a profession outside the experiment lost its table"
                    : null;
        } finally {
            runtime.world.features = features;
        }
    }
}
