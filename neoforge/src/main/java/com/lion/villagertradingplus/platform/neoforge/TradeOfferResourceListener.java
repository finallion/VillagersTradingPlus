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

public class TradeOfferResourceListener extends SimpleJsonResourceReloadListener<JsonElement> {

    public TradeOfferResourceListener() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("villager_trades"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, ProfilerFiller profiler) {
        TradeOfferRegistryLoader.stageAdditionFiles(loader);
    }
}
