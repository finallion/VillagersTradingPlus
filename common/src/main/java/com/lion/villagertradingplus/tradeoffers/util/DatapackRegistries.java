package com.lion.villagertradingplus.tradeoffers.util;

import java.lang.ref.WeakReference;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

/// Access to the dynamic registries while trade files parse. The reload hook sets them after the
/// item components bind, and the listeners only stage the files.
public final class DatapackRegistries {

    private static volatile WeakReference<RegistryAccess> current = new WeakReference<>(null);

    private DatapackRegistries() {
    }

    public static void set(RegistryAccess registryManager) {
        current = new WeakReference<>(registryManager);
    }

    /// Empty when trade files parse outside a datapack load.
    public static <T> Optional<Registry<T>> registry(ResourceKey<? extends Registry<? extends T>> key) {
        RegistryAccess manager = current.get();
        return manager == null ? Optional.empty() : manager.lookup(key);
    }

    /// Same object that [#registry] reads. Handed out whole for [RegistryOps].
    public static Optional<HolderLookup.Provider> lookup() {
        return Optional.ofNullable(current.get());
    }
}
