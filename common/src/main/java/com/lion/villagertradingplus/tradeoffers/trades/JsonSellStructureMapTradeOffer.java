package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;

import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.NotNull;

public class JsonSellStructureMapTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeOffers.Factory deserialize(JsonObject json) {
        loadDefaultStats(json);

        TagKey<Structure> structure = TagKey.of(RegistryKeys.STRUCTURE, readIdentifier(json, "structure_id", ""));
        String name = readString(json, "name", "");
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_structure_map trade", "buy"));

        MapIcon.Type decoration = readDecoration(json);

        return new Factory(
                buy,
                currency,
                structure,
                name,
                decoration,
                maxUses,
                experience,
                priceMultiplier
        );
    }

    private static MapIcon.Type readDecoration(JsonObject json) {
        if (!json.has("decoration")) {
            return MapIcon.Type.RED_X;
        }

        String raw = json.get("decoration").getAsString();
        String key = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;

        for (MapIcon.Type type : MapIcon.Type.values()) {
            if (type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }

        VillagerTradingPlus.LOGGER.warn(
                "Unknown map decoration \"{}\" in a sell_map trade{}, using the red X.",
                raw,
                TradeOfferManager.fileSuffix()
        );
        return MapIcon.Type.RED_X;
    }

    private static class Factory implements TradeOffers.Factory, CatalogExpandable {
        private final ItemStack currency;
        private final ItemStack buy;
        private final String nameKey;
        private final MapIcon.Type decoration;
        private final TagKey<Structure> structure;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(
                ItemStack buy,
                ItemStack currency,
                TagKey<Structure> structure,
                String nameKey,
                MapIcon.Type decoration,
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
                            currency,
                            buy,
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
                map.setCustomName(Text.translatable(this.nameKey));
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
