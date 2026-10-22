package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Raw `nbt` applies last.
public final class ItemStackSerializer {

    private ItemStackSerializer() {}

    public static ItemStack fromJson(JsonObject json) {
        return fromJson(json, true);
    }

    public static ItemStack fromJson(JsonObject json, boolean withCount) {
        JsonElement idElement = json.get("item");

        if (idElement == null) {
            throw new TradeParseException("trade item is missing the \"item\" key: " + json);
        }

        String id = idElement.getAsString();
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(id));

        if (item.isEmpty()) {
            throw new TradeParseException("unknown item id \"" + id + "\" (is the mod that provides it installed?)");
        }

        ItemStack stack = new ItemStack(item.get(), 1);

        applyComponents(stack, json);

        if (withCount && json.has("count")) {
            stack.setCount(clampCount(json.get("count").getAsInt(), stack.getMaxStackSize(), id));
        }

        return stack;
    }

    public static int clampCount(int requested, int maxCount, String itemId) {
        int clamped = Math.max(1, Math.min(requested, maxCount));

        if (clamped != requested) {
            VillagerTradingPlus.LOGGER.warn(
                    "Trade item {}{} has count {}, using {} (allowed 1 to {}).",
                    itemId,
                    TradeOfferManager.fileSuffix(),
                    requested,
                    clamped,
                    maxCount
            );
        }

        return clamped;
    }

    public static void applyComponents(ItemStack stack, JsonObject json) {
        if (stack.isEmpty()) {
            return;
        }

        if (json.has("name")) {
            stack.set(DataComponents.CUSTOM_NAME, parseText(json.get("name")));
        }

        if (json.has("lore")) {
            applyLore(stack, json.getAsJsonArray("lore"));
        }

        if (json.has("enchantments")) {
            applyEnchantments(stack, json.getAsJsonArray("enchantments"));
        }

        if (json.has("color")) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(parseColor(json.get("color").getAsString())));
        }

        if (json.has("potion")) {
            String rawPotion = json.get("potion").getAsString();
            Identifier potionId = Identifier.tryParse(rawPotion);
            Optional<Holder.Reference<Potion>> potion = potionId == null
                    ? Optional.empty()
                    : BuiltInRegistries.POTION.get(potionId);
            if (potion.isEmpty()) {
                VillagerTradingPlus.LOGGER.warn(
                        "Unknown potion id \"{}\" on a trade item{}, skipping it.",
                        rawPotion,
                        TradeOfferManager.fileSuffix()
                );
            } else {
                stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion.get()));
            }
        }

        if (json.has("skull_owner")) {
            stack.set(
                    DataComponents.PROFILE,
                    ResolvableProfile.createUnresolved(json.get("skull_owner").getAsString())
            );
        }

        if (json.has("book")) {
            applyBook(stack, json.getAsJsonObject("book"));
        }

        if (json.has("nbt")) {
            applyRawNbt(stack, json.get("nbt").getAsString());
        }
    }

    private static void applyEnchantments(ItemStack stack, JsonArray enchantments) {
        Optional<Registry<Enchantment>> registry = DatapackRegistries.registry(Registries.ENCHANTMENT);

        if (registry.isEmpty()) {
            VillagerTradingPlus.LOGGER.error(
                    "Cannot resolve \"enchantments\" on a trade item: no datapack registries are loaded. "
                            + "The item stays unenchanted."
            );
            return;
        }

        ItemEnchantments.Mutable builder =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        boolean applied = false;

        for (JsonElement element : enchantments) {
            JsonObject entry = element.getAsJsonObject();

            if (!entry.has("id")) {
                VillagerTradingPlus.LOGGER.error("Enchantment on a trade item is missing its \"id\": {}", entry);
                continue;
            }

            String id = entry.get("id").getAsString();
            Identifier identifier = Identifier.tryParse(id);
            Optional<Holder.Reference<Enchantment>> enchantment = identifier == null
                    ? Optional.empty()
                    : registry.get().get(ResourceKey.create(Registries.ENCHANTMENT, identifier));

            if (enchantment.isEmpty()) {
                VillagerTradingPlus.LOGGER.warn(
                        "Unknown enchantment \"{}\" on a trade item{}, skipping it.",
                        id,
                        TradeOfferManager.fileSuffix()
                );
                continue;
            }

            builder.set(enchantment.get(), entry.has("lvl") ? entry.get("lvl").getAsInt() : 1);
            applied = true;
        }

        if (applied) {
            EnchantmentHelper.setEnchantments(stack, builder.toImmutable());
        }
    }

    private static void applyLore(ItemStack stack, JsonArray lore) {
        List<Component> lines = new ArrayList<>();
        for (JsonElement line : lore) {
            lines.add(parseText(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static void applyBook(ItemStack stack, JsonObject book) {
        String title = book.has("title") ? book.get("title").getAsString() : "";
        String author = book.has("author") ? book.get("author").getAsString() : "";

        List<Filterable<Component>> pages = new ArrayList<>();

        if (book.has("pages")) {
            for (JsonElement page : book.getAsJsonArray("pages")) {
                pages.add(Filterable.passThrough(parseText(page)));
            }
        }

        stack.set(
                DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, pages, true)
        );
    }

    private static void applyRawNbt(ItemStack stack, String snbt) {
        try {
            CompoundTag parsed = TagParser.parseCompoundFully(snbt);
            componentize(stack, parsed).ifPresent(stack::applyComponentsAndValidate);
        } catch (Exception e) {
            VillagerTradingPlus.LOGGER.warn(
                    "Invalid \"nbt\" on a trade item{}, skipping it: {} ({})",
                    TradeOfferManager.fileSuffix(),
                    snbt,
                    e.getMessage()
            );
        }
    }

    private static final int RAW_NBT_DATA_VERSION = 3465;

    private static Optional<DataComponentPatch> componentize(ItemStack stack, CompoundTag tag) {
        Optional<HolderLookup.Provider> lookup = DatapackRegistries.lookup();

        if (lookup.isEmpty()) {
            VillagerTradingPlus.LOGGER.error(
                    "Cannot convert the \"nbt\" of a trade item: no datapack registries are loaded. "
                            + "The item is used without it."
            );
            return Optional.empty();
        }

        CompoundTag old = new CompoundTag();
        old.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        old.putInt("Count", 1);
        old.put("tag", tag);

        Dynamic<Tag> fixed = DataFixers.getDataFixer()
                .update(
                        References.ITEM_STACK,
                        new Dynamic<>(NbtOps.INSTANCE, old),
                        RAW_NBT_DATA_VERSION,
                        SharedConstants.getCurrentVersion().dataVersion().version()
                );

        // Components can reference dynamic registries, so plain NbtOps cannot read the stack back.
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, lookup.get());
        return ItemStack.CODEC.parse(ops, fixed.getValue())
                .resultOrPartial(error -> {
                    throw new TradeParseException("could not read the converted \"nbt\" of a trade item: " + error);
                })
                .map(ItemStack::getComponentsPatch);
    }

    private static Component parseText(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String trimmed = raw.trim();

            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                Component parsed = decodeText(com.google.gson.JsonParser.parseString(raw));

                if (parsed != null) {
                    return parsed;
                }
            }

            return Component.literal(raw);
        }

        Component parsed = decodeText(element);
        return parsed != null ? parsed : Component.empty();
    }

    private static Component decodeText(JsonElement element) {
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseColor(String value) {
        String trimmed = value.trim();

        if (trimmed.startsWith("#")) {
            return Integer.parseInt(trimmed.substring(1), 16);
        }

        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return Integer.parseInt(trimmed.substring(2), 16);
        }

        return Integer.parseInt(trimmed);
    }
}
