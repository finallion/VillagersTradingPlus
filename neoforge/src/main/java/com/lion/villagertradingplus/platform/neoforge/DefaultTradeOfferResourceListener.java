package com.lion.villagertradingplus.platform.neoforge;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

public class DefaultTradeOfferResourceListener extends SimpleJsonResourceReloadListener<JsonElement> {

    public DefaultTradeOfferResourceListener() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("default_villager_trades"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, ProfilerFiller profiler) {
        TradeOfferRegistryLoader.stageDefaultFiles(loader);
    }
}
