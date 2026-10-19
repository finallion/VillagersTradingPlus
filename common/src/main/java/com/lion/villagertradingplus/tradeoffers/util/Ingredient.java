package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Vanilla TradeOffer matches one concrete stack, so a tag input is not an any-of match.
public final class Ingredient {

    private final ItemStack fixed;
    private final TagKey<net.minecraft.item.Item> tag;
    private final int count;
    private final JsonObject componentSugar;

    private Ingredient(ItemStack fixed, TagKey<net.minecraft.item.Item> tag, int count, JsonObject componentSugar) {
        this.fixed = fixed;
        this.tag = tag;
        this.count = count;
        this.componentSugar = componentSugar;
    }

    public static Ingredient fromJson(JsonObject json) {
        int count = json.has("count") ? json.get("count").getAsInt() : 1;

        if (json.has("tag")) {
            TagKey<net.minecraft.item.Item> tag = TagKey.of(
                    RegistryKeys.ITEM,
                    Identifier.tryParse(json.get("tag").getAsString())
            );
            return new Ingredient(null, tag, count, json);
        }

        return new Ingredient(ItemStackSerializer.fromJson(json), null, count, null);
    }

    public boolean isTag() {
        return tag != null;
    }

    public ItemStack resolve(Random random) {
        if (tag == null) {
            return fixed.copy();
        }

        Optional<RegistryEntryList.Named<net.minecraft.item.Item>> entries = Registries.ITEM.getEntryList(tag);

        if (entries.isEmpty() || entries.get().size() == 0) {
            return ItemStack.EMPTY;
        }

        RegistryEntryList.Named<net.minecraft.item.Item> list = entries.get();
        return build(list.get(random.nextInt(list.size())));
    }

    public List<ItemStack> resolveAll() {
        if (tag == null) {
            return List.of(fixed.copy());
        }

        Optional<RegistryEntryList.Named<net.minecraft.item.Item>> entries = Registries.ITEM.getEntryList(tag);

        if (entries.isEmpty()) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (RegistryEntry<net.minecraft.item.Item> entry : entries.get()) {
            stacks.add(build(entry));
        }
        return stacks;
    }

    private ItemStack build(RegistryEntry<net.minecraft.item.Item> entry) {
        ItemStack stack = new ItemStack(entry.value(), count);

        if (componentSugar != null) {
            ItemStackSerializer.applyComponents(stack, componentSugar);
        }

        return stack;
    }
}
