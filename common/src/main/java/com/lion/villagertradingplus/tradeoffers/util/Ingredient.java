package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        int count = json.has("count") ? json.get("count").getAsInt() : 1;

        if (json.has("tag")) {
            TagKey<net.minecraft.world.item.Item> tag = TagKey.create(
                    Registries.ITEM,
                    Identifier.tryParse(json.get("tag").getAsString())
            );
            return new Ingredient(null, tag, count, json);
        }

        return new Ingredient(ItemStackSerializer.fromJson(json), null, count, null);
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
        ItemStack stack = new ItemStack(entry.value(), count);

        if (componentSugar != null) {
            ItemStackSerializer.applyComponents(stack, componentSugar);
        }

        return stack;
    }
}
