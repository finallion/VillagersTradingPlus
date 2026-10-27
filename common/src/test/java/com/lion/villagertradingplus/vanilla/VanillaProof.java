package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.tradeoffers.ConditionalTradeFactory;
import com.lion.villagertradingplus.tradeoffers.RegistryRebindFactory;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import com.lion.villagertradingplus.tradeoffers.WanderingTraderTradeLoader;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public final class VanillaProof {

    private static final int EXACT_SEEDS = 40;
    private static final int RANDOM_SEEDS = 300;
    private static final int BOOK_SAMPLES = 300_000;

    private final List<String[]> results = new ArrayList<>();
    private final VanillaRuntime runtime = VanillaRuntime.get();
    private final List<Entity> merchants = new ArrayList<>();

    private Map<String, Map<Integer, TradeOffers.Factory[]>> templates;
    private List<Pair<TradeOffers.Factory[], Integer>> wanderingTemplates;

    private VanillaProof() {
        for (RegistryKey<VillagerType> type : TemplateWriter.TYPES) {
            merchants.add(runtime.villager(type));
        }

        merchants.add(runtime.wanderer());
    }

    public static Map<String, String> templates() {
        return new TemplateWriter().render();
    }

    public static List<String[]> run(String folder) throws IOException {
        VanillaTables.professions();
        VanillaProof proof = new VanillaProof();
        proof.load(Path.of(folder));
        proof.checks();
        return proof.results;
    }

    private void load(Path folder) throws IOException {
        TradeOfferManager.registerTradeOffers();
        TradeOfferRegistryLoader.begin();
        JsonObject wandering = null;

        try (var files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

                if (name.equals("wandering_trader")) {
                    wandering = json;
                } else {
                    TradeOfferManager.deserializeJson(Identifier.of("vtp_test", name), json);
                }
            }
        }

        templates = new TreeMap<>();

        for (Map.Entry<RegistryKey<VillagerProfession>, Int2ObjectOpenHashMap<TradeOffers.Factory[]>> entry
                : TradeOfferRegistryLoader.getRegistryForLoading().entrySet()) {
            Map<Integer, TradeOffers.Factory[]> levels = new HashMap<>();
            entry.getValue().forEach((level, pool) -> levels.put(level, pool));
            templates.put(entry.getKey().getValue().getPath(), levels);
        }

        if (wandering != null) {
            WanderingTraderTradeLoader.begin();
            TradeOfferManager.deserializeWanderingTraderJson(Identifier.of("vtp_test", "wandering_trader"), wandering);
            WanderingTraderTradeLoader.apply();
            wanderingTemplates = new ArrayList<>(WanderingTraderTradeLoader.pools());
        }
    }

    private void checks() {
        check("the vanilla tables read as expected", this::vanillaSanity);
        check("every profession has the vanilla number of trades on every tier", this::tierCounts);
        check("every listing makes the same offers as vanilla", this::listings);
        check("enchanted book listings give the same books at the same odds", this::books);
        check("map listings have the vanilla structure, name, marker and price", this::maps);
        check("drawing from a level gives the same offers as vanilla", this::levelDraws);
        check("the wandering trader has the three vanilla pools", this::wanderingPools);
        check("every wandering trader listing makes the same offers as vanilla", this::wanderingListings);
        check("drawing from the wandering trader gives the same offers as vanilla", this::wanderingDraws);
    }

    private void check(String name, Supplier<List<String>> body) {
        List<String> failures;

        try {
            failures = body.get();
        } catch (Throwable e) {
            failures = List.of("threw " + e);
        }

        results.add(new String[] {name, String.join("\n", failures.stream().limit(12).toList())});
    }

    private List<String> vanillaSanity() {
        List<String> failures = new ArrayList<>();
        int total = VanillaTables.professions().values().stream().mapToInt(VanillaTables::count).sum();

        if (VanillaTables.professions().size() != 13 || total != 297) {
            failures.add("expected 13 professions and 297 listings, found "
                    + VanillaTables.professions().size() + " and " + total);
        }

        List<Pair<TradeOffers.Factory[], Integer>> wandering = VanillaTables.wandering();
        int[] sizes = {6, 15, 73};
        int[] rolls = {2, 2, 5};

        for (int pool = 0; pool < 3; pool++) {
            if (wandering.get(pool).getLeft().length != sizes[pool] || wandering.get(pool).getRight() != rolls[pool]) {
                failures.add("wandering pool " + pool + " is not " + sizes[pool] + " listings with " + rolls[pool] + " rolls");
            }
        }

        return failures;
    }

    private List<String> tierCounts() {
        List<String> failures = new ArrayList<>();

        VanillaTables.professions().forEach((profession, levels) -> {
            Map<Integer, TradeOffers.Factory[]> actual = templates.get(profession);

            if (actual == null) {
                failures.add(profession + " has no template");
                return;
            }

            for (int level = 1; level <= 5; level++) {
                int expected = levels.get(level).length;
                int found = actual.containsKey(level) ? actual.get(level).length : 0;

                if (expected != found) {
                    failures.add(profession + " " + VanillaTables.TIERS[level - 1] + ": vanilla " + expected + " trades, template " + found);
                }
            }
        });

        return failures;
    }

    private List<String> listings() {
        List<String> failures = new ArrayList<>();

        VanillaTables.professions().forEach((profession, levels) -> levels.forEach((level, pool) -> {
            TradeOffers.Factory[] actual = templates.getOrDefault(profession, Map.of()).get(level);

            if (actual == null || actual.length != pool.length) {
                return;
            }

            for (int index = 0; index < pool.length; index++) {
                String kind = effectiveKind(pool[index]);
                String label = profession + " " + VanillaTables.TIERS[level - 1] + " #" + index + " (" + kind + ")";

                if (kind.equals("EnchantBookFactory") || kind.equals("SellMapFactory")) {
                    continue;
                }

                int seeds = isRandom(kind) ? RANDOM_SEEDS : EXACT_SEEDS;
                failures.addAll(TradeCompare.exact(label, pool[index], actual[index], merchants, seeds));
            }
        }));

        return failures;
    }

    private List<String> books() {
        List<String> failures = new ArrayList<>();
        Entity merchant = merchants.get(0);

        VanillaTables.professions().forEach((profession, levels) -> levels.forEach((level, pool) -> {
            TradeOffers.Factory[] actual = templates.getOrDefault(profession, Map.of()).get(level);

            for (int index = 0; actual != null && index < pool.length && index < actual.length; index++) {
                if (effectiveKind(pool[index]).equals("EnchantBookFactory")) {
                    failures.addAll(bookDistribution(
                            profession + " " + VanillaTables.TIERS[level - 1] + " #" + index,
                            pool[index],
                            actual[index],
                            merchant
                    ));
                }
            }
        }));

        return failures;
    }

    private List<String> bookDistribution(String label, TradeOffers.Factory vanilla, TradeOffers.Factory template, Entity merchant) {
        Map<String, double[]> expected = sampleBooks(vanilla, merchant);
        Map<String, double[]> actual = sampleBooks(template, merchant);
        List<String> failures = new ArrayList<>();

        if (!expected.keySet().equals(actual.keySet())) {
            failures.add(label + ": the books differ, vanilla " + expected.keySet().size() + ", template " + actual.keySet().size());
            return failures;
        }

        for (String key : expected.keySet()) {
            double[] a = expected.get(key);
            double[] b = actual.get(key);
            double pa = a[0] / BOOK_SAMPLES;
            double pb = b[0] / BOOK_SAMPLES;
            double sigma = Math.sqrt(Math.max(pa, 1e-9) * 2.0 / BOOK_SAMPLES);

            if (Math.abs(pa - pb) > 6 * sigma + 1e-4) {
                failures.add(label + " " + key + ": odds " + pa + " vs " + pb);
            }

            double meanA = a[1] / a[0];
            double meanB = b[1] / b[0];
            double varA = a[2] / a[0] - meanA * meanA;
            double varB = b[2] / b[0] - meanB * meanB;
            double tolerance = 6 * Math.sqrt(varA / a[0] + varB / b[0]) + 1e-9;

            if (Math.abs(meanA - meanB) > tolerance) {
                failures.add(label + " " + key + ": mean price " + meanA + " vs " + meanB);
            }

            if (a[0] > 2000 && (a[3] != b[3] || a[4] != b[4])) {
                failures.add(label + " " + key + ": price range " + a[3] + ".." + a[4] + " vs " + b[3] + ".." + b[4]);
            }
        }

        return failures;
    }

    private Map<String, double[]> sampleBooks(TradeOffers.Factory factory, Entity merchant) {
        Map<String, double[]> table = new TreeMap<>();

        for (int seed = 0; seed < BOOK_SAMPLES; seed++) {
            TradeOffer offer = TradeApi.create(factory, merchant, Random.create(seed));
            var stored = offer.getSellItem().get(DataComponentTypes.STORED_ENCHANTMENTS);
            var entry = stored.getEnchantmentEntries().iterator().next();
            String key = entry.getKey().getIdAsString() + " " + entry.getIntValue();
            int price = offer.getFirstBuyItem().count();
            double[] row = table.computeIfAbsent(key, k -> new double[] {0, 0, 0, Double.MAX_VALUE, 0});
            row[0]++;
            row[1] += price;
            row[2] += (double) price * price;
            row[3] = Math.min(row[3], price);
            row[4] = Math.max(row[4], price);
        }

        return table;
    }

    private List<String> maps() {
        List<String> failures = new ArrayList<>();

        VanillaTables.professions().forEach((profession, levels) -> levels.forEach((level, pool) -> {
            TradeOffers.Factory[] actual = templates.getOrDefault(profession, Map.of()).get(level);

            for (int index = 0; actual != null && index < pool.length && index < actual.length; index++) {
                if (effectiveKind(pool[index]).equals("SellMapFactory")) {
                    failures.addAll(mapFields(
                            profession + " " + VanillaTables.TIERS[level - 1] + " #" + index,
                            pool[index],
                            actual[index]
                    ));
                }
            }
        }));

        return failures;
    }

    private List<String> mapFields(String label, TradeOffers.Factory vanilla, TradeOffers.Factory template) {
        List<String> failures = new ArrayList<>();
        TradeOffers.Factory inner = vanilla;

        if (VanillaTables.kind(vanilla).equals("TypedWrapperFactory")) {
            Map<RegistryKey<VillagerType>, TradeOffers.Factory> byType = VanillaTables.field(vanilla, "typeToFactory");
            inner = byType.values().iterator().next();

            for (Entity merchant : merchants) {
                boolean expected = merchant instanceof net.minecraft.village.VillagerDataContainer container
                        && byType.containsKey(container.getVillagerData().type().getKey().orElseThrow());
                boolean actual = !(template instanceof ConditionalTradeFactory conditional)
                        ? true
                        : conditional.condition().test(merchant);

                if (expected != actual) {
                    failures.add(label + " merchant " + TradeCompare.describe(merchant) + ": vanilla " + expected + ", template " + actual);
                }
            }
        }

        TradeOffers.Factory bare = unwrap(template);

        if (!VanillaTables.<Object>field(inner, "structure").equals(VanillaTables.field(bare, "structure"))) {
            failures.add(label + ": structure tag differs");
        }

        if (!VanillaTables.<String>field(inner, "nameKey").equals(VanillaTables.field(bare, "nameKey"))) {
            failures.add(label + ": name differs");
        }

        net.minecraft.registry.entry.RegistryEntry<?> decoration = VanillaTables.field(inner, "decoration");

        if (!decoration.getKey().equals(VanillaTables.<net.minecraft.registry.entry.RegistryEntry<?>>field(bare, "decoration").getKey())) {
            failures.add(label + ": marker differs");
        }

        net.minecraft.item.ItemStack currency = VanillaTables.field(bare, "currency");
        net.minecraft.item.ItemStack buy = VanillaTables.field(bare, "buy");

        if (currency.getItem() != Items.EMERALD || currency.getCount() != VanillaTables.<Integer>field(inner, "price")) {
            failures.add(label + ": price differs");
        }

        if (buy.getItem() != Items.COMPASS || buy.getCount() != 1) {
            failures.add(label + ": second item is not one compass");
        }

        if (!VanillaTables.<Integer>field(inner, "maxUses").equals(VanillaTables.field(bare, "maxUses"))) {
            failures.add(label + ": max uses differ");
        }

        if (!VanillaTables.<Integer>field(inner, "experience").equals(VanillaTables.field(bare, "experience"))) {
            failures.add(label + ": experience differs");
        }

        if (Float.compare(VanillaTables.<Float>field(bare, "multiplier"), 0.2f) != 0) {
            failures.add(label + ": price multiplier is not 0.2");
        }

        return failures;
    }

    private List<String> levelDraws() {
        List<String> failures = new ArrayList<>();

        VanillaTables.professions().forEach((profession, levels) -> levels.forEach((level, pool) -> {
            TradeOffers.Factory[] actual = templates.getOrDefault(profession, Map.of()).get(level);

            if (actual == null || actual.length != pool.length || hasRandomWorldKind(pool)) {
                return;
            }

            for (Entity merchant : merchants) {
                for (long seed = 0; seed < 60; seed++) {
                    List<TradeOffer> expected = TradeCompare.draw(pool, merchant, 2, seed);
                    List<TradeOffer> found = TradeCompare.draw(actual, merchant, 2, seed);
                    String diff = listDiff(expected, found);

                    if (diff != null && failures.size() < 6) {
                        failures.add(profession + " " + VanillaTables.TIERS[level - 1] + " merchant "
                                + TradeCompare.describe(merchant) + " seed " + seed + ": " + diff);
                    }
                }
            }
        }));

        return failures;
    }

    private List<String> wanderingPools() {
        List<String> failures = new ArrayList<>();

        if (wanderingTemplates == null || wanderingTemplates.size() != 3) {
            return List.of("the merged wandering trader has " + (wanderingTemplates == null ? 0 : wanderingTemplates.size()) + " pools");
        }

        for (int pool = 0; pool < 3; pool++) {
            Pair<TradeOffers.Factory[], Integer> vanilla = VanillaTables.wandering().get(pool);
            Pair<TradeOffers.Factory[], Integer> template = wanderingTemplates.get(pool);

            if (vanilla.getLeft().length != template.getLeft().length) {
                failures.add(VanillaTables.WANDERING_POOLS[pool] + ": vanilla " + vanilla.getLeft().length
                        + " trades, template " + template.getLeft().length);
            }

            if (!vanilla.getRight().equals(template.getRight())) {
                failures.add(VanillaTables.WANDERING_POOLS[pool] + ": vanilla draws " + vanilla.getRight()
                        + ", template draws " + template.getRight());
            }
        }

        return failures;
    }

    private List<String> wanderingListings() {
        List<String> failures = new ArrayList<>();

        if (wanderingTemplates == null || wanderingTemplates.size() != 3) {
            return failures;
        }

        for (int pool = 0; pool < 3; pool++) {
            TradeOffers.Factory[] vanilla = VanillaTables.wandering().get(pool).getLeft();
            TradeOffers.Factory[] template = wanderingTemplates.get(pool).getLeft();

            for (int index = 0; index < Math.min(vanilla.length, template.length); index++) {
                String kind = effectiveKind(vanilla[index]);
                String label = VanillaTables.WANDERING_POOLS[pool] + " #" + index + " (" + kind + ")";
                failures.addAll(TradeCompare.exact(label, vanilla[index], template[index], merchants, isRandom(kind) ? RANDOM_SEEDS : EXACT_SEEDS));
            }
        }

        return failures;
    }

    private List<String> wanderingDraws() {
        List<String> failures = new ArrayList<>();

        if (wanderingTemplates == null || wanderingTemplates.size() != 3) {
            return failures;
        }

        Entity wanderer = merchants.get(merchants.size() - 1);

        for (int pool = 0; pool < 3; pool++) {
            TradeOffers.Factory[] vanilla = VanillaTables.wandering().get(pool).getLeft();
            TradeOffers.Factory[] template = wanderingTemplates.get(pool).getLeft();

            if (vanilla.length != template.length) {
                continue;
            }

            int count = VanillaTables.wandering().get(pool).getRight();

            for (long seed = 0; seed < 100; seed++) {
                String diff = listDiff(
                        TradeCompare.draw(vanilla, wanderer, count, seed),
                        TradeCompare.draw(template, wanderer, count, seed)
                );

                if (diff != null && failures.size() < 6) {
                    failures.add(VanillaTables.WANDERING_POOLS[pool] + " seed " + seed + ": " + diff);
                }
            }
        }

        return failures;
    }

    private static String listDiff(List<TradeOffer> expected, List<TradeOffer> actual) {
        if (expected.size() != actual.size()) {
            return "vanilla " + expected.size() + " offers, template " + actual.size();
        }

        for (int i = 0; i < expected.size(); i++) {
            String diff = TradeCompare.offerDiff(expected.get(i), actual.get(i));

            if (diff != null) {
                return "offer " + i + ": " + diff;
            }
        }

        return null;
    }

    static String effectiveKind(TradeOffers.Factory factory) {
        if (VanillaTables.kind(factory).equals("TypedWrapperFactory")) {
            Map<RegistryKey<VillagerType>, TradeOffers.Factory> byType = VanillaTables.field(factory, "typeToFactory");
            return VanillaTables.kind(byType.values().iterator().next());
        }

        return VanillaTables.kind(factory);
    }

    private static boolean isRandom(String kind) {
        return kind.equals("SellEnchantedToolFactory")
                || kind.equals("SellDyedArmorFactory")
                || kind.equals("SellPotionHoldingItemFactory")
                || kind.equals("EnchantBookFactory");
    }

    private static boolean hasRandomWorldKind(TradeOffers.Factory[] pool) {
        for (TradeOffers.Factory factory : pool) {
            String kind = effectiveKind(factory);

            if (kind.equals("EnchantBookFactory") || kind.equals("SellMapFactory")) {
                return true;
            }
        }

        return false;
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
}
