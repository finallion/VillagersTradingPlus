package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import com.lion.villagertradingplus.tradeoffers.TradePools;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VtpFeaturesTest {

    private static final Path TEMPLATES = Path.of(System.getProperty("vtp.rootDir")).resolve(VanillaTradeTemplates.FOLDER);

    private static VanillaEnv env;
    private static RandomSource random = RandomSource.create(7L);
    private static Entity villager;
    private static Entity wanderer;

    @BeforeAll
    static void load() {
        env = VanillaEnv.get();
        villager = env.villager(VillagerType.PLAINS, () -> random);
        wanderer = env.wanderingTrader(() -> random);
    }

    @AfterAll
    static void reset() {
        TradeOfferRegistryLoader.stageDefaultFiles(Map.of());
        TradeOfferRegistryLoader.stageAdditionFiles(Map.of());
        WanderingTraderTradeLoader.stageFiles(Map.of());
        TradeOfferRegistryLoader.finish();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static TradeFactory trade(String text) {
        TradeFactory factory = TradeOfferManager.deserializeTrade(json(text));
        assertNotNull(factory, text);
        return factory;
    }

    private static MerchantOffer offer(TradeFactory factory, Entity merchant) {
        return factory.create(env.level, merchant, random);
    }

    @Test
    void processItemKeepsTheItemInSlotAAndTheCurrencyInSlotB() {
        MerchantOffer offer = offer(trade("""
                {"type":"villagertradingplus:process_item",
                 "convertible":{"item":"minecraft:cod","count":6},
                 "priceIn":{"item":"minecraft:emerald","count":1},
                 "sell":{"item":"minecraft:cooked_cod","count":6}}"""), villager);

        assertEquals(Items.COD, offer.getItemCostA().item().value());
        assertEquals(Items.EMERALD, offer.getItemCostB().orElseThrow().item().value());
    }

    @Test
    void sellPotionFromATagFollowsTheVanillaFletcherTrade() {
        TradeFactory factory = trade("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:arrow","count":5},
                 "priceIn":{"item":"minecraft:emerald","count":2},
                 "sell":{"item":"minecraft:tipped_arrow","count":5},
                 "potions":"#minecraft:tradeable"}""");
        HolderSet<Potion> tradeable = BuiltInRegistries.POTION.get(TagKey.create(Registries.POTION, Identifier.parse("minecraft:tradeable"))).orElseThrow();
        Set<Holder<Potion>> seen = new java.util.HashSet<>();

        for (int i = 0; i < 400; i++) {
            MerchantOffer offer = offer(factory, villager);
            assertEquals(Items.EMERALD, offer.getItemCostA().item().value());
            assertEquals(2, offer.getItemCostA().count());
            assertEquals(Items.ARROW, offer.getItemCostB().orElseThrow().item().value());
            assertEquals(5, offer.getItemCostB().orElseThrow().count());
            assertTrue(offer.getItemCostB().orElseThrow().components().isEmpty(), "no water filter");
            assertEquals(5, offer.getResult().getCount());
            Holder<Potion> potion = offer.getResult().get(DataComponents.POTION_CONTENTS).potion().orElseThrow();
            assertTrue(tradeable.contains(potion));
            seen.add(potion);
        }

        assertEquals(tradeable.size(), seen.size(), "every potion of the tag is rolled");
    }

    @Test
    void sellPotionWithoutATagKeepsItsOldJson() {
        assertNotNull(TradeOfferManager.deserializeTrade(json("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:potion","count":3},
                 "priceIn":{"item":"minecraft:emerald","count":5},
                 "sell":{"item":"minecraft:splash_potion","count":1}}""")));
        assertNull(TradeOfferManager.deserializeTrade(json("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:arrow","count":5},
                 "priceIn":{"item":"minecraft:emerald","count":2},
                 "sell":{"item":"minecraft:tipped_arrow","count":5},
                 "potions":"minecraft:tradeable"}""")), "a potion list needs the # of a tag");
    }

    @Test
    void sellDyedArmorDyesTheItemAndTakesTheCurrencyInSlotA() {
        TradeFactory factory = trade("""
                {"type":"villagertradingplus:sell_dyed_armor",
                 "sell":{"item":"minecraft:leather_chestplate","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":7},
                 "max_uses":12,"villager_experience":1,"price_multiplier":0.2}""");
        Set<Integer> colors = new java.util.HashSet<>();

        for (int i = 0; i < 300; i++) {
            MerchantOffer offer = offer(factory, villager);
            assertEquals(Items.EMERALD, offer.getItemCostA().item().value());
            assertEquals(7, offer.getItemCostA().count());
            assertTrue(offer.getItemCostB().isEmpty());
            assertEquals(Items.LEATHER_CHESTPLATE, offer.getResult().getItem());
            colors.add(offer.getResult().get(DataComponents.DYED_COLOR).rgb());
            assertEquals(12, offer.getMaxUses());
            assertEquals(0.2f, offer.getPriceMultiplier());
        }

        assertTrue(colors.size() > 100, "blended colours vary");
    }

    @Test
    void sellMapTakesADecorationAndKeepsTheVanillaOneByDefault() {
        String map = """
                {"type":"villagertradingplus:sell_map",
                 "structure_id":"minecraft:on_taiga_village_maps",
                 %s
                 "buy":{"item":"minecraft:compass","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":14}}""";

        assertEquals("minecraft:village_taiga", decorationOf(trade(map.formatted(""))));
        assertEquals("minecraft:red_x", decorationOf(trade(map.formatted("\"decoration\":\"minecraft:red_x\","))));
        assertNull(TradeOfferManager.deserializeTrade(json(map.formatted("\"decoration\":\"minecraft:nowhere\","))));
    }

    private static String decorationOf(TradeFactory factory) {
        try {
            var wrapper = factory.getClass().getDeclaredField("delegate");
            wrapper.setAccessible(true);
            Object inner = wrapper.get(factory);
            var modifierField = inner.getClass().getDeclaredField("modifier");
            modifierField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Holder<net.minecraft.world.level.storage.loot.functions.LootItemFunction> modifier =
                    (Holder<net.minecraft.world.level.storage.loot.functions.LootItemFunction>) modifierField.get(inner);
            JsonElement encoded = LootItemFunctions.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, env.registries), modifier).getOrThrow();
            return findDecoration(encoded);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String findDecoration(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();

            if (object.has("decoration")) {
                return object.get("decoration").getAsString();
            }

            for (var entry : object.entrySet()) {
                String found = findDecoration(entry.getValue());

                if (found != null) {
                    return found;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String found = findDecoration(child);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    @Test
    void enchantedToolsDrawFromTheTradedEquipmentTag() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("vtp.rootDir")).resolve(
                "common/src/main/java/com/lion/villagertradingplus/tradeoffers/trades/JsonSellEnchantedToolTradeOffer.java"
        ));

        assertTrue(source.contains("EnchantmentTags.ON_TRADED_EQUIPMENT"));
        assertFalse(source.contains("IN_ENCHANTING_TABLE"));
    }

    private static void loadTemplates() throws IOException {
        Map<Identifier, JsonElement> files = new HashMap<>();

        try (var list = Files.list(TEMPLATES)) {
            for (Path file : list.toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));

                if (name.equals("wandering_trader")) {
                    continue;
                }

                files.put(Identifier.fromNamespaceAndPath("test", name), parsed);
            }
        }

        TradeOfferRegistryLoader.stageDefaultFiles(files);
        TradeOfferRegistryLoader.stageAdditionFiles(Map.of());
        WanderingTraderTradeLoader.stageFiles(Map.of(
                Identifier.fromNamespaceAndPath("test", "wandering_trader"),
                JsonParser.parseString(Files.readString(TEMPLATES.resolve("wandering_trader.json"), StandardCharsets.UTF_8))
        ));
        TradeOfferRegistryLoader.finish();
    }

    @Test
    void replacedProfessionsRollTheVanillaNumberOfTradesPerLevel() throws IOException {
        loadTemplates();
        int checked = 0;

        for (Holder.Reference<TradeSet> set : env.registries.lookupOrThrow(Registries.TRADE_SET).listElements().toList()) {
            String name = set.key().identifier().getPath();

            if (name.startsWith("wandering_trader")) {
                continue;
            }

            String profession = name.substring(0, name.indexOf('/'));
            int level = Integer.parseInt(name.substring(name.indexOf('_') + 1));
            Holder<VillagerProfession> holder = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(
                    net.minecraft.resources.ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace(profession))
            );
            int expected = TradePools.villagerPicks(env.level, villager, holder, level);
            MerchantOffers offers = new MerchantOffers();
            int added = TradePools.addVillagerOffers(env.level, villager, holder, level, offers);

            assertEquals(vanillaAmount(set.value()), expected, name + " picks");

            if (profession.equals("cartographer")) {
                assertTrue(added <= expected, name + " offers");
            } else {
                assertEquals(Math.min(expected, set.value().trades().size()), added, name + " offers");
            }

            checked++;
        }

        assertEquals(65, checked);
    }

    private static int vanillaAmount(TradeSet set) {
        var context = com.lion.villagertradingplus.tradeoffers.VanillaTradeFactory.context(
                env.level, villager, random, java.util.Optional.empty());
        return set.calculateNumberOfTrades(context);
    }

    @Test
    void masterLibrarianWithMoreTradesRollsThree() {
        String trade = "{\"type\":\"villagertradingplus:sell_item\",\"sell\":{\"item\":\"minecraft:%s\",\"count\":1},"
                + "\"priceIn\":{\"item\":\"minecraft:emerald\",\"count\":1}}";
        String trades = String.join(",", trade.formatted("apple"), trade.formatted("bread"), trade.formatted("carrot"),
                trade.formatted("potato"), trade.formatted("cookie"));
        TradeOfferRegistryLoader.stageDefaultFiles(Map.of(
                Identifier.fromNamespaceAndPath("test", "librarian"),
                JsonParser.parseString("{\"profession\":\"minecraft:librarian\",\"trades\":{\"master\":[" + trades + "]}}")
        ));
        TradeOfferRegistryLoader.stageAdditionFiles(Map.of());
        TradeOfferRegistryLoader.finish();
        Holder<VillagerProfession> librarian = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace("librarian"))
        );
        MerchantOffers offers = new MerchantOffers();

        assertEquals(3, TradePools.addVillagerOffers(env.level, villager, librarian, 5, offers));
        assertEquals(2, TradePools.villagerPicks(env.level, villager, librarian, 4));
    }

    @Test
    void wanderingTraderRollsTheBuyingPoolFromTheTemplate() throws IOException {
        loadTemplates();
        assertEquals(6, WanderingTraderTradeLoader.snapshot().tier(0).size());

        for (int i = 0; i < 20; i++) {
            MerchantOffers offers = new MerchantOffers();
            TradePools.fillWanderingTrader(env.level, wanderer, offers);

            assertEquals(9, offers.size(), "2 buying, 2 uncommon, 5 common");
            assertEquals(Items.EMERALD, offers.get(0).getResult().getItem());
            assertEquals(Items.EMERALD, offers.get(1).getResult().getItem());
        }
    }

    @Test
    void wanderingFileWithoutBuyingKeepsTheVanillaBuyingPool() throws IOException {
        WanderingTraderTradeLoader.stageFiles(Map.of(
                Identifier.fromNamespaceAndPath("test", "common_only"),
                JsonParser.parseString("""
                        {"replace":true,"trades":{"common":[
                          {"type":"villagertradingplus:sell_item",
                           "sell":{"item":"minecraft:glowstone_dust","count":4},
                           "priceIn":{"item":"minecraft:emerald","count":2}}]}}""")
        ));
        TradeOfferRegistryLoader.stageDefaultFiles(Map.of());
        TradeOfferRegistryLoader.finish();

        assertEquals(0, WanderingTraderTradeLoader.snapshot().tier(0).size());
        MerchantOffers offers = new MerchantOffers();
        TradePools.fillWanderingTrader(env.level, wanderer, offers);
        assertEquals(5, offers.size(), "2 vanilla buying, 2 vanilla uncommon, the 1 custom common trade");
        assertEquals(Items.EMERALD, offers.get(0).getResult().getItem());
    }

    private static final String DIRT_FOR_EMERALD = "{\"type\":\"villagertradingplus:buy_item\","
            + "\"buy\":{\"item\":\"minecraft:dirt\",\"count\":1},"
            + "\"reward\":{\"item\":\"minecraft:emerald\",\"count\":1}}";

    private static void stageBuying(boolean replace) {
        WanderingTraderTradeLoader.stageFiles(Map.of(
                Identifier.fromNamespaceAndPath("test", "buying"),
                JsonParser.parseString("{\"replace\":" + replace + ",\"trades\":{\"buying\":[" + DIRT_FOR_EMERALD + "]}}")
        ));
        TradeOfferRegistryLoader.stageDefaultFiles(Map.of());
        TradeOfferRegistryLoader.finish();
    }

    @Test
    void replacingFileDrawsOnlyTheCustomBuyingTrade() {
        stageBuying(true);

        for (int i = 0; i < 50; i++) {
            MerchantOffers offers = new MerchantOffers();
            TradePools.fillWanderingTrader(env.level, wanderer, offers);

            assertEquals(Items.DIRT, offers.get(0).getItemCostA().item().value());
            assertEquals(Items.EMERALD, offers.get(0).getResult().getItem());
            assertEquals(1, offers.stream().filter(offer -> offer.getItemCostA().item().value() == Items.DIRT).count());
        }
    }

    @Test
    void nonReplacingFileMixesVanillaAndCustomBuyingTrades() {
        stageBuying(false);
        int custom = 0;
        int vanilla = 0;

        for (int i = 0; i < 300; i++) {
            MerchantOffers offers = new MerchantOffers();
            TradePools.fillWanderingTrader(env.level, wanderer, offers);

            for (int slot = 0; slot < 2; slot++) {
                if (offers.get(slot).getItemCostA().item().value() == Items.DIRT) {
                    custom++;
                } else {
                    vanilla++;
                }
            }
        }

        assertTrue(custom > 0, "the custom buying trade is drawn");
        assertTrue(vanilla > custom, "vanilla buying trades are still drawn");
    }

    @Test
    void sellPotionWithAnEmptyTagGivesNoOfferAndWarnsOnce() throws ReflectiveOperationException {
        TradeFactory factory = trade("""
                {"type":"villagertradingplus:sell_potion",
                 "convertible":{"item":"minecraft:arrow","count":5},
                 "priceIn":{"item":"minecraft:emerald","count":2},
                 "sell":{"item":"minecraft:tipped_arrow","count":5},
                 "potions":"#minecraft:tradable"}""");
        var wrapper = factory.getClass().getDeclaredField("delegate");
        wrapper.setAccessible(true);
        Object inner = wrapper.get(factory);
        var warned = inner.getClass().getDeclaredField("warnedEmptyTag");
        warned.setAccessible(true);

        assertFalse(warned.getBoolean(inner));
        assertNull(offer(factory, villager));
        assertTrue(warned.getBoolean(inner), "the typo is logged");
        assertNull(offer(factory, villager));
    }

    @Test
    void sellMapNameReachesTheVanillaMapItem() throws ReflectiveOperationException {
        TradeFactory factory = trade("""
                {"type":"villagertradingplus:sell_map",
                 "structure_id":"minecraft:on_woodland_explorer_maps","name":"map.mypack.lost_mansion",
                 "buy":{"item":"minecraft:compass","count":1},
                 "priceIn":{"item":"minecraft:emerald","count":14}}""");
        var wrapper = factory.getClass().getDeclaredField("delegate");
        wrapper.setAccessible(true);
        Object inner = wrapper.get(factory);
        var givesField = inner.getClass().getDeclaredField("gives");
        givesField.setAccessible(true);
        var gives = (net.minecraft.world.item.ItemStackTemplate) givesField.get(inner);

        assertEquals(Items.WOODLAND_MANSION_MAP, gives.item().value());
        assertEquals(
                net.minecraft.network.chat.Component.translatable("map.mypack.lost_mansion"),
                gives.create().get(DataComponents.ITEM_NAME)
        );
    }
}
