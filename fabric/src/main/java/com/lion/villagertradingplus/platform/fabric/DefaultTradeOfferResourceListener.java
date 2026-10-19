package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeMerger;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.profiler.Profiler;

import java.util.Map;

public class DefaultTradeOfferResourceListener extends JsonDataLoader<JsonElement> implements IdentifiableResourceReloadListener {

    public DefaultTradeOfferResourceListener() {
        super(Codecs.JSON_ELEMENT, ResourceFinder.json("default_villager_trades"));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of(VillagerTradingPlus.MOD_ID,"default_villager_data_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, Profiler profiler) {
        TradeOfferRegistryLoader.begin();

        loader.forEach((identifier, jsonElement) -> {
            if (!jsonElement.isJsonObject()) {
                return;
            }

            VillagerTradingPlus.LOGGER.debug("Deserializing default trades from: {}", identifier);

            TradeOfferManager.deserializeJson(identifier, jsonElement.getAsJsonObject());
        });

        TradeMerger.installDefaults(TradeOfferRegistryLoader.getRegistryForLoading());
    }
}