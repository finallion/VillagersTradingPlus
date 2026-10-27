package com.lion.villagertradingplus.vanilla;

import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.registry.ReloadableRegistries;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.SaveLoading;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.path.SymlinkFinder;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.World;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class VanillaRuntime {

    private static VanillaRuntime instance;

    public final DynamicRegistryManager registries;
    public final TestWorld world;
    private final DataPackContents contents;

    private VanillaRuntime(DataPackContents contents, DynamicRegistryManager registries) throws Exception {
        this.contents = contents;
        this.registries = registries;
        this.world = allocate(TestWorld.class);
        this.world.registries = registries;
        this.world.features = FeatureFlags.DEFAULT_ENABLED_FEATURES;
        this.world.server = allocate(TestServer.class);
        this.world.server.contents = contents;
        set(World.class, this.world, "random", net.minecraft.util.math.random.Random.create(0L));
        this.world.brewing = BrewingRecipeRegistry.create(FeatureFlags.VANILLA_FEATURES);
        DatapackRegistries.set(registries);
    }

    public static synchronized VanillaRuntime get() {
        if (instance == null) {
            try {
                instance = boot();
            } catch (Exception e) {
                throw new IllegalStateException("could not boot Minecraft for the trade tests", e);
            }
        }

        return instance;
    }

    private static VanillaRuntime boot() throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();

        Path packs = Files.createTempDirectory("vtp-packs");
        ResourcePackManager manager = VanillaDataPackProvider.createManager(packs, new SymlinkFinder(path -> true));
        DataConfiguration configuration = new DataConfiguration(
                new DataPackSettings(List.of("vanilla"), List.of()),
                FeatureFlags.DEFAULT_ENABLED_FEATURES
        );
        SaveLoading.ServerConfig config = new SaveLoading.ServerConfig(
                new SaveLoading.DataPacks(manager, configuration, false, false),
                CommandManager.RegistrationEnvironment.DEDICATED,
                LeveledPermissionPredicate.OWNERS
        );

        Object[] loaded = SaveLoading.<Boolean, Object[]>load(
                config,
                context -> new SaveLoading.LoadContext<>(Boolean.TRUE, context.dimensionsRegistryManager()),
                (resources, contents, combined, extra) -> {
                    contents.applyPendingTagLoads();
                    return new Object[] {contents, combined.getCombinedRegistryManager()};
                },
                Runnable::run,
                Runnable::run
        ).get();

        return new VanillaRuntime((DataPackContents) loaded[0], (DynamicRegistryManager) loaded[1]);
    }

    public FakeVillager villager(RegistryKey<VillagerType> type) {
        try {
            FakeVillager villager = allocate(FakeVillager.class);
            set(Entity.class, villager, "world", this.world);
            set(Entity.class, villager, "random", net.minecraft.util.math.random.Random.create(0L));
            villager.type = type;
            return villager;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public WanderingTraderEntity wanderer() {
        try {
            WanderingTraderEntity trader = allocate(WanderingTraderEntity.class);
            set(Entity.class, trader, "world", this.world);
            set(Entity.class, trader, "random", net.minecraft.util.math.random.Random.create(0L));
            return trader;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static class TestWorld extends ServerWorld {
        DynamicRegistryManager registries;
        BrewingRecipeRegistry brewing;
        TestServer server;
        public FeatureSet features;

        private TestWorld() {
            super(null, null, null, null, null, null, false, 0L, null, false, null);
        }

        @Override
        public DynamicRegistryManager getRegistryManager() {
            return this.registries;
        }

        @Override
        public long getTimeOfDay() {
            return 0L;
        }

        @Override
        public MinecraftServer getServer() {
            return this.server;
        }

        @Override
        public BrewingRecipeRegistry getBrewingRecipeRegistry() {
            return this.brewing;
        }

        @Override
        public FeatureSet getEnabledFeatures() {
            return this.features;
        }
    }

    public static class TestServer extends MinecraftDedicatedServer {
        DataPackContents contents;

        private TestServer() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public ReloadableRegistries.Lookup getReloadableRegistries() {
            return this.contents.getReloadableRegistries();
        }
    }

    /// A villager of a chosen type that never ran its constructor.
    public static class FakeVillager extends VillagerEntity {
        RegistryKey<VillagerType> type;

        private FakeVillager() {
            super(null, null);
        }

        @Override
        public VillagerData getVillagerData() {
            return new VillagerData(
                    Registries.VILLAGER_TYPE.getOrThrow(this.type),
                    Registries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE),
                    1
            );
        }
    }
}
