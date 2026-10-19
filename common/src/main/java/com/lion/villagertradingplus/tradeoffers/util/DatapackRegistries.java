package com.lion.villagertradingplus.tradeoffers.util;

import java.lang.ref.WeakReference;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

public final class DatapackRegistries {

    private static volatile WeakReference<RegistryAccess> current = new WeakReference<>(null);

    private DatapackRegistries() {}

    public static void set(RegistryAccess registryManager) {
        current = new WeakReference<>(registryManager);
    }

    public static <T> Optional<Registry<T>> registry(ResourceKey<? extends Registry<? extends T>> key) {
        RegistryAccess manager = current.get();
        return manager == null ? Optional.empty() : manager.lookup(key);
    }

    public static Optional<HolderLookup.Provider> lookup() {
        return Optional.ofNullable(current.get());
    }
}
