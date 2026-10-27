package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

public class JsonSellPotionTradeOffer extends JsonTradeOffer {

    private static final String POTIONS = "potions";

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        ItemStack sell = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "sell"));
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_potion trade", "convertible"));
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());

        return new Factory(
                buy,
                sell,
                currency,
                potionTag(json),
                maxUses,
                experience,
                priceMultiplier,
                demand
        );
    }

    @Nullable
    private static TagKey<Potion> potionTag(JsonObject json) {
        if (!json.has(POTIONS)) {
            return null;
        }

        String raw = json.get(POTIONS).getAsString();
        Identifier id = raw.startsWith("#") ? Identifier.tryParse(raw.substring(1)) : null;

        if (id == null) {
            throw new TradeParseException("\"" + POTIONS + "\" must be a potion tag such as \"#minecraft:tradeable\": " + raw);
        }

        return TagKey.create(Registries.POTION, id);
    }

    private static class Factory implements TradeFactory, CatalogExpandable {
        private final ItemStack buy;
        private final ItemStack sell;
        private final ItemStack currency;
        private final TagKey<Potion> potions;
        private final int maxUses;
        private final int experience;
        private final float multiplier;
        private final int demand;
        private boolean warnedEmptyTag;

        public Factory(
                ItemStack buy,
                ItemStack sell,
                ItemStack currency,
                TagKey<Potion> potions,
                int maxUses,
                int experience,
                float multiplier,
                int demand
        ) {
            this.buy = buy;
            this.sell = sell;
            this.currency = currency;
            this.potions = potions;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
            this.demand = demand;
        }

        public MerchantOffer create(
                net.minecraft.server.level.ServerLevel world,
                Entity entity,
                net.minecraft.util.RandomSource random
        ) {
            if (this.potions != null) {
                return createFromTag(random);
            }

            List<Holder<Potion>> list = brewable(entity.level());

            if (list.isEmpty()) {
                return null;
            }

            Holder<Potion> potion = list.get(random.nextInt(list.size()));

            return new MerchantOffer(
                    waterBottle(),
                    Optional.of(tradedCurrency()),
                    sellStack(potion),
                    this.maxUses,
                    this.experience,
                    this.multiplier
            );
        }

        private MerchantOffer createFromTag(net.minecraft.util.RandomSource random) {
            Optional<Holder<Potion>> potion = BuiltInRegistries.POTION.get(this.potions)
                    .flatMap(set -> set.getRandomElement(random));

            if (potion.isEmpty()) {
                warnEmptyTag();
                return null;
            }

            return new MerchantOffer(
                    traded(this.currency),
                    tradedOrEmpty(this.buy),
                    sellStack(potion.get()),
                    0,
                    this.maxUses,
                    this.experience,
                    this.multiplier,
                    this.demand
            );
        }

        private void warnEmptyTag() {
            if (this.warnedEmptyTag) {
                return;
            }

            this.warnedEmptyTag = true;
            VillagerTradingPlus.LOGGER.warn(
                    "The potion tag #{} of a sell_potion trade is unknown or empty, so the trade gives no offer.",
                    this.potions.location()
            );
        }

        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            List<Holder<Potion>> candidates = this.potions == null
                    ? brewable(merchant.level())
                    : tagMembers();
            out.pushShare(candidates.isEmpty() ? 1.0f : 1.0f / candidates.size());
            for (Holder<Potion> potion : candidates) {
                if (out.isFull()) {
                    out.countSkipped(1);
                    continue;
                }

                out.add(
                        this.potions == null ? waterBottle().itemStack() : this.currency,
                        this.potions == null ? this.currency : this.buy,
                        sellStack(potion),
                        this.maxUses,
                        this.experience,
                        this.multiplier,
                        0
                );
            }
            out.popShare();
        }

        private List<Holder<Potion>> tagMembers() {
            return BuiltInRegistries.POTION.get(this.potions)
                    .map(set -> set.stream().collect(Collectors.toList()))
                    .orElse(List.of());
        }

        private static List<Holder<Potion>> brewable(Level world) {
            MinecraftServer server = world.getServer();

            if (server == null) {
                return List.of();
            }

            Set<Holder<Potion>> outputs = new HashSet<>();
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                if (holder.value() instanceof BrewingRecipe recipe) {
                    PotionContents contents = recipe.getOutput().get(DataComponents.POTION_CONTENTS);

                    if (contents != null) {
                        contents.potion().ifPresent(outputs::add);
                    }
                }
            }
            return BuiltInRegistries.POTION.listElements()
                    .filter(entry -> !entry.value().getEffects().isEmpty() && outputs.contains(entry))
                    .collect(Collectors.toList());
        }

        private ItemCost waterBottle() {
            return new ItemCost(this.buy.getItem(), this.buy.getCount())
                    .withComponents(
                            builder -> builder.expect(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER))
                    );
        }

        private ItemCost tradedCurrency() {
            return new ItemCost(this.currency.getItem(), this.currency.getCount());
        }

        private ItemStack sellStack(Holder<Potion> potion) {
            ItemStack stack = new ItemStack(this.sell.getItem(), Math.max(1, this.sell.getCount()));
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            return stack;
        }
    }
}
