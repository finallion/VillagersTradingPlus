package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.TradeOfferRegistryLoader;
import com.lion.villagertradingplus.tradeoffers.util.DatapackRegistries;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableServerResources.class)
public class DataPackContentsMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void villagertradingplus$captureDatapackRegistries(
            LayeredRegistryAccess<RegistryLayer> dynamicRegistries,
            HolderLookup.Provider registries,
            FeatureFlagSet enabledFeatures,
            Commands.CommandSelection environment,
            List<Registry.PendingTags<?>> pendingTagLoads,
            PermissionSet permissions,
            List<DataComponentInitializers.PendingComponents<?>> pendingComponents,
            CallbackInfo ci
    ) {
        DatapackRegistries.set(dynamicRegistries.compositeAccess());
    }

    @Inject(method = "updateComponentsAndStaticRegistryTags", at = @At("TAIL"))
    private void villagertradingplus$buildTrades(CallbackInfo ci) {
        TradeOfferRegistryLoader.finish();
    }
}
