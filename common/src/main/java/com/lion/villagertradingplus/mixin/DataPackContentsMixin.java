package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// A mod reloader cannot receive the manager, hence the capture.
@Mixin(DataPackContents.class)
public class DataPackContentsMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void villagertradingplus$captureDatapackRegistries(
            DynamicRegistryManager.Immutable dynamicRegistryManager,
            FeatureSet enabledFeatures,
            CommandManager.RegistrationEnvironment environment,
            int functionPermissionLevel,
            CallbackInfo ci
    ) {
        DatapackRegistries.set(dynamicRegistryManager);
    }
}