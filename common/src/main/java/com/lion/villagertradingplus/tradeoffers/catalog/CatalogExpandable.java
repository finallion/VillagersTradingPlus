package com.lion.villagertradingplus.tradeoffers.catalog;

import net.minecraft.entity.Entity;

/// Implemented by trade factories that can list every offer they produce.
/// Other factories go through CatalogBuilder#addSampled, which calls create once with a fixed seed.
/// Implementations must not change world state or run expensive lookups, such as structure searches.
/// The catalogue runs this on the server thread each time a player opens it.
public interface CatalogExpandable {

    /// Implementations stop when isFull() is true and report omitted rows with countSkipped.
    void expandCatalog(Entity merchant, CatalogBuilder out);
}
