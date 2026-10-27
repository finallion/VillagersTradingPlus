package com.lion.villagertradingplus.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.ComponentChanges;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerType;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class TemplateWriter {

    private static final String NS = "villagertradingplus:";

    static final RegistryKey<VillagerType>[] TYPES = types();

    private final VanillaRuntime runtime = VanillaRuntime.get();

    @SuppressWarnings("unchecked")
    private static RegistryKey<VillagerType>[] types() {
        return new RegistryKey[] {
                VillagerType.DESERT,
                VillagerType.JUNGLE,
                VillagerType.PLAINS,
                VillagerType.SAVANNA,
                VillagerType.SNOW,
                VillagerType.SWAMP,
                VillagerType.TAIGA
        };
    }

    Map<String, String> render() {
        Map<String, String> files = new TreeMap<>();

        VanillaTables.professions().forEach((profession, levels) -> {
            JsonObject root = new JsonObject();
            root.addProperty("profession", "minecraft:" + profession);
            JsonObject trades = new JsonObject();

            for (int level = 1; level <= 5; level++) {
                JsonArray tier = new JsonArray();

                for (TradeOffers.Factory factory : levels.get(level)) {
                    tier.add(trade(factory, runtime.villager(VillagerType.PLAINS)));
                }

                trades.add(VanillaTables.TIERS[level - 1], tier);
            }

            root.add("trades", trades);
            files.put(profession + ".json", JsonText.pretty(root));
        });

        JsonObject root = new JsonObject();
        root.addProperty("replace", true);
        JsonObject trades = new JsonObject();
        List<Pair<TradeOffers.Factory[], Integer>> pools = VanillaTables.wandering();

        for (int pool = 0; pool < VanillaTables.WANDERING_POOLS.length; pool++) {
            JsonArray tier = new JsonArray();

            for (TradeOffers.Factory factory : pools.get(pool).getLeft()) {
                tier.add(trade(factory, runtime.wanderer()));
            }

            trades.add(VanillaTables.WANDERING_POOLS[pool], tier);
        }

        root.add("trades", trades);
        files.put("wandering_trader.json", JsonText.pretty(root));
        return files;
    }

    private JsonObject trade(TradeOffers.Factory factory, Entity entity) {
        String kind = VanillaTables.kind(factory);

        if (kind.equals("TypedWrapperFactory")) {
            Map<RegistryKey<VillagerType>, TradeOffers.Factory> byType = VanillaTables.field(factory, "typeToFactory");
            TradeOffers.Factory inner = byType.values().iterator().next();

            for (TradeOffers.Factory other : byType.values()) {
                if (other != inner) {
                    throw new IllegalStateException("a typed trade with different trades per type has no template form");
                }
            }

            RegistryKey<VillagerType> first = byType.keySet().iterator().next();
            JsonObject json = trade(inner, runtime.villager(first));
            json.add("merchant_predicate", predicate(byType.keySet()));
            return json;
        }

        JsonObject json = new JsonObject();

        switch (kind) {
            case "BuyItemFactory" -> {
                json.addProperty("type", NS + "buy_item");
                json.add("buy", tradedJson(VanillaTables.field(factory, "stack")));
                json.add("reward", stackJson(Items.EMERALD, VanillaTables.<Integer>field(factory, "price"), null));
                stats(json, sample(factory, entity));
            }
            case "SellItemFactory" -> {
                requireNoProvider(factory);
                json.addProperty("type", NS + "sell_item");
                json.add("sell", stackJson(VanillaTables.field(factory, "sell")));
                json.add("priceIn", stackJson(Items.EMERALD, VanillaTables.<Integer>field(factory, "price"), null));
                stats(json, sample(factory, entity));
            }
            case "ProcessItemFactory" -> {
                requireNoProvider(factory);
                json.addProperty("type", NS + "process_item");
                json.add("convertible", tradedJson(VanillaTables.field(factory, "toBeProcessed")));
                json.add("priceIn", stackJson(Items.EMERALD, VanillaTables.<Integer>field(factory, "price"), null));
                json.add("sell", stackJson(VanillaTables.field(factory, "processed")));
                stats(json, sample(factory, entity));
            }
            case "SellEnchantedToolFactory" -> {
                ItemStack tool = VanillaTables.field(factory, "tool");
                json.addProperty("type", NS + "sell_enchanted_tool");
                json.add("sell", stackJson(tool.getItem(), 1, null));
                json.add("basePriceIn", stackJson(Items.EMERALD, VanillaTables.<Integer>field(factory, "basePrice"), null));
                stats(json, sample(factory, entity));
            }
            case "EnchantBookFactory" -> {
                TagKey<?> tag = VanillaTables.field(factory, "possibleEnchantments");

                if (!tag.equals(EnchantmentTags.TRADEABLE)
                        || VanillaTables.<Integer>field(factory, "minLevel") != 0
                        || VanillaTables.<Integer>field(factory, "maxLevel") != Integer.MAX_VALUE) {
                    throw new IllegalStateException("an enchanted book listing with a tag or level limit has no template form");
                }

                json.addProperty("type", NS + "sell_enchanted_book");
                JsonObject currency = new JsonObject();
                currency.addProperty("item", "minecraft:emerald");
                json.add("currency", currency);
                stats(json, sample(factory, entity));
            }
            case "SellMapFactory" -> {
                RegistryEntry<MapDecorationType> decoration = VanillaTables.field(factory, "decoration");
                TagKey<?> structure = VanillaTables.field(factory, "structure");
                json.addProperty("type", NS + "sell_map");
                json.addProperty("structure_id", structure.id().toString());
                json.addProperty("name", VanillaTables.<String>field(factory, "nameKey"));
                json.addProperty("decoration", decoration.getKey().orElseThrow().getValue().toString());
                json.add("buy", stackJson(Items.COMPASS, 1, null));
                json.add("priceIn", stackJson(Items.EMERALD, VanillaTables.<Integer>field(factory, "price"), null));
                json.addProperty("max_uses", VanillaTables.<Integer>field(factory, "maxUses"));
                json.addProperty("villager_experience", VanillaTables.<Integer>field(factory, "experience"));
                json.addProperty("price_multiplier", mapMultiplier());
            }
            case "SellDyedArmorFactory" -> {
                Item item = VanillaTables.field(factory, "sell");
                json.addProperty("type", NS + "sell_dyed_armor");
                json.add("sell", stackJson(item, 1, null));
                json.add("priceIn", stackJson(Items.EMERALD, VanillaTables.<Integer>field(factory, "price"), null));
                stats(json, sample(factory, entity));
            }
            case "SellPotionHoldingItemFactory" -> {
                ItemStack sell = VanillaTables.field(factory, "sell");
                json.addProperty("type", NS + "sell_potion");
                json.add("convertible", stackJson(
                        VanillaTables.<Item>field(factory, "secondBuy"),
                        VanillaTables.<Integer>field(factory, "secondCount"),
                        null
                ));
                json.add("priceIn", stackJson(Items.EMERALD, VanillaTables.<Integer>field(factory, "price"), null));
                json.add("sell", stackJson(sell.getItem(), VanillaTables.<Integer>field(factory, "sellCount"), null));
                stats(json, sample(factory, entity));
            }
            case "SellSuspiciousStewFactory" -> {
                TradeOffer offer = sample(factory, entity);
                json.addProperty("type", NS + "sell_item");
                json.add("sell", stackJson(offer.getSellItem()));
                json.add("priceIn", tradedJson(offer.getFirstBuyItem()));
                stats(json, offer);
            }
            case "TypeAwareBuyForOneEmeraldFactory" -> {
                Map<RegistryKey<VillagerType>, Item> byType = VanillaTables.field(factory, "map");
                int count = VanillaTables.field(factory, "count");
                JsonObject buy = new JsonObject();

                for (RegistryKey<VillagerType> type : TYPES) {
                    buy.add(type.getValue().toString(), stackJson(byType.get(type), count, null));
                }

                json.addProperty("type", NS + "buy_item_by_villager_type");
                json.add("buy", buy);
                TradeOffer offer = sample(factory, runtime.villager(VillagerType.PLAINS));
                json.add("reward", stackJson(offer.getSellItem()));
                stats(json, offer);
            }
            default -> throw new IllegalStateException("no template form for " + kind);
        }

        return json;
    }

    private static void requireNoProvider(TradeOffers.Factory factory) {
        java.util.Optional<?> provider = VanillaTables.field(factory, "enchantmentProviderKey");

        if (provider.isPresent()) {
            throw new IllegalStateException("a listing with an enchantment provider is part of the trade experiment");
        }
    }

    private TradeOffer sample(TradeOffers.Factory factory, Entity entity) {
        TradeOffer offer = TradeApi.create(factory, entity, Random.create(0L));

        if (offer == null) {
            throw new IllegalStateException(VanillaTables.kind(factory) + " gave no offer for a sample merchant");
        }

        return offer;
    }

    private static void stats(JsonObject json, TradeOffer offer) {
        json.addProperty("max_uses", offer.getMaxUses());
        json.addProperty("villager_experience", offer.getMerchantExperience());
        json.addProperty("price_multiplier", offer.getPriceMultiplier());
    }

    private JsonObject predicate(java.util.Set<RegistryKey<VillagerType>> types) {
        List<JsonObject> terms = new ArrayList<>();

        for (RegistryKey<VillagerType> type : TYPES) {
            if (types.contains(type)) {
                JsonObject components = new JsonObject();
                components.addProperty("minecraft:villager/variant", type.getValue().toString());
                JsonObject predicate = new JsonObject();
                predicate.add("components", components);
                JsonObject term = new JsonObject();
                term.addProperty("condition", "minecraft:entity_properties");
                term.addProperty("entity", "this");
                term.add("predicate", predicate);
                terms.add(term);
            }
        }

        if (terms.size() == 1) {
            return terms.get(0);
        }

        JsonArray array = new JsonArray();
        terms.forEach(array::add);
        JsonObject anyOf = new JsonObject();
        anyOf.addProperty("condition", "minecraft:any_of");
        anyOf.add("terms", array);
        return anyOf;
    }

    private JsonObject tradedJson(TradedItem traded) {
        return stackJson(traded.item().value(), traded.count(), traded.components().toChanges());
    }

    private JsonObject stackJson(ItemStack stack) {
        return stackJson(stack.getItem(), stack.getCount(), stack.getComponentChanges());
    }

    private JsonObject stackJson(Item item, int count, ComponentChanges changes) {
        JsonObject json = new JsonObject();
        json.addProperty("item", Registries.ITEM.getId(item).toString());
        json.addProperty("count", count);

        if (changes != null && !changes.isEmpty()) {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, runtime.registries);
            json.add("components", ComponentChanges.CODEC.encodeStart(ops, changes).getOrThrow(IllegalStateException::new));
        }

        return json;
    }

    /// The map factory builds its offer with a literal. This reads the literal out of the class.
    private static float mapMultiplier() {
        AtomicBoolean found = new AtomicBoolean();
        float[] value = new float[1];

        try (InputStream in = TradeOffers.class.getClassLoader()
                .getResourceAsStream("net/minecraft/village/TradeOffers$SellMapFactory.class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("create")) {
                        return null;
                    }

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLdcInsn(Object constant) {
                            if (constant instanceof Float f) {
                                if (found.getAndSet(true)) {
                                    throw new IllegalStateException("more than one float literal in the map factory");
                                }

                                value[0] = f;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        if (!found.get()) {
            throw new IllegalStateException("no float literal in the map factory");
        }

        return value[0];
    }
}
