package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

/// Raw `nbt` applies last.
public final class ItemStackSerializer {

    private static final int COST_COUNT_LIMIT = 64;

    private ItemStackSerializer() {}

    public static ItemStack fromJson(JsonObject json) {
        return fromJson(json, true);
    }

    /// A slot A cost may exceed the stack size.
    public static ItemStack fromJsonCost(JsonObject json) {
        return fromJson(json, true, true);
    }

    public static ItemStack fromJson(JsonObject json, boolean withCount) {
        return fromJson(json, withCount, false);
    }

    private static ItemStack fromJson(JsonObject json, boolean withCount, boolean cost) {
        JsonElement idElement = json.get("item");

        if (idElement == null) {
            throw new TradeParseException("trade item is missing the \"item\" key: " + json);
        }

        String id = idElement.getAsString();
        Optional<Item> item = Registries.ITEM.getOrEmpty(Identifier.tryParse(id));

        if (item.isEmpty()) {
            throw new TradeParseException("unknown item id \"" + id + "\" (is the mod that provides it installed?)");
        }

        ItemStack stack = new ItemStack(item.get(), 1);

        if (withCount && json.has("count")) {
            int limit = cost && stack.getMaxCount() == 1 ? COST_COUNT_LIMIT : stack.getMaxCount();

            stack.setCount(clampCount(json.get("count").getAsInt(), limit, id));
        }

        applyComponents(stack, json);
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
            stack.setCustomName(parseText(json.get("name")));
        }

        if (json.has("lore")) {
            applyLore(stack, json.getAsJsonArray("lore"));
        }

        if (json.has("enchantments")) {
            applyEnchantments(stack, json.getAsJsonArray("enchantments"));
        }

        if (json.has("color") && stack.getItem() instanceof DyeableItem dyeable) {
            dyeable.setColor(stack, parseColor(json.get("color").getAsString()));
        }

        if (json.has("potion")) {
            String rawPotion = json.get("potion").getAsString();
            Identifier potionId = Identifier.tryParse(rawPotion);

            if (potionId == null || !Registries.POTION.containsId(potionId)) {
                VillagerTradingPlus.LOGGER.warn(
                        "Unknown potion id \"{}\" on a trade item{}, skipping it.",
                        rawPotion,
                        TradeOfferManager.fileSuffix()
                );
            } else {
                PotionUtil.setPotion(stack, Registries.POTION.get(potionId));
            }
        }

        if (json.has("skull_owner")) {
            stack.getOrCreateNbt().putString("SkullOwner", json.get("skull_owner").getAsString());
        }

        if (json.has("book")) {
            applyBook(stack, json.getAsJsonObject("book"));
        }

        if (json.has("nbt")) {
            applyRawNbt(stack, json.get("nbt").getAsString());
        }
    }

    private static void applyLore(ItemStack stack, JsonArray lore) {
        NbtList loreList = new NbtList();
        for (JsonElement line : lore) {
            loreList.add(NbtString.of(Text.Serializer.toJson(parseText(line))));
        }
        stack.getOrCreateSubNbt("display").put("Lore", loreList);
    }

    private static void applyEnchantments(ItemStack stack, JsonArray enchantments) {
        boolean isBook = stack.getItem() instanceof EnchantedBookItem;
        for (JsonElement element : enchantments) {
            JsonObject obj = element.getAsJsonObject();
            Enchantment enchantment = Registries.ENCHANTMENT.get(Identifier.tryParse(obj.get("id").getAsString()));

            if (enchantment == null) {
                VillagerTradingPlus.LOGGER.warn(
                        "Unknown enchantment \"{}\" on a trade item{}, skipping it.",
                        obj.get("id").getAsString(),
                        TradeOfferManager.fileSuffix()
                );
                continue;
            }

            int level = obj.has("lvl") ? obj.get("lvl").getAsInt() : 1;

            if (isBook) {
                EnchantedBookItem.addEnchantment(stack, new EnchantmentLevelEntry(enchantment, level));
            } else {
                stack.addEnchantment(enchantment, level);
            }
        }
    }

    private static void applyBook(ItemStack stack, JsonObject book) {
        NbtCompound nbt = stack.getOrCreateNbt();

        if (book.has("title")) {
            nbt.putString("title", book.get("title").getAsString());
        }

        if (book.has("author")) {
            nbt.putString("author", book.get("author").getAsString());
        }

        if (book.has("pages")) {
            NbtList pages = new NbtList();
            for (JsonElement page : book.getAsJsonArray("pages")) {
                pages.add(NbtString.of(Text.Serializer.toJson(parseText(page))));
            }
            nbt.put("pages", pages);
        }
    }

    private static void applyRawNbt(ItemStack stack, String snbt) {
        try {
            NbtCompound parsed = StringNbtReader.parse(snbt);
            stack.getOrCreateNbt().copyFrom(parsed);
        } catch (Exception e) {
            VillagerTradingPlus.LOGGER.warn(
                    "Invalid \"nbt\" on a trade item{}, skipping it: {} ({})",
                    TradeOfferManager.fileSuffix(),
                    snbt,
                    e.getMessage()
            );
        }
    }

    /// JSON-like strings parse as components. A failed parse keeps the literal.
    private static Text parseText(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String trimmed = raw.trim();

            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    Text parsed = Text.Serializer.fromJson(raw);

                    if (parsed != null) {
                        return parsed;
                    }
                } catch (Exception ignored) {
                }
            }

            return Text.literal(raw);
        }

        try {
            Text parsed = Text.Serializer.fromJson(element);

            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
        }
        return Text.empty();
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
