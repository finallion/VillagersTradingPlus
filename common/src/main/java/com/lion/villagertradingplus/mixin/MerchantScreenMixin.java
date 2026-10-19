package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.client.screen.TradeCatalogPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    private MerchantScreenMixin() {
        super(null, null, null);
    }

    @Shadow
    private boolean isDragging;

    @Shadow
    private int shopItem;

    @Unique private int villagertradingplus$level = 1;
    @Unique private Button villagertradingplus$levelDisplay;
    @Unique private Button villagertradingplus$setLevelButton;
    @Unique private Button villagertradingplus$rerollLevelButton;
    @Unique private Button villagertradingplus$catalogButton;
    @Unique private TradeCatalogPanel villagertradingplus$catalog;
    @Unique private boolean villagertradingplus$shortLabels;

    @Inject(method = "init", at = @At("TAIL"))
    private void villagertradingplus$addControls(CallbackInfo ci) {
        boolean allowReroll = VillagerTradingPlus.CONFIG.allow_trade_reroll;
        boolean allowSetLevel = VillagerTradingPlus.CONFIG.allow_set_villager_level;
        boolean allowView = VillagerTradingPlus.CONFIG.allow_view_all_trades;

        if (!allowReroll && !allowSetLevel && !allowView) {
            return;
        }

        if (allowView && this.villagertradingplus$catalog == null) {
            this.villagertradingplus$catalog = new TradeCatalogPanel(Minecraft.getInstance());
            // A window resize runs init again, so reset only once.
            TradeCatalogClientState.clear();
        }

        int step = 23;
        int gap = 5;
        int margin = 2;
        int maxWidth = 110;
        int minWidth = 68;

        // The column adapts to the gutter width, which is only 102 units at GUI scale 4.
        int gutter = this.width - (this.leftPos + this.imageWidth) - gap - margin;
        int rows = 1 + (allowSetLevel ? 1 : 0) + (allowReroll ? 2 : 0) + (allowView ? 1 : 0);

        int w;
        int baseX;
        int row;

        if (gutter >= minWidth) {
            w = Math.min(maxWidth, gutter);
            baseX = this.leftPos + this.imageWidth + gap;
            row = this.topPos;
        } else {
            w = maxWidth;
            baseX = Math.max(margin, Math.min(this.leftPos, this.width - w - margin));
            row = Math.min(this.topPos + this.imageHeight + 4, this.height - rows * step - margin);
        }

        row = Math.max(margin, row);

        this.villagertradingplus$shortLabels = w < 90;

        int arrowWidth = villagertradingplus$shortLabels ? 16 : 20;
        villagertradingplus$levelDisplay = addRenderableWidget(
                Button.builder(villagertradingplus$levelText(), b -> {})
                        .bounds(baseX + arrowWidth + 4, row, w - 2 * arrowWidth - 8, 20)
                        .build()
        );
        villagertradingplus$levelDisplay.active = false;
        addRenderableWidget(
                Button.builder(Component.literal("<"), b -> villagertradingplus$changeLevel(-1))
                        .bounds(baseX, row, arrowWidth, 20)
                        .build()
        );
        addRenderableWidget(
                Button.builder(Component.literal(">"), b -> villagertradingplus$changeLevel(1))
                        .bounds(baseX + w - arrowWidth, row, arrowWidth, 20)
                        .build()
        );
        row += step;

        if (allowSetLevel) {
            villagertradingplus$setLevelButton = addRenderableWidget(
                    Button.builder(
                            villagertradingplus$label("set_level"),
                            b -> villagertradingplus$click(100 + villagertradingplus$level)
                    )
                            .bounds(baseX, row, w, 20)
                            .build()
            );
            row += step;
        }

        if (allowReroll) {
            villagertradingplus$rerollLevelButton = addRenderableWidget(
                    Button.builder(
                            villagertradingplus$label("reroll_level"),
                            b -> villagertradingplus$click(110 + villagertradingplus$level)
                    )
                            .bounds(baseX, row, w, 20)
                            .build()
            );
            row += step;
            addRenderableWidget(
                    Button.builder(
                            villagertradingplus$shortLabels
                                    ? Component.translatable("gui.villagertradingplus.reroll_all_short")
                                    : Component.translatable("gui.villagertradingplus.reroll_all"),
                            b -> villagertradingplus$click(120)
                    )
                            .bounds(baseX, row, w, 20)
                            .build()
            );
            row += step;
        }

        if (allowView) {
            villagertradingplus$catalogButton = addRenderableWidget(
                    Button.builder(
                            villagertradingplus$label("catalog_button"),
                            b -> villagertradingplus$toggleCatalog()
                    )
                            .bounds(baseX, row, w, 20)
                            .build()
            );
        }
    }

    @Unique
    private Component villagertradingplus$label(String key) {
        return Component.translatable(
                "gui.villagertradingplus." + key + (villagertradingplus$shortLabels ? "_short" : ""),
                villagertradingplus$level
        );
    }

    /// A re-roll can shorten the offer list while the screen is open.
    @Inject(method = "extractContents", at = @At("HEAD"))
    private void villagertradingplus$clampSelectedIndex(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        int size = this.getMenu().getOffers().size();

        if (this.shopItem >= size) {
            this.shopItem = Math.max(0, size - 1);
        }
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void villagertradingplus$renderCatalog(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }

        villagertradingplus$layoutCatalog();
        this.villagertradingplus$catalog.extract(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$catalogClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (this.villagertradingplus$catalog == null) {
            return;
        }

        villagertradingplus$layoutCatalog();

        if (!this.villagertradingplus$catalog.isOver(mouseX, mouseY)) {
            return;
        }

        // Vanilla drops the cursor stack on a click outside the GUI. Cancel press and release.
        this.isDragging = false;
        ((HandledScreenAccessor) this).villagertradingplus$setCancelNextRelease(true);

        int id = this.villagertradingplus$catalog.mouseClicked(mouseX, mouseY);

        if (id >= 0) {
            villagertradingplus$click(id);
        }

        cir.setReturnValue(true);
    }

    /// Vanilla scrolls the trade list from anywhere on screen, so the panel claims the wheel.
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$catalogScroll(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }

        villagertradingplus$layoutCatalog();

        if (this.villagertradingplus$catalog.isOver(mouseX, mouseY)) {
            this.villagertradingplus$catalog.mouseScrolled(verticalAmount);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void villagertradingplus$layoutCatalog() {
        this.villagertradingplus$catalog.layout(this.leftPos, this.topPos, this.width, this.height);
    }

    @Unique
    private void villagertradingplus$toggleCatalog() {
        int id = this.villagertradingplus$catalog.toggle(this.villagertradingplus$level);

        if (id >= 0) {
            villagertradingplus$click(id);
        }
    }

    @Unique
    private void villagertradingplus$changeLevel(int delta) {
        villagertradingplus$level = Mth.clamp(villagertradingplus$level + delta, 1, 5);

        if (villagertradingplus$levelDisplay != null) {
            villagertradingplus$levelDisplay.setMessage(villagertradingplus$levelText());
        }

        if (villagertradingplus$setLevelButton != null) {
            villagertradingplus$setLevelButton.setMessage(villagertradingplus$label("set_level"));
        }

        if (villagertradingplus$rerollLevelButton != null) {
            villagertradingplus$rerollLevelButton.setMessage(villagertradingplus$label("reroll_level"));
        }

        if (villagertradingplus$catalogButton != null) {
            villagertradingplus$catalogButton.setMessage(villagertradingplus$label("catalog_button"));
        }
    }

    @Unique
    private Component villagertradingplus$levelText() {
        return villagertradingplus$label("level");
    }

    @Unique
    private void villagertradingplus$click(int id) {
        Minecraft client = Minecraft.getInstance();

        if (client.gameMode != null) {
            client.gameMode.handleInventoryButtonClick(this.getMenu().containerId, id);
        }
    }
}
