package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory;
import com.lion.villagertradingplus.tradeoffers.RegistryRebindFactory;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.VanillaTradeFactory;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaTemplatesProofTest {

    private static final Path TEMPLATES = Path.of(System.getProperty("vtp.rootDir")).resolve(VanillaTradeTemplates.FOLDER);
    private static final String[] TIERS = {"novice", "apprentice", "journeyman", "expert", "master"};
    private static final int DRAWS = 20000;

    private static VanillaEnv env;
    private static final AtomicReference<RandomSource> RANDOM = new AtomicReference<>(RandomSource.create(0L));
    private static final Map<String, JsonObject> FILES = new TreeMap<>();

    private record Slot(
            String set,
            String tradeId,
            Holder<VillagerTrade> vanilla,
            JsonObject template,
            List<Entity> merchants
    ) {
        String type() {
            return template.get("type").getAsString().substring("villagertradingplus:".length());
        }
    }

    @BeforeAll
    static void load() throws IOException {
        env = VanillaEnv.get();

        try (var list = Files.list(TEMPLATES)) {
            for (Path file : list.toList()) {
                FILES.put(file.getFileName().toString(), JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
    }

    private static List<Entity> villagers() {
        List<Entity> merchants = new ArrayList<>();
        for (ResourceKey<VillagerType> type : List.of(
                VillagerType.DESERT,
                VillagerType.JUNGLE,
                VillagerType.PLAINS,
                VillagerType.SAVANNA,
                VillagerType.SNOW,
                VillagerType.SWAMP,
                VillagerType.TAIGA
        )) {
            merchants.add(env.villager(type, RANDOM::get));
        }
        return merchants;
    }

    private static List<Slot> slots() {
        List<Entity> villagers = villagers();
        List<Entity> wandering = List.of(env.wanderingTrader(RANDOM::get));
        List<Slot> slots = new ArrayList<>();

        env.registries.lookupOrThrow(Registries.TRADE_SET).listElements()
                .sorted(Comparator.comparing(set -> set.key().identifier().toString()))
                .forEach(set -> {
                    String name = set.key().identifier().getPath();
                    String owner = name.substring(0, name.indexOf('/'));
                    String pool = name.substring(name.indexOf('/') + 1);
                    boolean isWandering = owner.equals("wandering_trader");
                    JsonObject file = FILES.get(owner + ".json");
                    assertNotNull(file, "template file for " + owner);

                    JsonArray entries = isWandering
                            ? file.getAsJsonObject("trades").getAsJsonArray(pool.equals("uncommon") ? "rare" : pool)
                            : file.getAsJsonObject("trades").getAsJsonArray(TIERS[Integer.parseInt(pool.substring(6)) - 1]);
                    TradeSet value = set.value();
                    List<Holder<VillagerTrade>> trades = value.trades().stream().toList();
                    assertEquals(trades.size(), entries.size(), "template entries of " + name);

                    for (int i = 0; i < trades.size(); i++) {
                        slots.add(new Slot(
                                name,
                                trades.get(i).unwrapKey().orElseThrow().identifier().getPath(),
                                trades.get(i),
                                entries.get(i).getAsJsonObject(),
                                isWandering ? wandering : villagers
                        ));
                    }
                });

        return slots;
    }

    private static JsonObject describe(MerchantOffer offer) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, env.registries);
        return MerchantOffer.CODEC.encodeStart(ops, offer).getOrThrow().getAsJsonObject();
    }

    private static MerchantOffer vanillaOffer(Slot slot, Entity merchant, RandomSource random) {
        RANDOM.set(random);
        return new VanillaTradeFactory(slot.vanilla(), Optional.empty()).create(env.level, merchant, random);
    }

    private static MerchantOffer templateOffer(TradeFactory factory, Entity merchant, RandomSource random) {
        RANDOM.set(random);
        return factory.create(env.level, merchant, random);
    }

    private static TradeFactory parse(Slot slot) {
        TradeFactory factory = TradeOfferManager.deserializeTrade(slot.template());
        assertNotNull(factory, "the parser rejected the template of " + slot.tradeId());
        return factory;
    }

    @Test
    void everyVanillaTradeHasItsTemplateAtTheSamePlace() {
        List<Slot> slots = slots();
        int pooled = 0;

        for (Slot slot : slots) {
            pooled++;
            assertNotNull(parse(slot), slot.tradeId());
        }

        long vanillaTrades = env.registries.lookupOrThrow(Registries.VILLAGER_TRADE).listElements().count();
        long distinct = slots.stream().map(Slot::tradeId).distinct().count();
        assertEquals(vanillaTrades, distinct, "every vanilla trade is in some set");
        System.out.println("PROOF slots=" + pooled + " distinctTrades=" + distinct);
    }

    @Test
    void fixedTradesGiveTheVanillaOffer() {
        List<String> failures = new ArrayList<>();
        int compared = 0;

        for (Slot slot : slots()) {
            if (isRandom(slot.type())) {
                continue;
            }

            TradeFactory factory = parse(slot);

            for (Entity merchant : slot.merchants()) {
                for (long seed = 1; seed <= 3; seed++) {
                    MerchantOffer vanilla = vanillaOffer(slot, merchant, RandomSource.create(seed));
                    MerchantOffer actual = templateOffer(factory, merchant, RandomSource.create(seed));
                    compared++;

                    if ((vanilla == null) != (actual == null)) {
                        failures.add(slot.tradeId() + ": vanilla " + (vanilla == null ? "gives no offer" : "gives an offer")
                                + ", template " + (actual == null ? "gives no offer" : "gives an offer"));
                        break;
                    }

                    if (vanilla != null && !describe(vanilla).equals(describe(actual))) {
                        failures.add(slot.tradeId() + ":\n  vanilla  " + describe(vanilla) + "\n  template " + describe(actual));
                        break;
                    }
                }
            }
        }

        System.out.println("PROOF fixed offers compared=" + compared);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static boolean isRandom(String type) {
        return switch (type) {
            case "sell_enchanted_tool", "sell_enchanted_book", "sell_dyed_armor", "weighted_pool", "sell_potion", "sell_map" -> true;
            default -> false;
        };
    }

    @Test
    void randomTradesFollowTheVanillaDistribution() {
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (Slot slot : slots()) {
            if (!isRandom(slot.type()) || slot.type().equals("sell_map")) {
                continue;
            }

            TradeFactory factory = parse(slot);
            Entity merchant = slot.merchants().get(0);
            RandomSource vanillaRandom = RandomSource.create(1000L);
            RandomSource actualRandom = RandomSource.create(2000L);

            Map<String, Integer> vanilla = Distributions.count(DRAWS, () -> vanillaOffer(slot, merchant, vanillaRandom), this::views);
            Map<String, Integer> actual = Distributions.count(DRAWS, () -> templateOffer(factory, merchant, actualRandom), this::views);
            String difference = Distributions.compare(vanilla, actual);
            checked++;
            System.out.println("PROOF random " + slot.tradeId() + " none=" + vanilla.getOrDefault("<none>", 0) + "/" + actual.getOrDefault("<none>", 0)
                    + " outcomes=" + vanilla.keySet().stream().filter(k -> k.startsWith("all:")).count() + "/"
                    + actual.keySet().stream().filter(k -> k.startsWith("all:")).count());
            assertTrue(vanilla.getOrDefault("<none>", 0) < DRAWS / 2, slot.tradeId() + " mostly gives no offer");

            if (difference != null) {
                failures.add(slot.tradeId() + ": " + difference);
            }
        }

        System.out.println("PROOF random trades compared=" + checked + " draws each=" + DRAWS);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /// A map offer needs a structure search, so the parts that decide the offer are compared instead of an offer.
    @Test
    void mapTradesCarryTheVanillaFields() throws ReflectiveOperationException {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, env.registries);
        int checked = 0;

        for (Slot slot : slots()) {
            if (!slot.type().equals("sell_map")) {
                continue;
            }

            JsonObject vanilla = VillagerTrade.CODEC.encodeStart(ops, slot.vanilla().value()).getOrThrow().getAsJsonObject();
            TradeFactory parsed = parse(slot);
            TradeFactory conditional = parsed;

            if (parsed instanceof ConditionalTradeFactory withCondition) {
                parsed = withCondition.delegate();
            }

            if (parsed instanceof RegistryRebindFactory rebind) {
                parsed = rebind.delegate();
            }

            Object factory = parsed;
            ItemStack currency = field(factory, "currency", ItemStack.class);
            ItemStack buy = field(factory, "buy", ItemStack.class);
            ItemStackTemplate gives = field(factory, "gives", ItemStackTemplate.class);
            @SuppressWarnings("unchecked")
            Holder<LootItemFunction> modifier = (Holder<LootItemFunction>) field(factory, "modifier", Object.class);
            String id = slot.tradeId();

            assertEquals(vanilla.getAsJsonObject("wants").get("id").getAsString(), BuiltInRegistries.ITEM.getKey(currency.getItem()).toString(), id);
            assertEquals(count(vanilla.getAsJsonObject("wants")), currency.getCount(), id);
            assertEquals(vanilla.getAsJsonObject("additional_wants").get("id").getAsString(), BuiltInRegistries.ITEM.getKey(buy.getItem()).toString(), id);
            assertEquals(count(vanilla.getAsJsonObject("additional_wants")), buy.getCount(), id);
            assertEquals(vanilla.get("gives"), ItemStackTemplate.CODEC.encodeStart(ops, gives).getOrThrow(), id);
            assertEquals(vanilla.get("given_item_modifier"), LootItemFunctions.CODEC.encodeStart(ops, modifier).getOrThrow(), id);
            assertEquals(vanilla.has("max_uses") ? vanilla.get("max_uses").getAsInt() : 4, (int) field(factory, "maxUses", Integer.class), id);
            assertEquals(vanilla.has("xp") ? vanilla.get("xp").getAsInt() : 1, (int) field(factory, "experience", Integer.class), id);
            assertEquals(vanilla.has("reputation_discount") ? vanilla.get("reputation_discount").getAsFloat() : 0.0f, (float) field(factory, "multiplier", Float.class), id);

            for (Entity merchant : slot.merchants()) {
                boolean offered = new VanillaTradeFactory(slot.vanilla(), Optional.empty()).offeredTo(env.level, merchant, RandomSource.create(1L));
                boolean templated = !(conditional instanceof ConditionalTradeFactory withCondition)
                        || withCondition.condition().test(merchant);
                assertEquals(offered, templated, id + " merchant predicate");
            }

            checked++;
        }

        assertEquals(10, checked);
        System.out.println("PROOF map trades compared=" + checked);
    }

    private static int count(JsonObject cost) {
        return cost.has("count") ? cost.get("count").getAsInt() : 1;
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    private List<String> views(MerchantOffer offer) {
        List<String> keys = new ArrayList<>();
        JsonObject whole = describe(offer);
        keys.add("all:" + canonical(whole));

        ItemEnchantments enchantments = offer.getResult().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = offer.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<String> pairs = new ArrayList<>();

        for (ItemEnchantments group : List.of(enchantments, stored)) {
            for (var entry : group.entrySet()) {
                String pair = entry.getKey().getRegisteredName() + "@" + entry.getIntValue();
                pairs.add(pair);
                keys.add("enchantment:" + pair);
            }
        }

        pairs.sort(String::compareTo);
        keys.add("enchantments:" + pairs);
        keys.add("costA:" + offer.getItemCostA().count());
        keys.add("costAandEnchantments:" + offer.getItemCostA().count() + pairs);

        if (offer.getResult().has(DataComponents.DYED_COLOR)) {
            keys.add("color:" + offer.getResult().get(DataComponents.DYED_COLOR).rgb());
        }

        return keys;
    }

    /// Enchantment maps list their entries in hash order, so equal offers need one key order to compare as text.
    private static String canonical(JsonElement element) {
        if (element.isJsonObject()) {
            StringBuilder text = new StringBuilder("{");
            element.getAsJsonObject().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> text.append(entry.getKey()).append("=").append(canonical(entry.getValue())).append(","));
            return text.append("}").toString();
        }

        if (element.isJsonArray()) {
            StringBuilder text = new StringBuilder("[");
            element.getAsJsonArray().forEach(child -> text.append(canonical(child)).append(","));
            return text.append("]").toString();
        }

        return element.toString();
    }
}
