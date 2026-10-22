package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/// Vanilla [MerchantOffer] matches one concrete [ItemStack], so a tag is not an any-of match.
public final class Ingredient {

    private final ItemStack fixed;
    private final TagKey<net.minecraft.world.item.Item> tag;
    private final int count;
    private final JsonObject componentSugar;
    private final Set<String> clampedOnce = new HashSet<>();

    private Ingredient(
            ItemStack fixed,
            TagKey<net.minecraft.world.item.Item> tag,
            int count,
            JsonObject componentSugar
    ) {
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
            TagKey<net.minecraft.world.item.Item> tag = TagKey.create(
                    Registries.ITEM,
                    Identifier.tryParse(json.get("tag").getAsString())
            );
            return new Ingredient(null, tag, count, json);
        }

        return new Ingredient(ItemStackSerializer.fromJson(json), null, 1, null);
    }

    public boolean isTag() {
        return tag != null;
    }

    public ItemStack resolve(RandomSource random) {
        if (tag == null) {
            return fixed.copy();
        }

        Optional<HolderSet.Named<net.minecraft.world.item.Item>> entries = BuiltInRegistries.ITEM.get(tag);

        if (entries.isEmpty() || entries.get().size() == 0) {
            return ItemStack.EMPTY;
        }

        HolderSet.Named<net.minecraft.world.item.Item> list = entries.get();
        return build(list.get(random.nextInt(list.size())));
    }

    public List<ItemStack> resolveAll() {
        if (tag == null) {
            return List.of(fixed.copy());
        }

        Optional<HolderSet.Named<net.minecraft.world.item.Item>> entries = BuiltInRegistries.ITEM.get(tag);

        if (entries.isEmpty()) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (Holder<net.minecraft.world.item.Item> entry : entries.get()) {
            stacks.add(build(entry));
        }
        return stacks;
    }

    private ItemStack build(Holder<net.minecraft.world.item.Item> entry) {
        ItemStack stack = new ItemStack(entry.value(), 1);

        if (componentSugar != null) {
            ItemStackSerializer.applyComponents(stack, componentSugar);
        }

        String label = "#" + tag.location() + " member " + BuiltInRegistries.ITEM.getKey(entry.value());
        int max = stack.getMaxStackSize();

        if (clampedOnce.add(label)) {
            ItemStackSerializer.clampCount(count, max, label);
        }

        stack.setCount(Math.max(1, Math.min(count, max)));
        return stack;
    }
}
