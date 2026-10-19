package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

public class JsonSellEnchantedBookFromListTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack currency = getItemStackFromJsonWithoutCount(JsonFields.requireObject(json, "sell_enchanted_book_from_list trade", "currency"));

        Optional<Registry<Enchantment>> registry = DatapackRegistries.registry(Registries.ENCHANTMENT);

        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : JsonFields.requireArray(json, "sell_enchanted_book_from_list trade", "enchantments")) {
            JsonObject obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            Identifier identifier = Identifier.tryParse(id);

            Optional<Holder.Reference<Enchantment>> enchantment = identifier == null
                    ? Optional.empty()
                    : registry.flatMap(r -> r.get(ResourceKey.create(Registries.ENCHANTMENT, identifier)));

            if (enchantment.isEmpty()) {
                VillagerTradingPlus.LOGGER.error("Unknown enchantment in sell_enchanted_book_from_list: {}", id);
                continue;
            }

            Enchantment value = enchantment.get().value();
            entries.add(
                    new Entry(
                            ResourceKey.create(Registries.ENCHANTMENT, identifier),
                            readInt(obj, "min_level", value.getMinLevel()),
                            readInt(obj, "max_level", value.getMaxLevel()),
                            readInt(obj, "weight", 1)
                    )
            );
        }

        int baseCost = readInt(json, "base_cost", 2);
        int costPerLevel = readInt(json, "cost_per_level", 3);
        int treasureMultiplier = readInt(json, "treasure_multiplier", 2);

        return new Factory(
                currency,
                entries,
                baseCost,
                costPerLevel,
                treasureMultiplier,
                maxUses,
                experience,
                priceMultiplier,
                demand
        );
    }

    /// Holds the key only, because the factory outlives the registry it parsed against.
    private record Entry(ResourceKey<Enchantment> key, int minLevel, int maxLevel, int weight) {
    }

    private record Resolved(Entry entry, Holder<Enchantment> enchantment) {
    }

    private static class Factory implements TradeFactory, CatalogExpandable {
        private final ItemStack currency;
        private final List<Entry> entries;
        private final int baseCost;
        private final int costPerLevel;
        private final int treasureMultiplier;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;

        public Factory(
                ItemStack currency,
                List<Entry> entries,
                int baseCost,
                int costPerLevel,
                int treasureMultiplier,
                int maxUses,
                int experience,
                float multiplier,
                int demand
        ) {
            this.currency = currency;
            this.entries = entries;
            this.baseCost = baseCost;
            this.costPerLevel = costPerLevel;
            this.treasureMultiplier = treasureMultiplier;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        @Override
        public MerchantOffer create(net.minecraft.server.level.ServerLevel world, Entity entity, RandomSource random) {
            List<Resolved> resolved = resolve(entity.level());
            int totalWeight = totalWeight(resolved);

            if (totalWeight <= 0) {
                return null;
            }

            Resolved chosen = pick(resolved, totalWeight, random);
            int level = Mth.nextInt(random, chosen.entry().minLevel(), chosen.entry().maxLevel());

            return new MerchantOffer(
                    traded(new ItemStack(currency.getItem(), cost(chosen.enchantment(), level))),
                    tradedOrEmpty(new ItemStack(Items.BOOK)),
                    book(chosen.enchantment(), level),
                    0,
                    maxUses,
                    experience,
                    multiplier,
                    demand
            );
        }

        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<Resolved> resolved = resolve(merchant.level());
            int totalWeight = totalWeight(resolved);

            for (Resolved candidate : resolved) {
                Entry entry = candidate.entry();
                out.pushWeight(entry.weight(), totalWeight);
                // Levels are uniform within an entry, so each row gets a fraction of its weight.
                out.pushShare(1.0f / (entry.maxLevel() - entry.minLevel() + 1));
                for (int level = entry.minLevel(); level <= entry.maxLevel(); level++) {
                    if (out.isFull()) {
                        out.countSkipped(entry.maxLevel() - level + 1);
                        break;
                    }

                    out.add(
                            new ItemStack(currency.getItem(), cost(candidate.enchantment(), level)),
                            new ItemStack(Items.BOOK),
                            book(candidate.enchantment(), level),
                            maxUses,
                            experience,
                            multiplier,
                            demand
                    );
                }
                out.popShare();
                out.popWeight();
            }
        }

        private List<Resolved> resolve(Level world) {
            Registry<Enchantment> registry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            List<Resolved> resolved = new ArrayList<>(this.entries.size());
            for (Entry entry : this.entries) {
                registry.get(entry.key()).ifPresent(enchantment -> resolved.add(new Resolved(entry, enchantment)));
            }
            return resolved;
        }

        private static int totalWeight(List<Resolved> resolved) {
            int total = 0;
            for (Resolved candidate : resolved) {
                total += candidate.entry().weight();
            }
            return total;
        }

        private ItemStack book(Holder<Enchantment> enchantment, int level) {
            return EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, level));
        }

        private int cost(Holder<Enchantment> enchantment, int level) {
            int cost = baseCost + level * costPerLevel;

            if (enchantment.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                cost *= treasureMultiplier;
            }

            return Mth.clamp(cost, 1, 64);
        }

        private static Resolved pick(List<Resolved> resolved, int totalWeight, RandomSource random) {
            int roll = random.nextInt(totalWeight);
            for (Resolved candidate : resolved) {
                roll -= candidate.entry().weight();

                if (roll < 0) {
                    return candidate;
                }
            }
            return resolved.get(resolved.size() - 1);
        }
    }
}
