package com.lion.villagertradingplus.api;

import com.lion.villagertradingplus.tradeoffers.TradePools;
import com.lion.villagertradingplus.tradeoffers.TradeSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/// Read-only view of the trades that VTP and vanilla offer to a profession.
public final class TradeLookup {

    private TradeLookup() {
    }

    public static int tradeCount(RegistryAccess registries, ResourceKey<VillagerProfession> profession, int level) {
        TradeSnapshot snapshot = TradePools.villagerSnapshot();
        int count = snapshot.defaultsAt(profession, level).size() + snapshot.additionsAt(profession, level).size();
        if (TradePools.replacesVanillaSet(snapshot, profession)) {
            return count;
        }
        return count + vanillaCount(registries, profession, level);
    }

    public static boolean hasTrades(RegistryAccess registries, ResourceKey<VillagerProfession> profession, int level) {
        return tradeCount(registries, profession, level) > 0;
    }

    /// True when VTP data replaces the vanilla trade sets of the profession.
    public static boolean isOwnedByVtp(ResourceKey<VillagerProfession> profession) {
        return TradePools.replacesVanillaSet(TradePools.villagerSnapshot(), profession);
    }

    private static int vanillaCount(RegistryAccess registries, ResourceKey<VillagerProfession> profession, int level) {
        return registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).get(profession)
                .map(Holder.Reference::value)
                .map(value -> TradePools.vanillaPool(registries, value.getTrades(level)).size())
                .orElse(0);
    }
}
