package com.lion.villagertradingplus.tradeoffers.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.datafixer.Schemas;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Raw `nbt` applies last. Bad input throws `TradeParseException`.
public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    public static ItemStack fromJson(JsonObject json) {
        return fromJson(json, true);
    }

    public static ItemStack fromJson(JsonObject json, boolean withCount) {
        JsonElement idElement = json.get("item");
        if (idElement == null) {
            throw new TradeParseException("trade item is missing the \"item\" key: " + json);
        }

        String id = idElement.getAsString();
        Optional<Item> item = Registries.ITEM.getOptionalValue(Identifier.tryParse(id));
        if (item.isEmpty()) {
            throw new TradeParseException("unknown item id \"" + id
                    + "\" (is the mod that provides it installed?)");
        }

        int count = withCount && json.has("count") ? json.get("count").getAsInt() : 1;
        ItemStack stack = new ItemStack(item.get(), count);
        applyComponents(stack, json);
        return stack;
    }

    /// Public so tag-resolved stacks reuse the same sugar per random pick.
    public static void applyComponents(ItemStack stack, JsonObject json) {
        if (stack.isEmpty()) {
            return;
        }

        if (json.has("name")) {
            stack.set(DataComponentTypes.CUSTOM_NAME, parseText(json.get("name")));
        }

        if (json.has("lore")) {
            applyLore(stack, json.getAsJsonArray("lore"));
        }

        if (json.has("enchantments")) {
            applyEnchantments(stack, json.getAsJsonArray("enchantments"));
        }

        // No dyeable check: any item can carry a color, and the game ignores it where it does not render one.
        if (json.has("color")) {
            stack.set(DataComponentTypes.DYED_COLOR,
                    new DyedColorComponent(parseColor(json.get("color").getAsString())));
        }

        if (json.has("potion")) {
            String rawPotion = json.get("potion").getAsString();
            Identifier potionId = Identifier.tryParse(rawPotion);
            Optional<RegistryEntry.Reference<Potion>> potion = potionId == null
                    ? Optional.empty()
                    : Registries.POTION.getEntry(potionId);
            stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(
                    potion.orElseThrow(() -> new TradeParseException("unknown potion id \"" + rawPotion + "\" on a trade item"))));
        }

        if (json.has("skull_owner")) {
            stack.set(DataComponentTypes.PROFILE,
                    ProfileComponent.ofDynamic(json.get("skull_owner").getAsString()));
        }

        if (json.has("book")) {
            applyBook(stack, json.getAsJsonObject("book"));
        }

        // Raw SNBT escape hatch, applied LAST so it can override or add anything above.
        if (json.has("nbt")) {
            applyRawNbt(stack, json.get("nbt").getAsString());
        }
    }

    /// Enchantments are a dynamic registry, so the ids resolve against the registry loaded for this world.
    private static void applyEnchantments(ItemStack stack, JsonArray enchantments) {
        Optional<Registry<Enchantment>> registry = DatapackRegistries.registry(RegistryKeys.ENCHANTMENT);
        if (registry.isEmpty()) {
            VillagerTradingPlus.LOGGER.error(
                    "Cannot resolve \"enchantments\" on a trade item: no datapack registries are loaded. "
                            + "The item stays unenchanted.");
            return;
        }

        // Builds on the existing stack, so repeated application accumulates. A tag Ingredient reapplies it per pick.
        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(EnchantmentHelper.getEnchantments(stack));
        boolean applied = false;

        for (JsonElement element : enchantments) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("id")) {
                VillagerTradingPlus.LOGGER.error("Enchantment on a trade item is missing its \"id\": {}", entry);
                continue;
            }

            String id = entry.get("id").getAsString();
            Identifier identifier = Identifier.tryParse(id);
            Optional<RegistryEntry.Reference<Enchantment>> enchantment = identifier == null
                    ? Optional.empty()
                    : registry.get().getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, identifier));

            if (enchantment.isEmpty()) {
                throw new TradeParseException("unknown enchantment \"" + id + "\" on a trade item");
            }

            builder.set(enchantment.get(), entry.has("lvl") ? entry.get("lvl").getAsInt() : 1);
            applied = true;
        }

        if (applied) {
            // Enchanted books take stored_enchantments. Other items take enchantments.
            EnchantmentHelper.set(stack, builder.build());
        }
    }

    private static void applyLore(ItemStack stack, JsonArray lore) {
        List<Text> lines = new ArrayList<>();
        for (JsonElement line : lore) {
            lines.add(parseText(line));
        }
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
    }

    private static void applyBook(ItemStack stack, JsonObject book) {
        String title = book.has("title") ? book.get("title").getAsString() : "";
        String author = book.has("author") ? book.get("author").getAsString() : "";

        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        if (book.has("pages")) {
            for (JsonElement page : book.getAsJsonArray("pages")) {
                pages.add(RawFilteredPair.of(parseText(page)));
            }
        }

        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new WrittenBookContentComponent(RawFilteredPair.of(title), author, 0, pages, true));
    }

    /// Runs the raw tag through the vanilla data fixer, so legacy SNBT keys land in the right components.
    private static void applyRawNbt(ItemStack stack, String snbt) {
        try {
            NbtCompound parsed = StringNbtReader.readCompound(snbt);
            componentize(stack, parsed).ifPresent(stack::applyChanges);
        } catch (TradeParseException e) {
            throw e;
        } catch (Exception e) {
            throw new TradeParseException("invalid \"nbt\" on a trade item: " + snbt + " (" + e.getMessage() + ")");
        }
    }

    /// Base data version of the raw tag. Anything below the componentization schema (3818) runs the fixer.
    private static final int RAW_NBT_DATA_VERSION = 3465;

    private static Optional<ComponentChanges> componentize(ItemStack stack, NbtCompound tag) {
        Optional<RegistryWrapper.WrapperLookup> lookup = DatapackRegistries.lookup();
        if (lookup.isEmpty()) {
            VillagerTradingPlus.LOGGER.error(
                    "Cannot convert the \"nbt\" of a trade item: no datapack registries are loaded. "
                            + "The item is used without it.");
            return Optional.empty();
        }

        NbtCompound old = new NbtCompound();
        old.putString("id", Registries.ITEM.getId(stack.getItem()).toString());
        // A fixed count of 1 stays within the range that ItemStack.CODEC accepts. The stack carries the real count.
        old.putInt("Count", 1);
        old.put("tag", tag);

        Dynamic<NbtElement> fixed = Schemas.getFixer().update(
                TypeReferences.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, old),
                RAW_NBT_DATA_VERSION,
                SharedConstants.getGameVersion().dataVersion().id());

        // Components can reference dynamic registries, so plain NbtOps cannot read the fixed stack back.
        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, lookup.get());
        return ItemStack.CODEC.parse(ops, fixed.getValue())
                .resultOrPartial(error -> {
                    throw new TradeParseException("could not read the converted \"nbt\" of a trade item: " + error);
                })
                .map(ItemStack::getComponentChanges);
    }

    /// Plain strings are literal. JSON-like strings parse as components, and a parse failure falls back to literal.
    private static Text parseText(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String trimmed = raw.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                Text parsed = decodeText(com.google.gson.JsonParser.parseString(raw));
                if (parsed != null) {
                    return parsed;
                }
            }
            return Text.literal(raw);
        }

        Text parsed = decodeText(element);
        return parsed != null ? parsed : Text.empty();
    }

    private static Text decodeText(JsonElement element) {
        try {
            return TextCodecs.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null);
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
