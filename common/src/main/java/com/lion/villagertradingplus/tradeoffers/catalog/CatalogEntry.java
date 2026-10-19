package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

public record CatalogEntry(
        ItemStack firstBuy,
        ItemStack secondBuy,
        ItemStack sell,
        int poolWeight,
        int poolTotalWeight,
        float poolShare,
        int tierPoolSize,
        int tierPicks,
        int maxUses,
        int villagerExperience,
        float priceMultiplier,
        int demand,
        int minPrice,
        int maxPrice,
        List<ConditionInfo> conditions,
        boolean conditionsMet
) {

    public boolean isPooled() {
        return this.poolTotalWeight > 0;
    }

    public boolean hasPriceRange() {
        return this.maxPrice > this.minPrice;
    }

    public void write(PacketByteBuf buf) {
        buf.writeItemStack(this.firstBuy);
        buf.writeItemStack(this.secondBuy);
        buf.writeItemStack(this.sell);
        buf.writeVarInt(this.poolWeight);
        buf.writeVarInt(this.poolTotalWeight);
        buf.writeFloat(this.poolShare);
        buf.writeVarInt(this.tierPoolSize);
        buf.writeVarInt(this.tierPicks);
        buf.writeVarInt(this.maxUses);
        buf.writeVarInt(this.villagerExperience);
        buf.writeFloat(this.priceMultiplier);
        buf.writeVarInt(this.demand);
        buf.writeVarInt(this.minPrice);
        buf.writeVarInt(this.maxPrice);
        buf.writeVarInt(this.conditions.size());
        for (ConditionInfo condition : this.conditions) {
            condition.write(buf);
        }
        buf.writeBoolean(this.conditionsMet);
    }

    public static CatalogEntry read(PacketByteBuf buf) {
        ItemStack firstBuy = buf.readItemStack();
        ItemStack secondBuy = buf.readItemStack();
        ItemStack sell = buf.readItemStack();
        int poolWeight = buf.readVarInt();
        int poolTotalWeight = buf.readVarInt();
        float poolShare = buf.readFloat();
        int tierPoolSize = buf.readVarInt();
        int tierPicks = buf.readVarInt();
        int maxUses = buf.readVarInt();
        int villagerExperience = buf.readVarInt();
        float priceMultiplier = buf.readFloat();
        int demand = buf.readVarInt();
        int minPrice = buf.readVarInt();
        int maxPrice = buf.readVarInt();

        int conditionCount = buf.readVarInt();
        List<ConditionInfo> conditions = new ArrayList<>(conditionCount);
        for (int i = 0; i < conditionCount; i++) {
            conditions.add(ConditionInfo.read(buf));
        }

        return new CatalogEntry(
                firstBuy,
                secondBuy,
                sell,
                poolWeight,
                poolTotalWeight,
                poolShare,
                tierPoolSize,
                tierPicks,
                maxUses,
                villagerExperience,
                priceMultiplier,
                demand,
                minPrice,
                maxPrice,
                List.copyOf(conditions),
                buf.readBoolean()
        );
    }
}
