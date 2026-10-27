package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class JsonSellStructureMapTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        TagKey<Structure> structure = TagKey.of(RegistryKeys.STRUCTURE, readIdentifier(json, "structure_id", ""));
        String name = readString(json, "name", "");
        ItemStack currency = getCostStackFromJson(json.get("priceIn").getAsJsonObject());
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_structure_map trade", "buy"));

        return new Factory(
                buy,
                currency,
                structure,
                name,
                decoration(json),
                maxUses,
                experience,
                priceMultiplier
        );
    }

    private static RegistryEntry<MapDecorationType> decoration(JsonObject json) {
        if (!json.has("decoration")) {
            return MapDecorationTypes.RED_X;
        }

        String raw = json.get("decoration").getAsString();
        Identifier id = Identifier.tryParse(raw);
        Optional<RegistryEntry.Reference<MapDecorationType>> entry =
                id == null ? Optional.empty() : Registries.MAP_DECORATION_TYPE.getEntry(id);

        if (entry.isEmpty()) {
            VillagerTradingPlus.LOGGER.warn(
                    "Unknown map decoration \"{}\" in a sell_map trade{}, using the red X.",
                    raw,
                    TradeOfferManager.fileSuffix()
            );
            return MapDecorationTypes.RED_X;
        }

        return entry.get();
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final ItemStack currency;
        private final ItemStack buy;
        private final String nameKey;
        private final TagKey<Structure> structure;
        private final RegistryEntry<MapDecorationType> decoration;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(
                ItemStack buy,
                ItemStack currency,
                TagKey<Structure> structure,
                String nameKey,
                RegistryEntry<MapDecorationType> decoration,
                int maxUses,
                int experience,
                float multiplier
        ) {
            this.buy = buy;
            this.currency = currency;
            this.structure = structure;
            this.nameKey = nameKey;
            this.decoration = decoration;
            this.maxUses = maxUses;
            this.experience = experience;
            this.multiplier = multiplier;
        }

        public TradeOffer create(Entity entity, net.minecraft.util.math.random.Random random) {
            if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
                return null;
            } else {
                BlockPos blockPos = serverWorld.locateStructure(this.structure, entity.getBlockPos(), 100, true);

                if (blockPos != null) {
                    ItemStack itemStack = FilledMapItem.createMap(serverWorld, blockPos.getX(), blockPos.getZ(), (byte)2, true, true);
                    FilledMapItem.fillExplorationMap(serverWorld, itemStack);
                    MapState.addDecorationsNbt(itemStack, blockPos, "+", this.decoration);
                    nameMap(itemStack);
                    return new TradeOffer(
                            traded(currency),
                            tradedOrEmpty(buy),
                            itemStack,
                            this.maxUses,
                            this.experience,
                            this.multiplier
                    );
                } else {
                    return null;
                }
            }
        }

        private void nameMap(ItemStack map) {
            if (!this.nameKey.isEmpty()) {
                map.set(DataComponentTypes.ITEM_NAME, Text.translatable(this.nameKey));
            }
        }

        /// Builds one row without touching the world, because create runs a 100-chunk structure search.
        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            ItemStack map = new ItemStack(Items.FILLED_MAP);
            nameMap(map);
            out.add(this.currency, this.buy, map, this.maxUses, this.experience, this.multiplier, 0);
        }
    }
}
