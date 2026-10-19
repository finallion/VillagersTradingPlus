package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.entity.Entity;

/// Runs on the server thread.
public interface CatalogExpandable {

    void expandCatalog(Entity merchant, CatalogBuilder out);
}
