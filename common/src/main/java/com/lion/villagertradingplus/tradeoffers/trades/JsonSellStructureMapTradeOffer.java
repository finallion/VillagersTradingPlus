package com.lion.villagertradingplus.tradeoffers.trades;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.VanillaTradeFactory;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import com.lion.villagertradingplus.tradeoffers.util.VanillaCodecs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrades;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import org.jetbrains.annotations.NotNull;

public class JsonSellStructureMapTradeOffer extends JsonTradeOffer {
    private static final String OWNER = "sell_map trade";
    private static final String GIVES = "gives";
    private static final String GIVEN_ITEM_MODIFIER = "given_item_modifier";
    private static final String DECORATION = "decoration";

    private static final Map<String, String> RENAMED_MAP_TAGS = Map.of(
            "minecraft:on_jungle_explorer_maps", "minecraft:on_jungle_pyramid_maps",
            "minecraft:on_swamp_explorer_maps", "minecraft:on_swamp_hut_maps",
            "minecraft:on_ocean_explorer_maps", "minecraft:on_ocean_monument_maps",
            "minecraft:on_woodland_explorer_maps", "minecraft:on_woodland_mansion_maps",
            "minecraft:on_trial_chambers_maps", "minecraft:on_buried_trial_chambers_maps"
    );

    private static final int VANILLA_TRADE_SEARCH_RADIUS = 100;

    private record VanillaMap(Item item, Holder<MapDecorationType> decoration) {
    }

