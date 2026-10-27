package com.lion.villagertradingplus.vanilla;

import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoading;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.path.SymlinkFinder;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.World;

public final class TestWorld {

    private static final Map<Boolean, TestWorld> CACHE = new HashMap<>();

    public final DynamicRegistryManager.Immutable registries;
    public final ServerWorld world;

    private TestWorld(DynamicRegistryManager.Immutable registries, ServerWorld world) {
        this.registries = registries;
        this.world = world;
    }

    public static synchronized TestWorld get(boolean rebalance) throws Exception {
        TestWorld cached = CACHE.get(rebalance);

        if (cached != null) {
            DatapackRegistries.set(cached.registries);
            return cached;
        }

        TestWorld created = load(rebalance);
        CACHE.put(rebalance, created);
        return created;
    }

    private static TestWorld load(boolean rebalance) throws Exception {
        Path packs = Files.createTempDirectory("vtp-packs");
        ResourcePackManager manager = VanillaDataPackProvider.createManager(packs, new SymlinkFinder(path -> true));
        FeatureSet features = rebalance
                ? FeatureFlags.DEFAULT_ENABLED_FEATURES.combine(FeatureSet.of(FeatureFlags.TRADE_REBALANCE))
                : FeatureFlags.DEFAULT_ENABLED_FEATURES;
        List<String> enabled = rebalance ? List.of("vanilla", "trade_rebalance") : List.of("vanilla");
        DataConfiguration configuration = new DataConfiguration(new DataPackSettings(enabled, List.of()), features);
        SaveLoading.DataPacks dataPacks = new SaveLoading.DataPacks(manager, configuration, false, false);
        SaveLoading.ServerConfig serverConfig = new SaveLoading.ServerConfig(dataPacks, CommandManager.RegistrationEnvironment.DEDICATED, 4);

        CompletableFuture<Object[]> future = SaveLoading.load(
                serverConfig,
                context -> new SaveLoading.LoadContext<>(Unit.INSTANCE, context.dimensionsRegistryManager()),
                (resources, contents, registries, extra) -> new Object[]{contents, registries, resources},
                Util.getMainWorkerExecutor(),
                Runnable::run
        );
        Object[] loaded = future.get(120, TimeUnit.SECONDS);
        net.minecraft.server.DataPackContents contents = (net.minecraft.server.DataPackContents) loaded[0];
        contents.refresh();

        @SuppressWarnings("unchecked")
        net.minecraft.registry.CombinedDynamicRegistries<net.minecraft.registry.ServerDynamicRegistryType> combined =
                (net.minecraft.registry.CombinedDynamicRegistries<net.minecraft.registry.ServerDynamicRegistryType>) loaded[1];
        DynamicRegistryManager.Immutable registries = combined.getCombinedRegistryManager();
        DatapackRegistries.set(registries);

        return new TestWorld(registries, createWorld(registries, features));
    }

    private static ServerWorld createWorld(DynamicRegistryManager.Immutable registries, FeatureSet features) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

        SaveProperties properties = (SaveProperties) Proxy.newProxyInstance(
                TestWorld.class.getClassLoader(),
                new Class<?>[]{SaveProperties.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEnabledFeatures")) {
                        return features;
                    }

                    throw new UnsupportedOperationException(method.getName());
                }
        );

        MinecraftDedicatedServer server = (MinecraftDedicatedServer) unsafe.allocateInstance(MinecraftDedicatedServer.class);
        set(MinecraftServer.class, BrewingRecipeRegistry.class, server, BrewingRecipeRegistry.create(features));
        set(MinecraftServer.class, SaveProperties.class, server, properties);

        ServerWorld world = (ServerWorld) unsafe.allocateInstance(ServerWorld.class);
        set(World.class, DynamicRegistryManager.class, world, registries);
        set(ServerWorld.class, MinecraftServer.class, world, server);
        return world;
    }

    private static void set(Class<?> owner, Class<?> type, Object target, Object value) throws Exception {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == type && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                field.set(target, value);
                return;
            }
        }

        throw new IllegalStateException("no " + type.getSimpleName() + " field in " + owner.getSimpleName());
    }
}
