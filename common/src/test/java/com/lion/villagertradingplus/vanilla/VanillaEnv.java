package com.lion.villagertradingplus.vanilla;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.lion.villagertradingplus.tradeoffers.TradeOfferManager;
import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.commands.Commands;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

public final class VanillaEnv {

    private static VanillaEnv instance;

    public final ResourceManager resources;
    public final RegistryAccess.Frozen registries;
    public final ServerLevel level;

    private VanillaEnv() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        this.resources = new MultiPackResourceManager(
                PackType.SERVER_DATA,
                List.<PackResources>of(ServerPacksSource.createVanillaPackSource().fullResources())
        );

        LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
        List<Registry.PendingTags<?>> pending = TagLoader.loadTagsForExistingRegistries(
                resources,
                layers.getLayer(RegistryLayer.STATIC)
        );

        List<HolderLookup.RegistryLookup<?>> lookups =
                TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLD), pending);
        layers = layers.replaceFrom(
                RegistryLayer.WORLD,
                RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join()
        );

        lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.DIMENSIONS), pending);
        layers = layers.replaceFrom(
                RegistryLayer.DIMENSIONS,
                RegistryDataLoader.load(resources, lookups, RegistryDataLoader.DIMENSION_REGISTRIES, Runnable::run).join()
        );

        this.registries = layers.compositeAccess();

        ReloadableServerResources reloadable = ReloadableServerResources.loadResources(
                this.resources,
                layers,
                pending,
                FeatureFlags.VANILLA_SET,
                Commands.CommandSelection.DEDICATED,
                PermissionSet.ALL_PERMISSIONS,
                Runnable::run,
                Runnable::run
        ).join();
        reloadable.updateComponentsAndStaticRegistryTags();

        DatapackRegistries.set(this.registries);
        TradeOfferManager.registerTradeOffers();
        this.level = mockLevel(this.registries, reloadable.fullRegistries());
    }

    public static synchronized VanillaEnv get() {
        if (instance == null) {
            try {
                instance = new VanillaEnv();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        return instance;
    }

    private static ServerLevel mockLevel(RegistryAccess.Frozen registries, ReloadableServerRegistries.Holder holder) {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.reloadableRegistries()).thenReturn(holder);

        ServerLevel level = mock(ServerLevel.class);
        when(level.registryAccess()).thenReturn(registries);
        when(level.getServer()).thenReturn(server);
        return level;
    }

    /// A villager of the given type. The random source is read on every call, so a test can swap it.
    public Villager villager(ResourceKey<VillagerType> type, Supplier<RandomSource> random) {
        Villager villager = mock(Villager.class);
        when(villager.getRandom()).thenAnswer(call -> random.get());
        when(villager.position()).thenReturn(Vec3.ZERO);
        when(villager.level()).thenReturn(level);
        Holder<VillagerType> holder = registries.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(type);
        doReturn(holder).when(villager).get(DataComponents.VILLAGER_VARIANT);
        return villager;
    }

    public WanderingTrader wanderingTrader(Supplier<RandomSource> random) {
        WanderingTrader trader = mock(WanderingTrader.class);
        when(trader.getRandom()).thenAnswer(call -> random.get());
        when(trader.position()).thenReturn(Vec3.ZERO);
        when(trader.level()).thenReturn(level);
        return trader;
    }

    public Map<String, JsonElement> json(String directory) throws IOException {
        Map<String, JsonElement> files = new TreeMap<>();

        for (Map.Entry<Identifier, Resource> entry : resources.listResources(
                directory,
                id -> id.getPath().endsWith(".json")
        ).entrySet()) {
            if (!"minecraft".equals(entry.getKey().getNamespace())) {
                continue;
            }

            String path = entry.getKey().getPath();
            String key = path.substring(directory.length() + 1, path.length() - ".json".length());

            try (InputStream stream = entry.getValue().open()) {
                files.put(key, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        }

        return files;
    }
}