    private static final Map<String, VanillaMap> VANILLA_MAPS = Map.ofEntries(
            Map.entry("minecraft:on_desert_village_maps", new VanillaMap(Items.DESERT_VILLAGE_MAP, MapDecorationTypes.DESERT_VILLAGE)),
            Map.entry("minecraft:on_plains_village_maps", new VanillaMap(Items.PLAINS_VILLAGE_MAP, MapDecorationTypes.PLAINS_VILLAGE)),
            Map.entry("minecraft:on_savanna_village_maps", new VanillaMap(Items.SAVANNA_VILLAGE_MAP, MapDecorationTypes.SAVANNA_VILLAGE)),
            Map.entry("minecraft:on_snowy_village_maps", new VanillaMap(Items.SNOWY_VILLAGE_MAP, MapDecorationTypes.SNOWY_VILLAGE)),
            Map.entry("minecraft:on_taiga_village_maps", new VanillaMap(Items.TAIGA_VILLAGE_MAP, MapDecorationTypes.TAIGA_VILLAGE)),
            Map.entry("minecraft:on_swamp_hut_maps", new VanillaMap(Items.SWAMP_HUT_MAP, MapDecorationTypes.SWAMP_HUT)),
            Map.entry("minecraft:on_jungle_pyramid_maps", new VanillaMap(Items.JUNGLE_PYRAMID_MAP, MapDecorationTypes.JUNGLE_TEMPLE)),
            Map.entry("minecraft:on_ocean_monument_maps", new VanillaMap(Items.OCEAN_MONUMENT_MAP, MapDecorationTypes.OCEAN_MONUMENT)),
            Map.entry("minecraft:on_buried_trial_chambers_maps", new VanillaMap(Items.BURIED_TRIAL_CHAMBERS_MAP, MapDecorationTypes.TRIAL_CHAMBERS)),
            Map.entry("minecraft:on_woodland_mansion_maps", new VanillaMap(Items.WOODLAND_MANSION_MAP, MapDecorationTypes.WOODLAND_MANSION)),
            Map.entry("minecraft:on_ancient_city_maps", new VanillaMap(Items.BURIED_ANCIENT_CITY_MAP, MapDecorationTypes.ANCIENT_CITY)),
            Map.entry("minecraft:on_mineshaft_maps", new VanillaMap(Items.BURIED_MINESHAFT_MAP, MapDecorationTypes.MINESHAFT)),
            Map.entry("minecraft:on_desert_pyramid_maps", new VanillaMap(Items.DESERT_PYRAMID_MAP, MapDecorationTypes.DESERT_PYRAMID)),
            Map.entry("minecraft:on_ocean_ruin_warm_maps", new VanillaMap(Items.WARM_OCEAN_RUINS_MAP, MapDecorationTypes.OCEAN_RUIN_WARM)),
            Map.entry("minecraft:on_treasure_maps", new VanillaMap(Items.BURIED_TREASURE_MAP, MapDecorationTypes.RED_X)),
            Map.entry("minecraft:on_abandoned_camp_bamboo_jungle", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP)),
            Map.entry("minecraft:on_abandoned_camp_birch_forest", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP)),
            Map.entry("minecraft:on_abandoned_camp_cherry_grove", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP)),
            Map.entry("minecraft:on_abandoned_camp_dappled_forest", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP)),
            Map.entry("minecraft:on_abandoned_camp_flower_forest", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP)),
            Map.entry("minecraft:on_abandoned_camp_pale_garden", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP)),
            Map.entry("minecraft:on_abandoned_camp_swamp", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP)),
            Map.entry("minecraft:on_abandoned_camp_windswept_forest", new VanillaMap(Items.ABANDONED_CAMP_MAP, MapDecorationTypes.ABANDONED_CAMP))
    );

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);
        ItemStack currency = getItemStackFromJson(JsonFields.requireObject(json, OWNER, "priceIn"));
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, OWNER, "buy"));

        if (json.has(GIVES) && json.has("name")) {
            VillagerTradingPlus.LOGGER.warn(
                    "sell_map \"name\" has no effect next to \"gives\"; set the name in \"gives\"{}.",
                    TradeOfferManager.fileSuffix()
            );
        }

        if (!json.has(GIVEN_ITEM_MODIFIER)) {
            return fromStructureTag(json, currency, buy);
        }

        if (!json.has(GIVES)) {
            throw new TradeParseException("\"" + GIVEN_ITEM_MODIFIER + "\" needs \"" + GIVES + "\"");
        }

        ItemStackTemplate gives = VanillaCodecs.decode(ItemStackTemplate.CODEC, json.get(GIVES), GIVES);
        Holder<LootItemFunction> modifier = VanillaCodecs.decode(
                LootItemFunctions.CODEC,
                json.get(GIVEN_ITEM_MODIFIER),
                GIVEN_ITEM_MODIFIER
        );
        VanillaCodecs.validateForTrade(modifier.value(), GIVEN_ITEM_MODIFIER);
        return new Factory(
                buy,
                currency,
                gives,
                modifier,
                maxUses,
                experience,
                priceMultiplier
        );
    }

    private Factory fromStructureTag(JsonObject json, ItemStack currency, ItemStack buy) {
        String requested = JsonFields.requireString(json, OWNER, "structure_id");
        String tagId = RENAMED_MAP_TAGS.getOrDefault(requested, requested);
        Identifier id = Identifier.tryParse(tagId);

        if (id == null) {
            throw new TradeParseException("invalid \"structure_id\": " + requested);
        }

        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, id);
        HolderSet<Structure> destination = DatapackRegistries.lookup()
                .flatMap(lookup -> lookup.lookup(Registries.STRUCTURE))
                .flatMap(structures -> structures.get(tag))
                .orElseThrow(() -> new TradeParseException("unknown structure tag \"" + tagId + "\""));
        VanillaMap vanilla = VANILLA_MAPS.get(id.toString());
        ExplorationMapFunction.Builder map = ExplorationMapFunction.makeExplorationMap(destination)
                .setSearchRadius(VANILLA_TRADE_SEARCH_RADIUS)
                .setSkipKnownStructures(true);

        if (json.has(DECORATION)) {
            map.setMapDecoration(decoration(json.get(DECORATION).getAsString()));
        } else if (vanilla != null) {
            map.setMapDecoration(vanilla.decoration());
        } else {
            map.setMapDecoration(MapDecorationTypes.RED_X);
        }

        List<Holder<LootItemFunction>> modifiers = List.of(
                Holder.direct(map.build()),
                VillagerTrades.discardItemIfItsNot(VillagerTrades.anyValidMap())
        );
        Holder<LootItemFunction> modifier = FunctionUserBuilder.buildFunction(modifiers)
                .orElseThrow();
        return new Factory(
                buy,
                currency,
                structureMapGives(json, vanilla),
                modifier,
                maxUses,
                experience,
                priceMultiplier
        );
    }

    private static Holder<MapDecorationType> decoration(String raw) {
        Identifier id = Identifier.tryParse(raw);

        if (id == null) {
            throw new TradeParseException("invalid \"" + DECORATION + "\": " + raw);
        }

        return BuiltInRegistries.MAP_DECORATION_TYPE.get(id)
                .map(entry -> (Holder<MapDecorationType>) entry)
                .orElseThrow(() -> new TradeParseException("unknown map decoration \"" + raw + "\""));
    }

    private static ItemStackTemplate structureMapGives(JsonObject json, VanillaMap vanilla) {
        if (json.has(GIVES)) {
            return VanillaCodecs.decode(ItemStackTemplate.CODEC, json.get(GIVES), GIVES);
        }

        Item item = vanilla != null ? vanilla.item() : Items.FILLED_MAP;
        String name = readString(json, "name", "");

        if (name.isEmpty()) {
            return new ItemStackTemplate(item);
        }

        DataComponentPatch itemName = DataComponentPatch.builder()
                .set(DataComponents.ITEM_NAME, Component.translatable(name))
                .build();
        return new ItemStackTemplate(item, itemName);
    }

    private static class Factory implements TradeFactory, CatalogExpandable {
        private final ItemStack currency;
        private final ItemStack buy;
        private final ItemStackTemplate gives;
        private final Holder<LootItemFunction> modifier;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(
                ItemStack buy,
                ItemStack currency,
                ItemStackTemplate gives,
                Holder<LootItemFunction> modifier,
                int maxUses,
                int experience,
                float multiplier
        ) {
            this.buy = buy;
            this.currency = currency;
            this.gives = gives;
            this.modifier = modifier;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        @Override
        public MerchantOffer create(ServerLevel level, Entity trader, RandomSource random) {
            LootContext context = VanillaTradeFactory.context(level, trader, random, Optional.empty());
            ItemStack result = this.modifier.value().apply(this.gives.create(), context);

            if (result.isEmpty()) {
                return null;
            }

            Integer additionalCost = result.remove(DataComponents.ADDITIONAL_TRADE_COST);
            int price = Mth.clamp(
                    this.currency.getCount() + (additionalCost == null ? 0 : additionalCost),
                    0,
                    this.currency.getItem().getDefaultMaxStackSize()
            );

            if (price < 1) {
                return null;
            }

            return new MerchantOffer(
                    traded(this.currency.copyWithCount(price)),
                    tradedOrEmpty(this.buy),
                    result,
                    this.maxUses,
                    this.experience,
                    this.multiplier
            );
        }

        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            out.add(
                    this.currency,
                    this.buy,
                    this.gives.create(),
                    this.maxUses,
                    this.experience,
                    this.multiplier,
                    0
            );
        }
    }
}
