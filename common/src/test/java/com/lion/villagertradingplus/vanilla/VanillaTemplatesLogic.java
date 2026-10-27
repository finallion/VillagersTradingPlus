package com.lion.villagertradingplus.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

public final class VanillaTemplatesLogic {

    private static final int RANDOM_SEEDS = 5000;
    private static final int PLAIN_SEEDS = 4;

    private static boolean booted;

    private VanillaTemplatesLogic() {}

    private static synchronized void boot() {
        if (!booted) {
            SharedConstants.createGameVersion();
            Bootstrap.initialize();
            TradeOfferManager.registerTradeOffers();
            booted = true;
        }

        try {
            TestWorld.get(false);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path templateDir() {
        return Path.of(System.getProperty("vtp.rootDir"))
                .resolve("common/src/main/resources/data/villagertradingplus/vanilla_trades");
    }

    private static Map<String, String> generate() throws Exception {
        Map<String, String> files = new TreeMap<>();

        for (String profession : VanillaTradeReader.PROFESSIONS) {
            files.put(profession + ".json", TemplateJson.render(VanillaTradeReader.professionFile(profession)));
        }

        files.put("wandering_trader.json", TemplateJson.render(VanillaTradeReader.wanderingFile()));
        return files;
    }

    public static void generator() throws Exception {
        boot();
        Map<String, String> files = generate();
        Path dir = templateDir();

        if (Boolean.parseBoolean(System.getProperty("vtp.regenerate", "false"))) {
            Files.createDirectories(dir);

            for (Map.Entry<String, String> file : files.entrySet()) {
                Files.writeString(dir.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
            }

            return;
        }

        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = dir.resolve(file.getKey());
            assertTrue(Files.exists(path), "missing template " + path);
            assertEquals(file.getValue(), Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n"), file.getKey());
        }

        try (var listing = Files.list(dir)) {
            assertEquals(files.size(), listing.count(), "unexpected extra file in the template folder");
        }
    }

    private static JsonObject read(String file) throws IOException {
        return JsonParser.parseString(Files.readString(templateDir().resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String describe(TradeOffer offer) throws Exception {
        if (offer == null) {
            return "null";
        }

        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, TestWorld.get(false).registries);
        return TradeOffer.CODEC.encodeStart(ops, offer).getOrThrow().toString();
    }

    private static boolean randomClass(String name) {
        return name.equals("EnchantBookFactory")
                || name.equals("SellEnchantedToolFactory")
                || name.equals("SellDyedArmorFactory")
                || name.equals("SellPotionHoldingItemFactory");
    }

    private static int compare(String label, TradeOffers.Factory vanilla, TradeOffers.Factory template) throws Exception {
        String name = vanilla.getClass().getSimpleName();
        int compared = 0;

        if (name.equals("SellMapFactory")) {
            compareMap(label, vanilla, template);
            return 1;
        }

        int seeds = randomClass(name) ? RANDOM_SEEDS : PLAIN_SEEDS;

        for (VillagerType type : Registries.VILLAGER_TYPE) {
            boolean typed = name.equals("TypeAwareBuyForOneEmeraldFactory");

            if (!typed && type != VillagerType.PLAINS) {
                continue;
            }

            for (int seed = 0; seed < seeds; seed++) {
                TradeOffer expected = VanillaTradeReader.create(vanilla, type, seed);
                TradeOffer actual = VanillaTradeReader.create(template, type, seed);
                assertEquals(describe(expected), describe(actual), label + " seed " + seed + " type " + type);
                compared++;
            }
        }

        return compared;
    }

    private static void compareMap(String label, TradeOffers.Factory vanilla, TradeOffers.Factory template) throws Exception {
        assertEquals(
                VanillaTradeReader.field(vanilla, "price"),
                ((net.minecraft.item.ItemStack) VanillaTradeReader.field(template, "currency")).getCount(),
                label + " price"
        );
        assertEquals(
                "minecraft:emerald",
                Registries.ITEM.getId(((net.minecraft.item.ItemStack) VanillaTradeReader.field(template, "currency")).getItem()).toString(),
                label + " currency"
        );
        net.minecraft.item.ItemStack compass = (net.minecraft.item.ItemStack) VanillaTradeReader.field(template, "buy");
        assertEquals("minecraft:compass", Registries.ITEM.getId(compass.getItem()).toString(), label + " second slot");
        assertEquals(1, compass.getCount(), label + " second slot count");
        assertEquals(VanillaTradeReader.field(vanilla, "structure"), VanillaTradeReader.field(template, "structure"), label + " structure");
        assertEquals(VanillaTradeReader.field(vanilla, "nameKey"), VanillaTradeReader.field(template, "nameKey"), label + " name");
        assertEquals(VanillaTradeReader.field(vanilla, "decoration"), VanillaTradeReader.field(template, "decoration"), label + " icon");
        assertEquals(VanillaTradeReader.field(vanilla, "maxUses"), VanillaTradeReader.field(template, "maxUses"), label + " max uses");
        assertEquals(VanillaTradeReader.field(vanilla, "experience"), VanillaTradeReader.field(template, "experience"), label + " xp");
        assertEquals(VanillaTradeReader.MAP_MULTIPLIER, (float) VanillaTradeReader.field(template, "multiplier"), label + " multiplier");
    }

    public static void proof() throws Exception {
        boot();
        Map<String, Integer> perClass = new TreeMap<>();
        int listings = 0;
        int offers = 0;

        TradeOfferRegistryLoader.begin();

        for (String profession : VanillaTradeReader.PROFESSIONS) {
            TradeOfferManager.deserializeJson(Identifier.of("villagertradingplus", "vanilla_trades/" + profession), read(profession + ".json"));
        }

        Map<VillagerProfession, Int2ObjectOpenHashMap<TradeOffers.Factory[]>> loaded = TradeOfferRegistryLoader.getRegistryForLoading();

        for (String profession : VanillaTradeReader.PROFESSIONS) {
            Int2ObjectOpenHashMap<TradeOffers.Factory[]> levels = loaded.get(VanillaTradeReader.profession(profession));
            assertNotNull(levels, profession + " was not loaded");
            assertEquals(5, levels.size(), profession + " level count");

            for (int level = 1; level <= 5; level++) {
                TradeOffers.Factory[] vanilla = VanillaTradeReader.pool(profession, level);
                TradeOffers.Factory[] template = levels.get(level);
                assertNotNull(template, profession + " level " + level);
                assertEquals(vanilla.length, template.length, profession + " level " + level + " offer count");

                for (int i = 0; i < vanilla.length; i++) {
                    String name = vanilla[i].getClass().getSimpleName();
                    offers += compare(profession + " L" + level + " #" + i + " " + name, vanilla[i], template[i]);
                    perClass.merge(name, 1, Integer::sum);
                    listings++;
                }
            }
        }

        JsonObject wanderingFile = read("wandering_trader.json");
        assertTrue(wanderingFile.get("replace").getAsBoolean(), "wandering file replaces vanilla");
        JsonObject tiers = wanderingFile.getAsJsonObject("trades");
        assertEquals(2, tiers.size());

        for (int level = 1; level <= 2; level++) {
            JsonArray array = tiers.getAsJsonArray(level == 1 ? "common" : "rare");
            TradeOffers.Factory[] vanilla = VanillaTradeReader.wandering(level);
            assertEquals(vanilla.length, array.size(), "wandering tier " + level + " offer count");

            for (int i = 0; i < vanilla.length; i++) {
                TradeOffers.Factory template = TradeOfferManager.deserializeTrade(array.get(i).getAsJsonObject());
                assertNotNull(template, "wandering tier " + level + " #" + i + " did not parse");
                String name = vanilla[i].getClass().getSimpleName();
                offers += compare("wandering L" + level + " #" + i + " " + name, vanilla[i], template);
                perClass.merge(name, 1, Integer::sum);
                listings++;
            }
        }

        assertEquals(361, listings, "291 villager and 70 wandering listings");
        System.out.println("VTP proof: " + listings + " listings, " + offers + " compared offers, per class " + perClass);
    }

    public static void distributions() throws Exception {
        boot();
        Map<String, Map<String, Integer>> vanillaSets = new HashMap<>();
        Map<String, Map<String, Integer>> templateSets = new HashMap<>();
        List<String> files = new ArrayList<>(VanillaTradeReader.PROFESSIONS);

        for (String profession : files) {
            JsonObject root = read(profession + ".json");

            for (int level = 1; level <= 5; level++) {
                JsonArray array = root.getAsJsonObject("trades").getAsJsonArray(VanillaTradeReader.TIERS[level - 1]);
                TradeOffers.Factory[] vanilla = VanillaTradeReader.pool(profession, level);

                for (int i = 0; i < vanilla.length; i++) {
                    String name = vanilla[i].getClass().getSimpleName();

                    if (!randomClass(name)) {
                        continue;
                    }

                    TradeOffers.Factory template = TradeOfferManager.deserializeTrade(array.get(i).getAsJsonObject());
                    String key = profession + " L" + level + " #" + i + " " + name;
                    Map<String, Integer> v = new TreeMap<>();
                    Map<String, Integer> t = new TreeMap<>();

                    for (int seed = 0; seed < 20000; seed++) {
                        v.merge(describe(vanilla[i].create(new StubVillager(VillagerType.PLAINS, TestWorld.get(false).world), Random.create(seed))), 1, Integer::sum);
                        t.merge(describe(template.create(new StubVillager(VillagerType.PLAINS, TestWorld.get(false).world), Random.create(seed))), 1, Integer::sum);
                    }

                    vanillaSets.put(key, v);
                    templateSets.put(key, t);
                }
            }
        }

        assertTrue(vanillaSets.size() >= 27, "random listings found: " + vanillaSets.size());

        for (String key : vanillaSets.keySet()) {
            assertEquals(vanillaSets.get(key), templateSets.get(key), key + " result frequencies");
        }

        int distinct = vanillaSets.values().stream().mapToInt(Map::size).sum();
        System.out.println("VTP distributions: " + vanillaSets.size() + " random listings, 20000 seeds each, " + distinct + " distinct results, identical frequencies");
    }

    public static void shape() throws Exception {
        boot();

        for (String profession : VanillaTradeReader.PROFESSIONS) {
            JsonObject root = read(profession + ".json");
            assertEquals("minecraft:" + profession, root.get("profession").getAsString());
            JsonObject trades = root.getAsJsonObject("trades");

            for (String tier : VanillaTradeReader.TIERS) {
                JsonElement array = trades.get(tier);
                assertNotNull(array, profession + " tier " + tier);

                for (JsonElement trade : array.getAsJsonArray()) {
                    JsonObject object = trade.getAsJsonObject();
                    assertTrue(object.has("max_uses"), profession + " " + tier + " max_uses");
                    assertTrue(object.has("villager_experience"), profession + " " + tier + " villager_experience");
                    assertTrue(object.has("price_multiplier"), profession + " " + tier + " price_multiplier");
                }
            }
        }
    }
}
