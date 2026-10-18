package com.lion.villagertradingplus.platform.fabric;

import com.google.gson.JsonElement;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import java.util.Map;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

public class TradeOfferResourceListener extends SimpleJsonResourceReloadListener<JsonElement> implements IdentifiableResourceReloadListener {

    public TradeOfferResourceListener() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("villager_trades"));
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(VillagerTradingPlus.MOD_ID, "villager_data_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, ProfilerFiller profiler) {
        TradeOfferRegistryLoader.stageAdditionFiles(loader);
    }
}
