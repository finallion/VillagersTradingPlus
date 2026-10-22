package com.lion.villagertradingplus.platform.forge;

import net.minecraftforge.fml.ModList;

public class ModCheckImpl {

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
