package com.lion.villagertradingplus.platform.neoforge;

import net.neoforged.fml.ModList;

public class ModCheckImpl {

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
