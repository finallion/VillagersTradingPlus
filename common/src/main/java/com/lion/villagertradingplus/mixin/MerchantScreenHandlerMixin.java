package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.tradeoffers.gui.TradeGuiActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class MerchantScreenHandlerMixin {

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$onButtonClick(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MerchantMenu handler)) {
            return;
        }

        Merchant merchant = ((MerchantScreenHandlerAccessor) handler).getMerchant();

        if (!(merchant instanceof AbstractVillager entity) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (TradeGuiActions.handle(serverPlayer, entity, id)) {
            cir.setReturnValue(true);
        }
    }
}
