package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.ItemStackSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.resources.Identifier;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

public abstract class JsonTradeOffer {
    protected int maxUses;
    protected int experience;
    protected float priceMultiplier;
    protected int demand;

    @NotNull
    public abstract TradeFactory deserialize(JsonObject json);

    protected void loadDefaultStats(JsonObject jsonObject) {
        this.maxUses = readInt(jsonObject, "max_uses", 12);
        this.experience = readInt(jsonObject, "villager_experience", 5);
        this.demand = readInt(jsonObject, "demand", 0);
        float base = readFloat(jsonObject, "price_multiplier", 0.05f);
        this.priceMultiplier = base * VillagerTradingPlus.CONFIG.trade_price_multiplier_scale;
    }

    protected MerchantOffer buildOffer(ItemStack buy, ItemStack second, ItemStack sell) {
        return new MerchantOffer(traded(buy), tradedOrEmpty(second), sell, 0, maxUses, experience, priceMultiplier, demand);
    }

    protected static ItemCost traded(ItemStack stack) {
        ItemCost tradedItem = new ItemCost(stack.getItem(), stack.getCount());
        DataComponentPatch changes = stack.getComponentsPatch();
        if (changes.isEmpty()) {
            return tradedItem;
        }
        return tradedItem.withComponents(builder -> {
            addChanges(builder, changes);
            return builder;
        });
    }

    protected static Optional<ItemCost> tradedOrEmpty(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(traded(stack));
    }

    private static void addChanges(DataComponentExactPredicate.Builder builder, DataComponentPatch changes) {
        // A predicate cannot require that a component is absent, so removed components are skipped.
        for (TypedDataComponent<?> component : changes.split().added()) {
            builder.expect(component);
        }
    }

    public static int readInt(JsonObject object, String key, int defaultValue) {
        return object.has(key) ? object.get(key).getAsInt() : defaultValue;
    }

    public static float readFloat(JsonObject object, String key, float defaultValue) {
        return object.has(key) ? object.get(key).getAsFloat() : defaultValue;
    }

    public static String readString(JsonObject object, String key, String defaultValue) {
        return object.has(key) ? object.get(key).getAsString() : defaultValue;
    }

    public static Identifier readIdentifier(JsonObject object, String key, String defaultValue) {
        return object.has(key) ? Identifier.tryParse(object.get(key).getAsString()) : Identifier.parse(defaultValue);
    }

    public static ItemStack getItemStackFromJson(JsonObject json) {
        return ItemStackSerializer.fromJson(json, true);
    }

    public static ItemStack getItemStackFromJsonWithoutCount(JsonObject json) {
        return ItemStackSerializer.fromJson(json, false);
    }
}
