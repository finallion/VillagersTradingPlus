package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerType;

public final class VtpTradeTypesLogic {

    private static boolean booted;

    private VtpTradeTypesLogic() {}

    private static synchronized void boot() {
        if (!booted) {
            SharedConstants.createGameVersion();
            Bootstrap.initialize();
            TradeOfferManager.registerTradeOffers();
            booted = true;
        }
    }

    private static TradeOffers.Factory parse(String json) {
        return TradeOfferManager.deserializeTrade(JsonParser.parseString(json).getAsJsonObject());
    }

    private static TradeOffer create(TradeOffers.Factory factory, VillagerType type, long seed) {
        return factory.create(new StubVillager(type), Random.create(seed));
    }

    private static String id(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    public static void processItemPutsEmeraldsInSlotA() {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:process_item",
                 "convertible":{"item":"minecraft:cod","count":6},
                 "priceIn":{"item":"minecraft:emerald","count":1},
                 "sell":{"item":"minecraft:cooked_cod","count":6},
                 "max_uses":16,"villager_experience":1}""");
        TradeOffer offer = create(factory, VillagerType.PLAINS, 0);

        assertEquals("minecraft:emerald", id(offer.getOriginalFirstBuyItem()));
        assertEquals(1, offer.getOriginalFirstBuyItem().getCount());
        assertEquals("minecraft:cod", id(offer.getSecondBuyItem()));
        assertEquals(6, offer.getSecondBuyItem().getCount());
        assertEquals("minecraft:cooked_cod", id(offer.getSellItem()));
    }

    public static void sellPotionExpressesTheTippedArrowTrade() {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_potion",
                 "sell":{"item":"minecraft:tipped_arrow","count":5},
                 "convertible":{"item":"minecraft:arrow","count":5},
                 "priceIn":{"item":"minecraft:emerald","count":2},
                 "max_uses":12,"villager_experience":30}""");
        TradeOffer offer = create(factory, VillagerType.PLAINS, 3);

