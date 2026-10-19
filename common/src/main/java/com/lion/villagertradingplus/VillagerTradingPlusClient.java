package com.lion.villagertradingplus;

import com.lion.villagertradingplus.client.TradeCatalogClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/// Client entry point. It registers the packet receiver for the catalogue panel.
public class VillagerTradingPlusClient {

	@Environment(EnvType.CLIENT)
	public static void init() {
		TradeCatalogClientState.register();
	}
}
