package com.lion.villagertradingplus.tradeoffers.util;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;

import java.lang.ref.WeakReference;
import java.util.Optional;

/// Access to the dynamic registries while trade files parse.
/// DataPackContents.reload fills every dynamic registry before it builds the reloaders.
/// The DataPackContents constructor captures the manager, see DataPackContentsMixin.
/// The reference is weak, because a strong static one would keep the whole registry set alive.
public final class DatapackRegistries {

    private static volatile WeakReference<DynamicRegistryManager> current = new WeakReference<>(null);

    private DatapackRegistries() {}

    public static void set(DynamicRegistryManager registryManager) {
        current = new WeakReference<>(registryManager);
    }

    /// Empty means a trade parse outside a datapack load. Callers report it and skip the feature.
    public static <T> Optional<Registry<T>> registry(RegistryKey<? extends Registry<? extends T>> key) {
        DynamicRegistryManager manager = current.get();
        return manager == null ? Optional.empty() : manager.getOptional(key);
    }

    /// The same object that registry reads, handed out whole for RegistryOps.
    public static Optional<RegistryWrapper.WrapperLookup> lookup() {
        return Optional.ofNullable(current.get());
    }
}