        assertEquals("minecraft:emerald", id(offer.getOriginalFirstBuyItem()));
        assertEquals(2, offer.getOriginalFirstBuyItem().getCount());
        assertEquals("minecraft:arrow", id(offer.getSecondBuyItem()));
        assertFalse(offer.getSecondBuyItem().hasNbt(), "arrows must not carry a potion filter");
        assertEquals(5, offer.getSecondBuyItem().getCount());
        assertEquals("minecraft:tipped_arrow", id(offer.getSellItem()));
        assertEquals(5, offer.getSellItem().getCount());
        assertFalse(PotionUtil.getPotionEffects(offer.getSellItem()).isEmpty());
    }

    public static void sellPotionKeepsTheWaterFilterOnBottles() {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:potion","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":5},
                 "sell":{"item":"minecraft:splash_potion","count":1}}""");
        TradeOffer offer = create(factory, VillagerType.PLAINS, 1);

        assertEquals("minecraft:emerald", id(offer.getOriginalFirstBuyItem()));
        assertEquals("minecraft:potion", id(offer.getSecondBuyItem()));
        assertEquals(Potions.WATER, PotionUtil.getPotion(offer.getSecondBuyItem()));
    }

    public static void sellDyedArmorBlendsUpToThreeDyes() {
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
            assertTrue(((DyeableItem) armor.getItem()).hasColor(armor), "seed " + seed + " is not dyed");
            assertEquals(7, offer.getOriginalFirstBuyItem().getCount());
            assertEquals(0.2F, offer.getPriceMultiplier());
            colors.add(((DyeableItem) armor.getItem()).getColor(armor));
        }

        assertTrue(colors.size() > 100, "blends should give many colors, got " + colors.size());
    }

    public static void sellDyedArmorLeavesOtherItemsAlone() {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_dyed_armor",
                 "sell":{"item":"minecraft:iron_helmet"},
                 "priceIn":{"item":"minecraft:emerald","count":5}}""");
        TradeOffer offer = create(factory, VillagerType.PLAINS, 0);

        assertFalse(offer.getSellItem().getOrCreateNbt().contains("display"));
    }

    public static void villagerTypeItemFollowsTheVillagerType() {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "count":1,
                 "buy_by_type":{"minecraft:plains":"minecraft:oak_boat","minecraft:taiga":"minecraft:spruce_boat"},
                 "reward":{"item":"minecraft:emerald","count":1},
                 "max_uses":12,"villager_experience":30}""");

        assertEquals("minecraft:oak_boat", id(create(factory, VillagerType.PLAINS, 0).getOriginalFirstBuyItem()));
        assertEquals("minecraft:spruce_boat", id(create(factory, VillagerType.TAIGA, 0).getOriginalFirstBuyItem()));
        assertEquals("minecraft:emerald", id(create(factory, VillagerType.PLAINS, 0).getSellItem()));
        assertNull(create(factory, VillagerType.DESERT, 0), "a type without an entry gives no offer");
        assertNull(factory.create(null, Random.create(0)), "a merchant without villager data gives no offer");

        TradeOffers.Factory shortKey = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy":{"minecraft:plains":"minecraft:oak_boat"},
                 "reward":{"item":"minecraft:emerald","count":1}}""");

        assertEquals("minecraft:oak_boat", id(create(shortKey, VillagerType.PLAINS, 0).getOriginalFirstBuyItem()));

        TradeOffers.Factory objectForm = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy_by_type":{"minecraft:plains":{"item":"minecraft:oak_boat","count":2}},
                 "reward":{"item":"minecraft:emerald","count":1}}""");

        assertEquals(2, create(objectForm, VillagerType.PLAINS, 0).getOriginalFirstBuyItem().getCount());
    }

    public static void villagerTypeItemRejectsUnknownTypes() {
        boot();
        assertNull(parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy_by_type":{"minecraft:nowhere":"minecraft:oak_boat"}}"""));
    }

    public static void villagerTypeItemSkipsOnlyTheUnknownEntry() {
        boot();
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:buy_item_by_villager_type",
                 "buy_by_type":{"othermod:jungle_tropics":"minecraft:jungle_boat","minecraft:plains":"minecraft:oak_boat"},
                 "reward":{"item":"minecraft:emerald","count":1}}""");

        assertNotNull(factory, "one unknown type must not drop the whole trade");
        assertEquals("minecraft:oak_boat", id(create(factory, VillagerType.PLAINS, 0).getOriginalFirstBuyItem()));
    }

    public static void secondSlotCostsStayWithinTheStackSize() {
        boot();
        TradeOffers.Factory potion = parse("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:potion","count":3},
                 "priceIn":{"item":"minecraft:emerald","count":5},
                 "sell":{"item":"minecraft:splash_potion","count":1}}""");
        assertEquals(1, create(potion, VillagerType.PLAINS, 1).getSecondBuyItem().getCount());

        TradeOffers.Factory process = parse("""
                {"type":"villagertradingplus:process_item",
                 "convertible":{"item":"minecraft:diamond_sword","count":2},
                 "priceIn":{"item":"minecraft:emerald","count":1},
                 "sell":{"item":"minecraft:diamond","count":1}}""");
        assertEquals(1, create(process, VillagerType.PLAINS, 0).getSecondBuyItem().getCount());

        TradeOffers.Factory multi = parse("""
                {"type":"villagertradingplus:multi_input",
                 "input_a":{"item":"minecraft:writable_book","count":2},
                 "input_b":{"item":"minecraft:writable_book","count":2},
                 "sell":{"item":"minecraft:emerald","count":1}}""");
        TradeOffer offer = create(multi, VillagerType.PLAINS, 0);
        assertEquals(2, offer.getOriginalFirstBuyItem().getCount());
        assertEquals(1, offer.getSecondBuyItem().getCount());
    }

    private static MapIcon.Type decoration(String extra) throws Exception {
        TradeOffers.Factory factory = parse("""
                {"type":"villagertradingplus:sell_map",
                 "structure_id":"minecraft:on_ocean_explorer_maps","name":"filled_map.monument",
                 "buy":{"item":"minecraft:compass","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":13}%s}""".formatted(extra));

        if (factory == null) {
            return null;
        }

        return (MapIcon.Type) VanillaTradeReader.field(factory, "decoration");
    }

    public static void mapDecorationDefaultsToRedX() throws Exception {
        boot();
        assertEquals(MapIcon.Type.RED_X, decoration(""));
        assertEquals(MapIcon.Type.MONUMENT, decoration(",\"decoration\":\"monument\""));
        assertEquals(MapIcon.Type.MANSION, decoration(",\"decoration\":\"minecraft:MANSION\""));
        assertEquals(MapIcon.Type.RED_X, decoration(",\"decoration\":\"not_an_icon\""));
    }

    public static void costsMayExceedAStackSizeOfOne() {
        boot();
        TradeOffers.Factory book = parse("""
                {"type":"villagertradingplus:buy_item",
                 "buy":{"item":"minecraft:writable_book","count":2},
                 "reward":{"item":"minecraft:emerald","count":1}}""");
        assertEquals(2, create(book, VillagerType.PLAINS, 0).getOriginalFirstBuyItem().getCount());

        TradeOffers.Factory tooMany = parse("""
                {"type":"villagertradingplus:buy_item",
                 "buy":{"item":"minecraft:writable_book","count":100},
                 "reward":{"item":"minecraft:emerald","count":1}}""");
        assertEquals(64, create(tooMany, VillagerType.PLAINS, 0).getOriginalFirstBuyItem().getCount());

        TradeOffers.Factory stackable = parse("""
                {"type":"villagertradingplus:buy_item",
                 "buy":{"item":"minecraft:wheat","count":100},
                 "reward":{"item":"minecraft:emerald","count":1}}""");
        assertEquals(64, create(stackable, VillagerType.PLAINS, 0).getOriginalFirstBuyItem().getCount());
    }

    public static void resultsStayClampedToTheStackSize() {
        boot();
        TradeOffers.Factory sell = parse("""
                {"type":"villagertradingplus:sell_item",
                 "sell":{"item":"minecraft:writable_book","count":2},
                 "priceIn":{"item":"minecraft:emerald","count":1}}""");
        TradeOffer offer = create(sell, VillagerType.PLAINS, 0);

        assertEquals(1, offer.getSellItem().getCount());
        assertNotNull(offer);
        assertEquals(Items.WRITABLE_BOOK, offer.getSellItem().getItem());
    }
}
