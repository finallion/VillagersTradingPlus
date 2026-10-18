package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.world.entity.Entity;

/// A factory that can list every offer it produces.
/// [CatalogBuilder#addSampled] samples other factories once with a fixed seed.
public interface CatalogExpandable {

    /// Stops when `isFull()` is true and reports the omitted rows with `countSkipped`.
    void expandCatalog(Entity merchant, CatalogBuilder out);
}
