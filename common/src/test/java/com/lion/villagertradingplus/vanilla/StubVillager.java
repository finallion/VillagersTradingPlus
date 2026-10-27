package com.lion.villagertradingplus.vanilla;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerDataContainer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.World;

public final class StubVillager extends Entity implements VillagerDataContainer {

    private VillagerData data;

    public StubVillager(VillagerType type, World world) {
        super(EntityType.VILLAGER, world);
        this.data = new VillagerData(type, VillagerProfession.NONE, 1);
    }

    @Override
    public VillagerData getVillagerData() {
        return this.data;
    }

    @Override
    public void setVillagerData(VillagerData data) {
        this.data = data;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
