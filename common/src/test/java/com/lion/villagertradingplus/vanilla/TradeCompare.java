package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.ComponentChanges;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class TradeCompare {

    private TradeCompare() {}

    static String offerDiff(TradeOffer a, TradeOffer b) {
        if (a == null || b == null) {
            return a == b ? null : (a == null ? "vanilla gave no offer, the template did" : "the template gave no offer, vanilla did");
        }

        String diff = tradedDiff("first buy", a.getFirstBuyItem(), b.getFirstBuyItem());

        if (diff != null) {
            return diff;
        }

        Optional<TradedItem> secondA = a.getSecondBuyItem();
        Optional<TradedItem> secondB = b.getSecondBuyItem();

        if (secondA.isPresent() != secondB.isPresent()) {
            return "second buy: vanilla " + secondA + ", template " + secondB;
        }

        if (secondA.isPresent()) {
            diff = tradedDiff("second buy", secondA.get(), secondB.get());

            if (diff != null) {
                return diff;
            }
        }

        if (!ItemStack.areEqual(a.getSellItem(), b.getSellItem())) {
            return "sell: vanilla " + a.getSellItem() + " " + a.getSellItem().getComponentChanges()
                    + ", template " + b.getSellItem() + " " + b.getSellItem().getComponentChanges();
        }

        if (a.getUses() != b.getUses()) {
            return "uses " + a.getUses() + " vs " + b.getUses();
        }

        if (a.getMaxUses() != b.getMaxUses()) {
            return "max uses " + a.getMaxUses() + " vs " + b.getMaxUses();
        }

        if (a.getMerchantExperience() != b.getMerchantExperience()) {
            return "villager experience " + a.getMerchantExperience() + " vs " + b.getMerchantExperience();
        }

        if (Float.compare(a.getPriceMultiplier(), b.getPriceMultiplier()) != 0) {
            return "price multiplier " + a.getPriceMultiplier() + " vs " + b.getPriceMultiplier();
        }

        if (a.getDemandBonus() != b.getDemandBonus()) {
            return "demand " + a.getDemandBonus() + " vs " + b.getDemandBonus();
        }

        if (a.getSpecialPrice() != b.getSpecialPrice()) {
            return "special price " + a.getSpecialPrice() + " vs " + b.getSpecialPrice();
        }

        if (a.shouldRewardPlayerExperience() != b.shouldRewardPlayerExperience()) {
            return "player experience reward differs";
        }

        return null;
    }

    private static String tradedDiff(String slot, TradedItem a, TradedItem b) {
        if (!a.item().matches(b.item())) {
            return slot + " item: " + a.item().getIdAsString() + " vs " + b.item().getIdAsString();
        }

        if (a.count() != b.count()) {
            return slot + " count: " + a.count() + " vs " + b.count();
        }

        String changesA = changes(a.components().toChanges());
        String changesB = changes(b.components().toChanges());

        if (!changesA.equals(changesB)) {
            return slot + " predicate: vanilla " + changesA + ", template " + changesB;
        }

        return null;
    }

    private static String changes(ComponentChanges changes) {
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, VanillaRuntime.get().registries);
        return ComponentChanges.CODEC.encodeStart(ops, changes).getOrThrow(IllegalStateException::new).toString();
    }

    static List<String> exact(
            String label,
            TradeOffers.Factory vanilla,
            TradeOffers.Factory template,
            List<? extends Entity> merchants,
            int seeds
    ) {
        List<String> failures = new ArrayList<>();

        for (Entity merchant : merchants) {
            for (int seed = 0; seed < seeds && failures.size() < 3; seed++) {
                try {
                    Random vanillaRandom = Random.create(seed);
                    Random templateRandom = Random.create(seed);
                    TradeOffer expected = TradeApi.create(vanilla, merchant, vanillaRandom);
                    TradeOffer actual = TradeApi.create(template, merchant, templateRandom);
                    String diff = offerDiff(expected, actual);

                    if (diff == null && vanillaRandom.nextInt() != templateRandom.nextInt()) {
                        diff = "the template used the random generator differently";
                    }

                    if (diff != null) {
                        failures.add(label + " merchant " + describe(merchant) + " seed " + seed + ": " + diff);
                    }
                } catch (RuntimeException e) {
                    failures.add(label + " merchant " + describe(merchant) + " seed " + seed + ": " + e);
                }
            }
        }

        return failures;
    }

    static String describe(Entity merchant) {
        if (merchant instanceof net.minecraft.village.VillagerDataContainer container) {
            return container.getVillagerData().type().getIdAsString();
        }

        return "wanderer";
    }

    static List<TradeOffer> draw(TradeOffers.Factory[] pool, Entity merchant, int count, long seed) {
        List<TradeOffers.Factory> list = new ArrayList<>(List.of(pool));
        List<TradeOffer> offers = new ArrayList<>();
        Random random = Random.create(seed);
        int made = 0;

        while (made < count && !list.isEmpty()) {
            TradeOffers.Factory factory = list.remove(random.nextInt(list.size()));
            TradeOffer offer = TradeApi.create(factory, merchant, random);

            if (offer != null) {
                offers.add(offer);
                made++;
            }
        }

        return offers;
    }
}
