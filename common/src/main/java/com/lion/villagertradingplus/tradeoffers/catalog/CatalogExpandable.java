package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.world.entity.Entity;

public interface CatalogExpandable {

    void expandCatalog(Entity merchant, CatalogBuilder out);
}
