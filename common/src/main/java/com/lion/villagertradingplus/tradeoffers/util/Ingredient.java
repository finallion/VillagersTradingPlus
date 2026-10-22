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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Vanilla TradeOffer matches one concrete stack, so a tag input is not an any-of match.
public final class Ingredient {

    private final ItemStack fixed;
    private final TagKey<net.minecraft.item.Item> tag;
    private final int count;
    private final JsonObject componentSugar;
    private final Set<String> clampedOnce = new HashSet<>();

    private Ingredient(ItemStack fixed, TagKey<net.minecraft.item.Item> tag, int count, JsonObject componentSugar) {
        this.fixed = fixed;
        this.tag = tag;
        this.count = count;
        this.componentSugar = componentSugar;
    }

    public static Ingredient fromJson(JsonObject json) {
        if (json.has("tag")) {
            int count = json.has("count")
                    ? ItemStackSerializer.clampCount(json.get("count").getAsInt(), 64, "#" + json.get("tag").getAsString())
                    : 1;
            TagKey<net.minecraft.item.Item> tag = TagKey.of(
                    RegistryKeys.ITEM,
                    Identifier.tryParse(json.get("tag").getAsString())
            );
            return new Ingredient(null, tag, count, json);
        }

        return new Ingredient(ItemStackSerializer.fromJson(json), null, 1, null);
    }

    public boolean isTag() {
        return tag != null;
    }

    public ItemStack resolve(Random random) {
        if (tag == null) {
            return fixed.copy();
        }

        Optional<RegistryEntryList.Named<net.minecraft.item.Item>> entries = Registries.ITEM.getOptional(tag);

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

        Optional<RegistryEntryList.Named<net.minecraft.item.Item>> entries = Registries.ITEM.getOptional(tag);

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
        ItemStack stack = new ItemStack(entry.value(), 1);

        if (componentSugar != null) {
            ItemStackSerializer.applyComponents(stack, componentSugar);
        }

        String label = "#" + tag.id() + " member " + Registries.ITEM.getId(entry.value());
        int max = stack.getMaxCount();

        if (clampedOnce.add(label)) {
            ItemStackSerializer.clampCount(count, max, label);
        }

        stack.setCount(Math.max(1, Math.min(count, max)));
        return stack;
    }
}
