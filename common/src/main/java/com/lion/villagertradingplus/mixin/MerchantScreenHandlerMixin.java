package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.gui.TradeGuiActions;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Routes custom trade-screen button clicks to TradeGuiActions on the server.
/// It targets ScreenHandler, because onButtonClick exists only on the superclass.
/// The instanceof guard narrows the target back to merchant screens.
@Mixin(ScreenHandler.class)
public abstract class MerchantScreenHandlerMixin {

    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$onButtonClick(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MerchantScreenHandler handler)) {
            return;
        }
        Merchant merchant = ((MerchantScreenHandlerAccessor) handler).getMerchant();
        if (!(merchant instanceof MerchantEntity entity) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (TradeGuiActions.handle(serverPlayer, entity, id)) {
            cir.setReturnValue(true);
        }
    }
}
