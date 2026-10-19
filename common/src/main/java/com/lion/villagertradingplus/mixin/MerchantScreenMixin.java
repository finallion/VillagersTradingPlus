package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.client.screen.TradeCatalogPanel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Adds the config-gated trade controls and hosts the catalogue panel at the left edge.
/// Control buttons use the vanilla ButtonClick packet. Only the catalogue reply needs a custom packet.
/// The mixin extends HandledScreen to use inherited members without shadows.
@Environment(EnvType.CLIENT)
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends HandledScreen<MerchantScreenHandler> {

    private MerchantScreenMixin() {
        super(null, null, null); // never executed; present only to satisfy the compiler
    }

    /// MerchantScreen.mouseClicked is the only place that clears this flag, so cancelling it strands a drag.
    @Shadow
    private boolean scrolling;

    /// Vanilla indexes the offer list with this field and does not check bounds.
    @Shadow
    private int selectedIndex;

    @Unique private int villagertradingplus$level = 1;
    @Unique private ButtonWidget villagertradingplus$levelDisplay;
    @Unique private ButtonWidget villagertradingplus$setLevelButton;
    @Unique private ButtonWidget villagertradingplus$rerollLevelButton;
    @Unique private ButtonWidget villagertradingplus$catalogButton;
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
            this.villagertradingplus$catalog = new TradeCatalogPanel(MinecraftClient.getInstance());
            // Init also re-runs on window resize, so reset only when the panel is absent.
            TradeCatalogClientState.clear();
        }

        int step = 23;
        int gap = 5;
        int margin = 2;
        int maxWidth = 110;
        int minWidth = 68;

        // The control column takes the right gutter and adapts to it, because the gutter varies with the GUI scale.
        int gutter = this.width - (this.x + this.backgroundWidth) - gap - margin;
        int rows = 1 + (allowSetLevel ? 1 : 0) + (allowReroll ? 2 : 0) + (allowView ? 1 : 0);

        int w;
        int baseX;
        int row;
        if (gutter >= minWidth) {
            w = Math.min(maxWidth, gutter);
            baseX = this.x + this.backgroundWidth + gap;
            row = this.y;
        } else {
            // Sit below the trade panel, or ride the bottom edge. Covering the hotbar harms less than covering trades.
            w = maxWidth;
            baseX = Math.max(margin, Math.min(this.x, this.width - w - margin));
            row = Math.min(this.y + this.backgroundHeight + 4, this.height - rows * step - margin);
        }
        row = Math.max(margin, row);

        // The full labels clip below about 74px, so the short variants apply.
        this.villagertradingplus$shortLabels = w < 90;

        // The arrows shrink with the labels, so the readout between them keeps a usable width.
        int arrowWidth = villagertradingplus$shortLabels ? 16 : 20;
        villagertradingplus$levelDisplay = addDrawableChild(ButtonWidget.builder(villagertradingplus$levelText(), b -> {})
                .dimensions(baseX + arrowWidth + 4, row, w - 2 * arrowWidth - 8, 20).build());
        villagertradingplus$levelDisplay.active = false;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"),
                b -> villagertradingplus$changeLevel(-1)).dimensions(baseX, row, arrowWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"),
                b -> villagertradingplus$changeLevel(1)).dimensions(baseX + w - arrowWidth, row, arrowWidth, 20).build());
        row += step;

        if (allowSetLevel) {
            villagertradingplus$setLevelButton = addDrawableChild(ButtonWidget.builder(
                            villagertradingplus$label("set_level"),
                            b -> villagertradingplus$click(100 + villagertradingplus$level))
                    .dimensions(baseX, row, w, 20).build());
            row += step;
        }

        if (allowReroll) {
            villagertradingplus$rerollLevelButton = addDrawableChild(ButtonWidget.builder(
                            villagertradingplus$label("reroll_level"),
                            b -> villagertradingplus$click(110 + villagertradingplus$level))
                    .dimensions(baseX, row, w, 20).build());
            row += step;
            addDrawableChild(ButtonWidget.builder(
                            villagertradingplus$shortLabels
                                    ? Text.translatable("gui.villagertradingplus.reroll_all_short")
                                    : Text.translatable("gui.villagertradingplus.reroll_all"),
                            b -> villagertradingplus$click(120))
                    .dimensions(baseX, row, w, 20).build());
            row += step;
        }

        if (allowView) {
            villagertradingplus$catalogButton = addDrawableChild(ButtonWidget.builder(
                            villagertradingplus$label("catalog_button"),
                            b -> villagertradingplus$toggleCatalog())
                    .dimensions(baseX, row, w, 20).build());
        }
    }

    @Unique
    private Text villagertradingplus$label(String key) {
        return Text.translatable("gui.villagertradingplus." + key + (villagertradingplus$shortLabels ? "_short" : ""),
                villagertradingplus$level);
    }

    /// MerchantScreen.render has no bounds check, and a shorter offer list can leave the selection past the end.
    @Inject(method = "render", at = @At("HEAD"))
    private void villagertradingplus$clampSelectedIndex(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int size = this.getScreenHandler().getRecipes().size();
        if (this.selectedIndex >= size) {
            this.selectedIndex = Math.max(0, size - 1);
        }
    }

    /// Drawn at TAIL, after the vanilla tooltip and the control buttons.
    @Inject(method = "render", at = @At("TAIL"))
    private void villagertradingplus$renderCatalog(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }
        villagertradingplus$layoutCatalog();
        this.villagertradingplus$catalog.render(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$catalogClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }
        villagertradingplus$layoutCatalog();
        if (!this.villagertradingplus$catalog.isOver(mouseX, mouseY)) {
            return;
        }

        // Vanilla throws the cursor stack on the ground for an outside click, so cancel both click halves.
        this.scrolling = false;
        ((HandledScreenAccessor) this).villagertradingplus$setCancelNextRelease(true);

        int id = this.villagertradingplus$catalog.mouseClicked(mouseX, mouseY);
        if (id >= 0) {
            villagertradingplus$click(id);
        }
        cir.setReturnValue(true);
    }

    /// Vanilla scrolls the trade list from anywhere on screen and never calls super, so the panel claims the wheel.
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$catalogScroll(double mouseX, double mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }
        villagertradingplus$layoutCatalog();
        if (this.villagertradingplus$catalog.isOver(mouseX, mouseY)) {
            this.villagertradingplus$catalog.mouseScrolled(amount);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void villagertradingplus$layoutCatalog() {
        this.villagertradingplus$catalog.layout(this.x, this.y, this.width, this.height);
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
        villagertradingplus$level = MathHelper.clamp(villagertradingplus$level + delta, 1, 5);
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
    private Text villagertradingplus$levelText() {
        return villagertradingplus$label("level");
    }

    @Unique
    private void villagertradingplus$click(int id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.clickButton(this.getScreenHandler().syncId, id);
        }
    }
}
