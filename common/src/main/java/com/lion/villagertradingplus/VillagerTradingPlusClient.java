package com.lion.villagertradingplus;

import com.lion.villagertradingplus.client.TradeCatalogClientState;

/// Client entry point. It registers the packet receiver for the catalogue panel.
public class VillagerTradingPlusClient {

	public static void init() {
		TradeCatalogClientState.register();
	}
}
