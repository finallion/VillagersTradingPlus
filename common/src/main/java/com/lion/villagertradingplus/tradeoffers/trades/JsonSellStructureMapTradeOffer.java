package com.lion.villagertradingplus.tradeoffers.trades;

import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.lion.villagertradingplus.tradeoffers.util.TradeParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import com.lion.villagertradingplus.tradeoffers.TradeFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogBuilder;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogExpandable;
import org.jetbrains.annotations.NotNull;

public class JsonSellStructureMapTradeOffer extends JsonTradeOffer {

    @Override
    @NotNull
    public TradeFactory deserialize(JsonObject json) {
        loadDefaultStats(json);

        TagKey<Structure> structure = TagKey.create(Registries.STRUCTURE, readIdentifier(json, "structure_id", ""));
        String name = readString(json, "name", "");
        Holder<MapDecorationType> decoration = json.has("decoration")
                ? decoration(json.get("decoration").getAsString())
                : MapDecorationTypes.RED_X;
        ItemStack currency = getItemStackFromJson(json.get("priceIn").getAsJsonObject());
        ItemStack buy = getItemStackFromJson(JsonFields.requireObject(json, "sell_structure_map trade", "buy"));

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

    private static Holder<MapDecorationType> decoration(String raw) {
        Identifier id = Identifier.tryParse(raw);

        if (id == null) {
            throw new TradeParseException("invalid \"decoration\": " + raw);
        }

        return BuiltInRegistries.MAP_DECORATION_TYPE.get(id)
                .map(entry -> (Holder<MapDecorationType>) entry)
                .orElseThrow(() -> new TradeParseException("unknown map decoration \"" + raw + "\""));
    }

    private static class Factory implements TradeFactory, CatalogExpandable {
        private final ItemStack currency;
        private final ItemStack buy;
        private final String nameKey;
        private final TagKey<Structure> structure;
        private final Holder<MapDecorationType> decoration;
        private final int maxUses;
        private final int experience;
        private final float multiplier;

        public Factory(
                ItemStack buy,
                ItemStack currency,
                TagKey<Structure> structure,
                String nameKey,
                Holder<MapDecorationType> decoration,
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

        public MerchantOffer create(
                net.minecraft.server.level.ServerLevel world,
                Entity entity,
                net.minecraft.util.RandomSource random
        ) {
            if (!(entity.level() instanceof ServerLevel serverWorld)) {
                return null;
            } else {
                BlockPos blockPos = serverWorld.findNearestMapStructure(this.structure, entity.blockPosition(), 100, true);

                if (blockPos != null) {
                    ItemStack itemStack = MapItem.create(serverWorld, blockPos.getX(), blockPos.getZ(), (byte)2, true, true);
                    MapItem.renderBiomePreviewMap(serverWorld, itemStack);
                    MapItemSavedData.addTargetDecoration(itemStack, blockPos, "+", this.decoration);
                    nameMap(itemStack);
                    return new MerchantOffer(
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
                map.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable(this.nameKey));
            }
        }

        @Override
        public void expandCatalog(Entity merchant, CatalogBuilder out) {
            ItemStack map = new ItemStack(Items.FILLED_MAP);
            nameMap(map);
            out.add(this.currency, this.buy, map, this.maxUses, this.experience, this.multiplier, 0);
        }
    }
}
