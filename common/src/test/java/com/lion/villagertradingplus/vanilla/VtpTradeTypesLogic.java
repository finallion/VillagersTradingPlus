package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import com.lion.villagertradingplus.tradeoffers.TradeTables;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.potion.Potions;
import net.minecraft.predicate.ComponentPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import org.apache.commons.lang3.tuple.Pair;

public final class VtpTradeTypesLogic {

    private static boolean booted;

    private VtpTradeTypesLogic() {}

    private static synchronized void boot() throws Exception {
        if (!booted) {
            SharedConstants.createGameVersion();
            Bootstrap.initialize();
            TradeOfferManager.registerTradeOffers();
            booted = true;
        }

        TestWorld.get(false);
    }

    private static TradeOffers.Factory parse(String json) {
        return TradeOfferManager.deserializeTrade(JsonParser.parseString(json).getAsJsonObject());
    }

    private static TradeOffer create(TradeOffers.Factory factory, VillagerType type, long seed) throws Exception {
        return VanillaTradeReader.create(factory, type, seed);
    }

    private static String id(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String id(TradedItem traded) {
        return Registries.ITEM.getId(traded.item().value()).toString();
    }

    public static void processItemPutsEmeraldsInSlotA() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:process_item",
                 "convertible":{"item":"minecraft:cod","count":6},
                 "priceIn":{"item":"minecraft:emerald","count":1},
                 "sell":{"item":"minecraft:cooked_cod","count":6},
                 "max_uses":16,"villager_experience":1}""");
        TradeOffer offer = create(factory, VillagerType.PLAINS, 0);

        assertEquals("minecraft:emerald", id(offer.getFirstBuyItem()));
        assertEquals(1, offer.getFirstBuyItem().count());
        assertEquals("minecraft:cod", id(offer.getSecondBuyItem().get()));
        assertEquals(6, offer.getSecondBuyItem().get().count());
        assertEquals("minecraft:cooked_cod", id(offer.getSellItem()));
    }

    public static void sellPotionExpressesTheTippedArrowTrade() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_potion",
                 "sell":{"item":"minecraft:tipped_arrow","count":5},
                 "convertible":{"item":"minecraft:arrow","count":5},
                 "priceIn":{"item":"minecraft:emerald","count":2},
                 "max_uses":12,"villager_experience":30}""");
        TradeOffer offer = create(factory, VillagerType.PLAINS, 3);

        assertEquals("minecraft:emerald", id(offer.getFirstBuyItem()));
        assertEquals(2, offer.getFirstBuyItem().count());
        assertEquals("minecraft:arrow", id(offer.getSecondBuyItem().get()));
        assertEquals(ComponentPredicate.EMPTY, offer.getSecondBuyItem().get().components(), "arrows must not carry a potion filter");
        assertEquals(5, offer.getSecondBuyItem().get().count());
        assertEquals("minecraft:tipped_arrow", id(offer.getSellItem()));
        assertEquals(5, offer.getSellItem().getCount());
        assertTrue(offer.getSellItem().get(DataComponentTypes.POTION_CONTENTS).hasEffects());
    }

    public static void sellPotionKeepsTheWaterFilterOnBottles() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:potion","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":5},
                 "sell":{"item":"minecraft:splash_potion","count":1}}""");
        TradeOffer offer = create(factory, VillagerType.PLAINS, 1);
        TradedItem second = offer.getSecondBuyItem().get();

        assertEquals("minecraft:emerald", id(offer.getFirstBuyItem()));
        assertEquals("minecraft:potion", id(second));
        assertNotEquals(ComponentPredicate.EMPTY, second.components());
        assertTrue(second.matches(waterBottle()));
    }

    private static ItemStack waterBottle() {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.WATER));
        return stack;
    }

    private static void assertNotEquals(Object unexpected, Object actual) {
        assertFalse(unexpected.equals(actual));
    }

    public static void sellDyedArmorBlendsUpToThreeDyes() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_dyed_armor",
                 "sell":{"item":"minecraft:leather_chestplate"},
                 "priceIn":{"item":"minecraft:emerald","count":7},
                 "max_uses":12,"villager_experience":5,"price_multiplier":0.2}""");
        Set<Integer> colors = new HashSet<>();

        for (int seed = 0; seed < 2000; seed++) {
            TradeOffer offer = create(factory, VillagerType.PLAINS, seed);
            ItemStack armor = offer.getSellItem();

            assertEquals("minecraft:leather_chestplate", id(armor));
            assertNotNull(armor.get(DataComponentTypes.DYED_COLOR), "seed " + seed + " is not dyed");
            assertEquals(7, offer.getFirstBuyItem().count());
            assertEquals(0.2F, offer.getPriceMultiplier());
            colors.add(armor.get(DataComponentTypes.DYED_COLOR).rgb());
        }

        assertTrue(colors.size() > 100, "blends should give many colors, got " + colors.size());
    }

    public static void sellDyedArmorDyesHorseArmorLikeVanilla() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_dyed_armor",
                 "sell":{"item":"minecraft:leather_horse_armor"},
                 "priceIn":{"item":"minecraft:emerald","count":6}}""");

        assertNotNull(create(factory, VillagerType.PLAINS, 0).getSellItem().get(DataComponentTypes.DYED_COLOR));
    }

    public static void sellDyedArmorLeavesOtherItemsAlone() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_dyed_armor",
                 "sell":{"item":"minecraft:iron_helmet"},
                 "priceIn":{"item":"minecraft:emerald","count":5}}""");

        assertNull(create(factory, VillagerType.PLAINS, 0).getSellItem().get(DataComponentTypes.DYED_COLOR));
    }

    public static void villagerTypeItemFollowsTheVillagerType() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "count":1,
                 "buy_by_type":{"minecraft:plains":"minecraft:oak_boat","minecraft:taiga":"minecraft:spruce_boat"},
                 "reward":{"item":"minecraft:emerald","count":1},
                 "max_uses":12,"villager_experience":30}""");

        assertEquals("minecraft:oak_boat", id(create(factory, VillagerType.PLAINS, 0).getFirstBuyItem()));
        assertEquals("minecraft:spruce_boat", id(create(factory, VillagerType.TAIGA, 0).getFirstBuyItem()));
        assertEquals("minecraft:emerald", id(create(factory, VillagerType.PLAINS, 0).getSellItem()));
        assertNull(create(factory, VillagerType.DESERT, 0), "a type without an entry gives no offer");
        assertNull(factory.create(null, Random.create(0)), "a merchant without villager data gives no offer");

        TradeOffers.Factory shortKey = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy":{"minecraft:plains":"minecraft:oak_boat"},
                 "reward":{"item":"minecraft:emerald","count":1}}""");

        assertEquals("minecraft:oak_boat", id(create(shortKey, VillagerType.PLAINS, 0).getFirstBuyItem()));

        TradeOffers.Factory objectForm = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy_by_type":{"minecraft:plains":{"item":"minecraft:oak_boat","count":2}},
                 "reward":{"item":"minecraft:emerald","count":1}}""");

        assertEquals(2, create(objectForm, VillagerType.PLAINS, 0).getFirstBuyItem().count());
    }

    public static void villagerTypeItemRejectsUnknownTypes() throws Exception {
        boot();
        assertNull(parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy_by_type":{"minecraft:nowhere":"minecraft:oak_boat"}}"""));
    }

    private static Object decoration(String extra) throws Exception {
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_map",
                 "structure_id":"minecraft:on_ocean_explorer_maps","name":"filled_map.monument",
                 "buy":{"item":"minecraft:compass","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":13}%s}""".formatted(extra));

        if (factory == null) {
            return null;
        }

        return VanillaTradeReader.field(factory, "decoration");
    }

    public static void mapDecorationDefaultsToRedX() throws Exception {
        boot();
        assertSame(MapDecorationTypes.RED_X, decoration(""));
        assertSame(MapDecorationTypes.MONUMENT, decoration(",\"decoration\":\"monument\""));
        assertSame(MapDecorationTypes.MANSION, decoration(",\"decoration\":\"minecraft:mansion\""));
        assertSame(MapDecorationTypes.TRIAL_CHAMBERS, decoration(",\"decoration\":\"trial_chambers\""));
        assertSame(MapDecorationTypes.RED_X, decoration(",\"decoration\":\"not_an_icon\""));
    }

    public static void mapNameIsAnItemName() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_map",
                 "structure_id":"minecraft:on_ocean_explorer_maps","name":"filled_map.monument",
                 "buy":{"item":"minecraft:compass","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":13}}""");
        Method nameMap = factory.getClass().getDeclaredMethod("nameMap", ItemStack.class);
        nameMap.setAccessible(true);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        nameMap.invoke(factory, map);

        assertEquals(Text.translatable("filled_map.monument"), map.get(DataComponentTypes.ITEM_NAME));
        assertNull(map.get(DataComponentTypes.CUSTOM_NAME));
    }

    public static void costsMayExceedAStackSizeOfOne() throws Exception {
        boot();
        TradeOffers.Factory book = parse("""
                {"type":"villagertradingplus:buy_item",
                 "buy":{"item":"minecraft:writable_book","count":2},
                 "reward":{"item":"minecraft:emerald","count":1}}""");
        assertEquals(2, create(book, VillagerType.PLAINS, 0).getFirstBuyItem().count());

        TradeOffers.Factory tooMany = parse("""
                {"type":"villagertradingplus:buy_item",
                 "buy":{"item":"minecraft:writable_book","count":100},
                 "reward":{"item":"minecraft:emerald","count":1}}""");
        assertEquals(64, create(tooMany, VillagerType.PLAINS, 0).getFirstBuyItem().count());

        TradeOffers.Factory stackable = parse("""
                {"type":"villagertradingplus:buy_item",
                 "buy":{"item":"minecraft:wheat","count":100},
                 "reward":{"item":"minecraft:emerald","count":1}}""");
        assertEquals(64, create(stackable, VillagerType.PLAINS, 0).getFirstBuyItem().count());
    }

    public static void resultsStayClampedToTheStackSize() throws Exception {
        boot();
        TradeOffers.Factory sell = parse("""
                {"type":"villagertradingplus:sell_item",
                 "sell":{"item":"minecraft:writable_book","count":2},
                 "priceIn":{"item":"minecraft:emerald","count":1}}""");
        TradeOffer offer = create(sell, VillagerType.PLAINS, 0);

        assertEquals(1, offer.getSellItem().getCount());
        assertEquals(Items.WRITABLE_BOOK, offer.getSellItem().getItem());
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static final String SELL_SAND = """
            {"type":"villagertradingplus:sell_item","sell":{"item":"minecraft:sand","count":1},
             "priceIn":{"item":"minecraft:emerald","count":1},"max_uses":12,"villager_experience":1}""";

    public static void rebalancedVillagerTablesGetTheSameTrades() throws Exception {
        boot();
        VillagerProfession librarian = VillagerProfession.LIBRARIAN;
        Int2ObjectMap<TradeOffers.Factory[]> rebalanced = TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian);
        Int2ObjectMap<TradeOffers.Factory[]> normal = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian);
        int rebalancedBefore = rebalanced.get(1).length;
        int normalBefore = normal.get(1).length;

        TradeOfferRegistryLoader.begin();
        TradeOfferManager.deserializeJson(
                Identifier.of("villagertradingplus", "test_add"),
                json("{\"profession\":\"minecraft:librarian\",\"trades\":{\"novice\":[" + SELL_SAND + "]}}")
        );
        TradeOfferManager.deserializeJson(
                Identifier.of("villagertradingplus", "test_add_farmer"),
                json("{\"profession\":\"minecraft:farmer\",\"trades\":{\"novice\":[" + SELL_SAND + "]}}")
        );
        TradeMerger.mergeIntoVanilla();

        assertEquals(rebalancedBefore + 1, TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length);
        assertEquals(normalBefore + 1, TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length);
        assertFalse(TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.containsKey(VillagerProfession.FARMER));

        TradeOfferRegistryLoader.begin();
        assertEquals(rebalancedBefore, TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length, "reset restores the rebalanced table");
        assertEquals(normalBefore, TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length);

        TradeOfferManager.deserializeJson(
                Identifier.of("villagertradingplus", "test_default"),
                json("{\"profession\":\"minecraft:librarian\",\"trades\":{\"novice\":[" + SELL_SAND + "]}}")
        );
        TradeMerger.installDefaults(TradeOfferRegistryLoader.getRegistryForLoading());

        assertEquals(1, TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).size());
        assertEquals(1, TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length);
        assertEquals(1, TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length);

        TradeOfferRegistryLoader.begin();
        assertEquals(5, TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).size());
        assertEquals(rebalancedBefore, TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian).get(1).length);
    }

    public static void rebalancedWorldsPickTheRebalancedTable() throws Exception {
        boot();
        TestWorld normal = TestWorld.get(false);
        TestWorld experiment = TestWorld.get(true);
        VillagerProfession librarian = VillagerProfession.LIBRARIAN;
        VillagerProfession farmer = VillagerProfession.FARMER;

        assertFalse(TradeTables.rebalanced(normal.world));
        assertTrue(TradeTables.rebalanced(experiment.world));
        assertSame(TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(librarian), TradeTables.villagerLevels(normal.world, librarian));
        assertSame(TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(librarian), TradeTables.villagerLevels(experiment.world, librarian));
        assertSame(TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(farmer), TradeTables.villagerLevels(experiment.world, farmer), "a profession missing from the experiment falls back");
        TestWorld.get(false);
    }

    public static void rebalancedWandererPoolsTakeCommonAndRare() throws Exception {
        boot();
        List<Pair<TradeOffers.Factory[], Integer>> vanilla = TradeOffers.REBALANCED_WANDERING_TRADER_TRADES;
        assertEquals(3, vanilla.size());
        int rare = vanilla.get(1).getLeft().length;
        int common = vanilla.get(2).getLeft().length;
        int buying = vanilla.get(0).getLeft().length;

        WanderingTraderTradeLoader.begin();
        TradeOfferManager.deserializeWanderingTraderJson(
                Identifier.of("villagertradingplus", "test_wanderer"),
                json("{\"replace\":false,\"trades\":{\"common\":[" + SELL_SAND + "],\"rare\":[" + SELL_SAND + "," + SELL_SAND + "]}}")
        );
        WanderingTraderTradeLoader.stage();

        List<Pair<TradeOffers.Factory[], Integer>> pools = WanderingTraderTradeLoader.rebalancedPools();
        assertEquals(3, pools.size());
        assertEquals(buying, pools.get(0).getLeft().length, "the buying pool stays as it is");
        assertEquals(rare + 2, pools.get(1).getLeft().length);
        assertEquals(common + 1, pools.get(2).getLeft().length);
        assertEquals(vanilla.get(1).getRight(), pools.get(1).getRight());
        assertEquals(VillagerTradingPlus.CONFIG.trade_offers_wandering_trader, pools.get(2).getRight());
        assertEquals(common + 1, WanderingTraderTradeLoader.rebalancedPool(1).length);
        assertEquals(rare + 2, WanderingTraderTradeLoader.rebalancedPool(2).length);

        WanderingTraderTradeLoader.begin();
        TradeOfferManager.deserializeWanderingTraderJson(
                Identifier.of("villagertradingplus", "test_wanderer_replace"),
                json("{\"replace\":true,\"trades\":{\"common\":[" + SELL_SAND + "]}}")
        );
        WanderingTraderTradeLoader.stage();

        assertEquals(1, WanderingTraderTradeLoader.rebalancedPool(1).length, "replace drops the vanilla common pool");
        assertEquals(rare, WanderingTraderTradeLoader.rebalancedPool(2).length, "a tier without trades keeps vanilla");

        WanderingTraderTradeLoader.begin();
        WanderingTraderTradeLoader.stage();
        assertEquals(common, WanderingTraderTradeLoader.rebalancedPool(1).length);
        assertEquals(rare, WanderingTraderTradeLoader.rebalancedPool(2).length);
    }

    public static void villagerTypeItemSkipsOnlyTheUnknownEntry() throws Exception {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy_by_type":{"othermod:jungle_tropics":"minecraft:jungle_boat","minecraft:plains":"minecraft:oak_boat"},
                 "reward":{"item":"minecraft:emerald","count":1}}""");

        assertNotNull(factory, "one unknown type must not drop the whole trade");
        assertEquals("minecraft:oak_boat", id(create(factory, VillagerType.PLAINS, 0).getFirstBuyItem()));
    }

    public static void secondSlotCostsStayWithinTheStackSize() throws Exception {
        boot();
        TradeOffers.Factory potion = parse("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:potion","count":3},
                 "priceIn":{"item":"minecraft:emerald","count":5},
                 "sell":{"item":"minecraft:splash_potion","count":1}}""");
        assertEquals(1, create(potion, VillagerType.PLAINS, 1).getSecondBuyItem().get().count());

        TradeOffers.Factory process = parse("""
                {"type":"villagertradingplus:process_item",
                 "convertible":{"item":"minecraft:diamond_sword","count":2},
                 "priceIn":{"item":"minecraft:emerald","count":1},
                 "sell":{"item":"minecraft:diamond","count":1}}""");
        assertEquals(1, create(process, VillagerType.PLAINS, 0).getSecondBuyItem().get().count());

        TradeOffers.Factory multi = parse("""
                {"type":"villagertradingplus:multi_input",
                 "input_a":{"item":"minecraft:writable_book","count":2},
                 "input_b":{"item":"minecraft:writable_book","count":2},
                 "sell":{"item":"minecraft:emerald","count":1}}""");
        TradeOffer offer = create(multi, VillagerType.PLAINS, 0);
        assertEquals(2, offer.getFirstBuyItem().count());
        assertEquals(1, offer.getSecondBuyItem().get().count());
    }

    @SuppressWarnings("unchecked")
    public static void rebalancedTableOfALateProfessionDoesNotGrow() throws Exception {
        boot();
        TradeMerger.resetToVanilla();

        java.lang.reflect.Field field = TradeMerger.class.getDeclaredField("rebalancedVanilla");
        field.setAccessible(true);
        java.util.Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> snapshot =
                (java.util.Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>>) field.get(null);
        Int2ObjectMap<TradeOffers.Factory[]> saved = snapshot.remove(VillagerProfession.LIBRARIAN);

        try {
            int before = TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(VillagerProfession.LIBRARIAN).get(1).length;

            for (int reload = 0; reload < 3; reload++) {
                TradeOfferRegistryLoader.begin();
                TradeOfferManager.deserializeJson(
                        Identifier.of("villagertradingplus", "test_late"),
                        json("{\"profession\":\"minecraft:librarian\",\"trades\":{\"novice\":[" + SELL_SAND + "]}}")
                );
                TradeMerger.mergeIntoVanilla();

                assertEquals(
                        before + 1,
                        TradeOffers.REBALANCED_PROFESSION_TO_LEVELED_TRADE.get(VillagerProfession.LIBRARIAN).get(1).length
                );
            }
        } finally {
            snapshot.put(VillagerProfession.LIBRARIAN, saved);
            TradeOfferRegistryLoader.begin();
        }
    }

    public static void rebalancedWandererBuyingPoolIsACatalogPage() throws Exception {
        boot();
        Pair<TradeOffers.Factory[], Integer> buying = TradeOffers.REBALANCED_WANDERING_TRADER_TRADES.get(0);

        WanderingTraderTradeLoader.begin();
        WanderingTraderTradeLoader.stage();

        assertEquals(3, WanderingTraderTradeLoader.REBALANCED_BUYING_PAGE);
        assertEquals(buying.getLeft().length, WanderingTraderTradeLoader.rebalancedPool(3).length);
        assertEquals(buying.getRight(), WanderingTraderTradeLoader.rebalancedPicks(3));
    }
}
