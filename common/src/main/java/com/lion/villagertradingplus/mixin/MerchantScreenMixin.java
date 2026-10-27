package com.lion.villagertradingplus.mixin;

import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.client.TradeControlsClientState;
import com.lion.villagertradingplus.client.screen.TradeCatalogPanel;
import com.lion.villagertradingplus.platform.ModCheck;
import com.lion.villagertradingplus.tradeoffers.gui.TradeControlsPayload;
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

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends HandledScreen<MerchantScreenHandler> {

    private MerchantScreenMixin() {
        super(null, null, null);
    }

    @Unique private static final int villagertradingplus$CONTROLS_REQUEST_ID = 140;
    @Unique private static final int villagertradingplus$VISIBLE_OFFER_ROWS = 7;
    @Unique private static Boolean villagertradingplus$recipeViewer;

    /// MerchantScreen.mouseClicked is the only place that clears this flag, so cancelling it strands a drag.
    @Shadow
    private boolean scrolling;

    /// Vanilla indexes the offer list with this field and does not check bounds.
    @Shadow
    private int selectedIndex;

    /// Vanilla draws and indexes the rows from this offset and does not check bounds.
    @Shadow
    private int indexStartOffset;

    @Unique private int villagertradingplus$level = 1;
    @Unique private ButtonWidget villagertradingplus$levelDisplay;
    @Unique private ButtonWidget villagertradingplus$levelDown;
    @Unique private ButtonWidget villagertradingplus$levelUp;
    @Unique private ButtonWidget villagertradingplus$setLevelButton;
    @Unique private ButtonWidget villagertradingplus$rerollLevelButton;
    @Unique private ButtonWidget villagertradingplus$rerollAllButton;
    @Unique private ButtonWidget villagertradingplus$catalogButton;
    @Unique private TradeCatalogPanel villagertradingplus$catalog;
    @Unique private boolean villagertradingplus$shortLabels;
    @Unique private boolean villagertradingplus$controlsRequested;
    @Unique private int villagertradingplus$controlsVersion = -1;

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
            // A window resize runs init again, so reset only once.
            TradeCatalogClientState.clear();
        }

        if (!this.villagertradingplus$controlsRequested) {
            this.villagertradingplus$controlsRequested = true;
            TradeControlsClientState.clear();
            villagertradingplus$click(villagertradingplus$CONTROLS_REQUEST_ID);
        }

        villagertradingplus$levelDisplay = villagertradingplus$addButton(villagertradingplus$levelText(), b -> {});
        villagertradingplus$levelDisplay.active = false;
        villagertradingplus$levelDown = villagertradingplus$addButton(Text.literal("<"), b -> villagertradingplus$changeLevel(-1));
        villagertradingplus$levelUp = villagertradingplus$addButton(Text.literal(">"), b -> villagertradingplus$changeLevel(1));

        if (allowSetLevel) {
            villagertradingplus$setLevelButton = villagertradingplus$addButton(
                    villagertradingplus$label("set_level"),
                    b -> villagertradingplus$click(100 + villagertradingplus$level)
            );
        }

        if (allowReroll) {
            villagertradingplus$rerollLevelButton = villagertradingplus$addButton(
                    villagertradingplus$label("reroll_level"),
                    b -> villagertradingplus$click(110 + villagertradingplus$level)
            );
            villagertradingplus$rerollAllButton = villagertradingplus$addButton(
                    villagertradingplus$rerollAllText(),
                    b -> villagertradingplus$click(120)
            );
        }

        if (allowView) {
            villagertradingplus$catalogButton = villagertradingplus$addButton(
                    villagertradingplus$label("catalog_button"),
                    b -> villagertradingplus$toggleCatalog()
            );
        }

        villagertradingplus$layoutControls();
    }

    @Unique
    private ButtonWidget villagertradingplus$addButton(Text text, ButtonWidget.PressAction action) {
        ButtonWidget button = addDrawableChild(
                ButtonWidget.builder(text, action)
                        .dimensions(0, 0, 20, 20)
                        .build()
        );
        button.visible = false;
        return button;
    }

    @Unique
    private void villagertradingplus$layoutControls() {
        TradeControlsPayload controls = TradeControlsClientState.current();
        this.villagertradingplus$controlsVersion = TradeControlsClientState.version();

        boolean hasLevels = controls != null && controls.maxLevel() > 1;
        boolean showSetLevel = hasLevels && controls.allowSetLevel() && villagertradingplus$setLevelButton != null;
        boolean showRerollLevel = hasLevels && controls.allowReroll() && villagertradingplus$rerollLevelButton != null;
        boolean showRerollAll = controls != null && controls.allowReroll() && villagertradingplus$rerollAllButton != null;
        boolean showCatalog = controls != null && controls.allowView() && villagertradingplus$catalogButton != null;
        boolean showLevelRow = hasLevels && (showSetLevel || showRerollLevel || showCatalog);

        this.villagertradingplus$level = MathHelper.clamp(this.villagertradingplus$level, 1, villagertradingplus$maxLevel());

        int step = 23;
        int gap = 5;
        int margin = 2;
        int maxWidth = 110;
        int minWidth = 68;

        int gutter = this.width - (this.x + this.backgroundWidth) - gap - margin;
        int rows = (showLevelRow ? 1 : 0)
                + (showSetLevel ? 1 : 0)
                + (showRerollLevel ? 1 : 0)
                + (showRerollAll ? 1 : 0)
                + (showCatalog ? 1 : 0);

        if (villagertradingplus$recipeViewerLoaded()
                && villagertradingplus$layoutBelow(showLevelRow, showSetLevel, showRerollLevel, showRerollAll, showCatalog)) {
            return;
        }

        int w;
        int baseX;
        int row;

        if (gutter >= minWidth) {
            w = Math.min(maxWidth, gutter);
            baseX = this.x + this.backgroundWidth + gap;
            row = this.y;
        } else {
            w = maxWidth;
            baseX = Math.max(margin, Math.min(this.x, this.width - w - margin));
            row = Math.min(this.y + this.backgroundHeight + 4, this.height - rows * step - margin);
        }

        row = Math.max(margin, row);

        // Full labels do not fit below 74 px. Use the short labels.
        this.villagertradingplus$shortLabels = w < 90;
        villagertradingplus$refreshMessages();

        int arrowWidth = villagertradingplus$shortLabels ? 16 : 20;
        villagertradingplus$place(villagertradingplus$levelDisplay, showLevelRow, baseX + arrowWidth + 4, row, w - 2 * arrowWidth - 8);
        villagertradingplus$place(villagertradingplus$levelDown, showLevelRow, baseX, row, arrowWidth);
        villagertradingplus$place(villagertradingplus$levelUp, showLevelRow, baseX + w - arrowWidth, row, arrowWidth);

        if (showLevelRow) {
            row += step;
        }

        villagertradingplus$place(villagertradingplus$setLevelButton, showSetLevel, baseX, row, w);

        if (showSetLevel) {
            row += step;
        }

        villagertradingplus$place(villagertradingplus$rerollLevelButton, showRerollLevel, baseX, row, w);

        if (showRerollLevel) {
            row += step;
        }

        villagertradingplus$place(villagertradingplus$rerollAllButton, showRerollAll, baseX, row, w);

        if (showRerollAll) {
            row += step;
        }

        villagertradingplus$place(villagertradingplus$catalogButton, showCatalog, baseX, row, w);
    }

    @Unique
    private boolean villagertradingplus$layoutBelow(
            boolean showLevelRow,
            boolean showSetLevel,
            boolean showRerollLevel,
            boolean showRerollAll,
            boolean showCatalog
    ) {
        ButtonWidget[] buttons = {
                null,
                villagertradingplus$setLevelButton,
                villagertradingplus$rerollLevelButton,
                villagertradingplus$rerollAllButton,
                villagertradingplus$catalogButton
        };
        boolean[] shown = {showLevelRow, showSetLevel, showRerollLevel, showRerollAll, showCatalog};
        // The level row counts 1.5 times a button, so weights are in half units.
        int[] weights = {3, 2, 2, 2, 2};
        int[] order = new int[5];
        int count = 0;

        for (int i = 0; i < shown.length; i++) {
            if (shown[i]) {
                order[count++] = i;
            }
        }

        if (count == 0) {
            return false;
        }

        int step = 23;
        int gap = 4;
        int margin = 2;
        int minUnit = 23;
        int maxUnit = 55;
        int shortBelow = 45;
        int available = Math.min(this.width - 2 * margin, 360);
        int top = this.y + this.backgroundHeight + 4;

        for (int lines = 1; lines <= 2; lines++) {
            if (top + (lines - 1) * step + 20 > this.height - margin) {
                return false;
            }

            int perLine = (count + lines - 1) / lines;
            int unit = maxUnit;

            for (int line = 0; line < lines; line++) {
                int from = line * perLine;
                int to = Math.min(count, from + perLine);
                int weightSum = 0;

                for (int i = from; i < to; i++) {
                    weightSum += weights[order[i]];
                }

                unit = Math.min(unit, (available - gap * (to - from - 1)) / weightSum);
            }

            if (unit < minUnit) {
                continue;
            }

            this.villagertradingplus$shortLabels = unit < shortBelow;
            villagertradingplus$refreshMessages();

            int arrowWidth = villagertradingplus$shortLabels ? 16 : 20;

            for (int line = 0; line < lines; line++) {
                int from = line * perLine;
                int to = Math.min(count, from + perLine);
                int used = gap * (to - from - 1);

                for (int i = from; i < to; i++) {
                    used += unit * weights[order[i]];
                }

                int bx = Math.max(margin, Math.min(this.x + this.backgroundWidth / 2 - used / 2, this.width - used - margin));
                int by = top + line * step;

                for (int i = from; i < to; i++) {
                    int kind = order[i];
                    int w = unit * weights[kind];

                    if (kind == 0) {
                        villagertradingplus$place(villagertradingplus$levelDisplay, true, bx + arrowWidth + 4, by, w - 2 * arrowWidth - 8);
                        villagertradingplus$place(villagertradingplus$levelDown, true, bx, by, arrowWidth);
                        villagertradingplus$place(villagertradingplus$levelUp, true, bx + w - arrowWidth, by, arrowWidth);
                    } else {
                        villagertradingplus$place(buttons[kind], true, bx, by, w);
                    }

                    bx += w + gap;
                }
            }

            for (int kind = 0; kind < shown.length; kind++) {
                if (shown[kind]) {
                    continue;
                }

                if (kind == 0) {
                    villagertradingplus$place(villagertradingplus$levelDisplay, false, 0, 0, 0);
                    villagertradingplus$place(villagertradingplus$levelDown, false, 0, 0, 0);
                    villagertradingplus$place(villagertradingplus$levelUp, false, 0, 0, 0);
                } else {
                    villagertradingplus$place(buttons[kind], false, 0, 0, 0);
                }
            }

            return true;
        }

        return false;
    }

    @Unique
    private static boolean villagertradingplus$recipeViewerLoaded() {
        if (villagertradingplus$recipeViewer == null) {
            villagertradingplus$recipeViewer = ModCheck.isModLoaded("jei")
                    || ModCheck.isModLoaded("roughlyenoughitems")
                    || ModCheck.isModLoaded("emi");
        }

        return villagertradingplus$recipeViewer;
    }

    @Unique
    private void villagertradingplus$place(ButtonWidget button, boolean show, int x, int y, int width) {
        if (button == null) {
            return;
        }

        button.visible = show;

        if (show) {
            button.setX(x);
            button.setY(y);
            button.setWidth(width);
        }
    }

    @Unique
    private void villagertradingplus$syncControls() {
        if (this.villagertradingplus$controlsRequested && this.villagertradingplus$controlsVersion != TradeControlsClientState.version()) {
            villagertradingplus$layoutControls();
        }
    }

    @Unique
    private int villagertradingplus$maxLevel() {
        TradeControlsPayload controls = TradeControlsClientState.current();
        return controls == null ? 5 : Math.max(1, controls.maxLevel());
    }

    @Unique
    private Text villagertradingplus$rerollAllText() {
        return villagertradingplus$shortLabels
                ? Text.translatable("gui.villagertradingplus.reroll_all_short")
                : Text.translatable("gui.villagertradingplus.reroll_all");
    }

    @Unique
    private Text villagertradingplus$label(String key) {
        return Text.translatable(
                "gui.villagertradingplus." + key + (villagertradingplus$shortLabels ? "_short" : ""),
                villagertradingplus$level
        );
    }

    /// A re-roll can shorten the offer list, and render has no bounds check.
    @Inject(method = "render", at = @At("HEAD"))
    private void villagertradingplus$clampSelectedIndex(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        villagertradingplus$syncControls();

        int size = this.getScreenHandler().getRecipes().size();

        if (this.selectedIndex >= size) {
            this.selectedIndex = Math.max(0, size - 1);
        }

        villagertradingplus$clampScrollOffset(size);
    }

    @Unique
    private void villagertradingplus$clampScrollOffset(int size) {
        int maxOffset = Math.max(0, size - villagertradingplus$VISIBLE_OFFER_ROWS);
        this.indexStartOffset = Math.max(0, Math.min(this.indexStartOffset, maxOffset));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void villagertradingplus$renderCatalog(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        if (this.villagertradingplus$catalog == null) {
            return;
        }

        villagertradingplus$layoutCatalog();
        this.villagertradingplus$catalog.render(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void villagertradingplus$catalogClick(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        villagertradingplus$clampScrollOffset(this.getScreenHandler().getRecipes().size());

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
        villagertradingplus$level = MathHelper.clamp(villagertradingplus$level + delta, 1, villagertradingplus$maxLevel());
        villagertradingplus$refreshMessages();
    }

    @Unique
    private void villagertradingplus$refreshMessages() {
        if (villagertradingplus$levelDisplay != null) {
            villagertradingplus$levelDisplay.setMessage(villagertradingplus$levelText());
        }

        if (villagertradingplus$setLevelButton != null) {
            villagertradingplus$setLevelButton.setMessage(villagertradingplus$label("set_level"));
        }

        if (villagertradingplus$rerollLevelButton != null) {
            villagertradingplus$rerollLevelButton.setMessage(villagertradingplus$label("reroll_level"));
        }

        if (villagertradingplus$rerollAllButton != null) {
            villagertradingplus$rerollAllButton.setMessage(villagertradingplus$rerollAllText());
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
