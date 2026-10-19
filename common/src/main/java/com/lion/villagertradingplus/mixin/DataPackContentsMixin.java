package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.CommandManager;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Hands the dynamic registries of the loading world to DatapackRegistries.
/// The constructor has them before any resource reloader runs, so trade files parse with full enchantment registries.
/// A mod reloader cannot receive the manager, hence the capture.
@Mixin(DataPackContents.class)
public class DataPackContentsMixin {

    /// The descriptor must match the constructor exactly. Mixin checks it only at apply time, so a stale one crashes.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void villagertradingplus$captureDatapackRegistries(
            CombinedDynamicRegistries<ServerDynamicRegistryType> dynamicRegistries,
            RegistryWrapper.WrapperLookup registries,
            FeatureSet enabledFeatures,
            CommandManager.RegistrationEnvironment environment,
            List<Registry.PendingTagLoad<?>> pendingTagLoads,
            int functionPermissionLevel,
            CallbackInfo ci
    ) {
        DatapackRegistries.set(dynamicRegistries.getCombinedRegistryManager());
    }
}