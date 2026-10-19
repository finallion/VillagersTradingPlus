package com.lion.villagertradingplus.tradeoffers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/// Immutable result of one reload. `defaults` replace a profession's vanilla trades. `additions` extend them.
public record TradeSnapshot(
        Map<ResourceKey<VillagerProfession>, Int2ObjectMap<List<TradeFactory>>> defaults,
        Map<ResourceKey<VillagerProfession>, Int2ObjectMap<List<TradeFactory>>> additions
) {

    public static final TradeSnapshot EMPTY = new TradeSnapshot(Map.of(), Map.of());

    public boolean replacesVanilla(ResourceKey<VillagerProfession> profession) {
        return defaults.containsKey(profession);
    }

    public boolean defines(ResourceKey<VillagerProfession> profession) {
        return defaults.containsKey(profession) || additions.containsKey(profession);
    }

    public List<TradeFactory> defaultsAt(ResourceKey<VillagerProfession> profession, int level) {
        return at(defaults, profession, level);
    }

    public List<TradeFactory> additionsAt(ResourceKey<VillagerProfession> profession, int level) {
        return at(additions, profession, level);
    }

    private static List<TradeFactory> at(
            Map<ResourceKey<VillagerProfession>, Int2ObjectMap<List<TradeFactory>>> source,
            ResourceKey<VillagerProfession> profession,
            int level
    ) {
        Int2ObjectMap<List<TradeFactory>> levels = source.get(profession);

        if (levels == null) {
            return List.of();
        }

        return levels.getOrDefault(level, List.of());
    }
}
