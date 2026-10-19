package com.lion.villagertradingplus.client.screen;

import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogEntry;
import com.lion.villagertradingplus.tradeoffers.catalog.ConditionInfo;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class TradeCatalogPanel {

    private static final int MAX_WIDTH = 176;
    /// Narrowest width: three 16px slots plus the arrow and padding.
    private static final int MIN_WIDTH = 73;
    private static final int COMPACT_WIDTH = 120;
    /// Matches the `imageHeight` of the trade screen, so the panels line up.
    private static final int HEIGHT = 166;

    private static final int DOCK_GAP = 2;
    private static final int SCREEN_MARGIN = 1;

    /// Row spacing uses the `x - 1` to `x + 17` slot span.
    private static final int SLOT_FOOTPRINT = 18;
    private static final int ARROW_WIDTH = 12;

    private static final int ROWS_PER_PAGE = 5;
    private static final int FIRST_ROW_Y = 42;
    private static final int ROW_STEP = 20;
    private static final int ROW_HEIGHT = 18;

    private static final int NAV_Y = 16;
    private static final int FOOTER_Y = 144;
    private static final int NAV_SIZE = 20;
    private static final int NAV_LEFT_X = 6;

    private static final int PANEL_BACKGROUND = 0xFFC6C6C6;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int ROW_HOVER = 0x40FFFFFF;
    private static final int ROW_GATED = 0x30FF7F27;
    private static final int BUTTON_FILL = 0xFF8B8B8B;
    private static final int BUTTON_HOVER = 0xFFA8A8A8;
    /// Colors are ARGB. A zero alpha byte draws nothing.
    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF707070;
    private static final int TEXT_GATED = 0xFFB06000;
    private static final int BUTTON_LABEL = 0xFFFFFFFF;

    private static final int CATALOG_REQUEST_BASE = 130;

    private final Minecraft client;

    private boolean visible;
    private int level = 1;
    private int firstRow;

    private int x;
    private int y;

    private int width = MAX_WIDTH;
    private boolean compact;
    private boolean overlapping;
    private int buyX;
    private int buy2X;
    private int arrowX;
    private int sellX;
    private int markerX;
    private int navRightX;
    private boolean showMarker;
    private boolean showChance;

    private int hoveredRow = -1;

    public TradeCatalogPanel(Minecraft client) {
        this.client = client;
        applyWidth(MAX_WIDTH);
    }

    public boolean isVisible() {
        return this.visible;
    }

    public int toggle(int requestedLevel) {
        this.visible = !this.visible;

        if (!this.visible) {
            return -1;
        }

        return requestLevel(requestedLevel);
    }

    public void hide() {
        this.visible = false;
    }

    private int requestLevel(int newLevel) {
        this.level = newLevel;
        this.firstRow = 0;
        TradeCatalogClientState.requesting();
        return CATALOG_REQUEST_BASE + newLevel;
    }

    public void layout(int merchantX, int merchantY, int screenWidth, int screenHeight) {
        applyWidth(Mth.clamp(merchantX - DOCK_GAP - SCREEN_MARGIN, MIN_WIDTH, MAX_WIDTH));

        this.x = Math.max(SCREEN_MARGIN, merchantX - DOCK_GAP - this.width);
        this.y = Mth.clamp(merchantY, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - HEIGHT - SCREEN_MARGIN));
        this.overlapping = this.x + this.width > merchantX;

        if (this.x + this.width > screenWidth) {
            this.x = Math.max(SCREEN_MARGIN, screenWidth - this.width - SCREEN_MARGIN);
        }
    }

    private void applyWidth(int panelWidth) {
        this.width = panelWidth;
        this.compact = panelWidth < COMPACT_WIDTH;

        boolean tight = panelWidth < 90;
        int slotGap = tight ? 1 : 3;
        int arrowPad = tight ? 2 : 4;

        int block = SLOT_FOOTPRINT + slotGap + SLOT_FOOTPRINT + arrowPad
                + ARROW_WIDTH + arrowPad + SLOT_FOOTPRINT;

        this.showMarker = panelWidth >= block + 14;
        this.showChance = panelWidth >= block + 52;
        int extras = (this.showMarker ? 10 : 0) + (this.showChance ? 36 : 0);

        int left = Math.max(1, (panelWidth - block - extras) / 2);
        this.buyX = left + 1;                                        // +1: slots draw from x - 1
        this.buy2X = this.buyX + SLOT_FOOTPRINT + slotGap;
        this.arrowX = this.buy2X + SLOT_FOOTPRINT + arrowPad;
        this.sellX = this.arrowX + ARROW_WIDTH + arrowPad;
        this.markerX = this.sellX + SLOT_FOOTPRINT + 2;

        this.navRightX = panelWidth - NAV_SIZE - 6;
    }

    public boolean isOver(double mouseX, double mouseY) {
        return this.visible
                && mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + HEIGHT;
    }

    public int mouseClicked(double mouseX, double mouseY) {
        int localX = (int) (mouseX - this.x);
        int localY = (int) (mouseY - this.y);
        int maxLevel = maxLevel();

        if (maxLevel > 1 && inRect(localX, localY, NAV_LEFT_X, NAV_Y, NAV_SIZE, NAV_SIZE)) {
            playClick();
            return requestLevel(wrapLevel(this.level - 1, maxLevel));
        }

        if (maxLevel > 1 && inRect(localX, localY, this.navRightX, NAV_Y, NAV_SIZE, NAV_SIZE)) {
            playClick();
            return requestLevel(wrapLevel(this.level + 1, maxLevel));
        }

        if (inRect(localX, localY, NAV_LEFT_X, FOOTER_Y, NAV_SIZE, NAV_SIZE)) {
            playClick();
            scrollRows(-ROWS_PER_PAGE);
            return -1;
        }

        if (inRect(localX, localY, this.navRightX, FOOTER_Y, NAV_SIZE, NAV_SIZE)) {
            playClick();
            scrollRows(ROWS_PER_PAGE);
            return -1;
        }

        return -1;
    }

    public void mouseScrolled(double amount) {
        scrollRows(amount > 0 ? -1 : 1);
    }

    private void scrollRows(int delta) {
        int size = entries().size();
        int lastPageStart = Math.max(0, (Mth.positiveCeilDiv(size, ROWS_PER_PAGE) - 1) * ROWS_PER_PAGE);
        this.firstRow = Mth.clamp(this.firstRow + delta, 0, lastPageStart);
    }

    private static int wrapLevel(int level, int maxLevel) {
        return Math.floorMod(level - 1, maxLevel) + 1;
    }

    private void playClick() {
        this.client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    public void extract(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        // A separate stratum draws the panel above the trade items.
        if (this.overlapping) {
            context.nextStratum();
        }

        drawPanel(context, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        Font font = this.client.font;
        drawFrame(context);

        // The server may clamp the tier, so adopt the served tier.
        TradeCatalogPacket.Payload payload = TradeCatalogClientState.current();

        if (payload != null && !TradeCatalogClientState.isLoading()) {
            this.level = payload.level();
        }

        Component title = label("catalog_title", this.level);
        context.text(font, title, this.x + (this.width - font.width(title)) / 2, this.y + 6, TEXT, false);

        if (maxLevel() > 1) {
            drawNavButton(context, NAV_LEFT_X, NAV_Y, "<", mouseX, mouseY);
            drawNavButton(context, this.navRightX, NAV_Y, ">", mouseX, mouseY);
        }

        if (TradeCatalogClientState.isLoading()) {
            drawCentered(context, Component.translatable("gui.villagertradingplus.catalog_loading"), FIRST_ROW_Y + 24, TEXT_DIM);
            this.hoveredRow = -1;
            return;
        }

        List<CatalogEntry> entries = entries();

        if (entries.isEmpty()) {
            drawCentered(context, label("catalog_empty"), FIRST_ROW_Y + 24, TEXT_DIM);
            this.hoveredRow = -1;
            return;
        }

        drawRows(context, entries, mouseX, mouseY);
        drawFooter(context, entries, mouseX, mouseY);

        // Draw last, above the vanilla tooltip pass.
        drawTooltip(context, entries, mouseX, mouseY);
    }

    private void drawFrame(GuiGraphicsExtractor context) {
        int left = this.x;
        int top = this.y;
        int right = left + this.width;
        int bottom = top + HEIGHT;

        context.fill(left, top, right, bottom, PANEL_BACKGROUND);
        context.fill(left, top, right, top + 1, PANEL_HIGHLIGHT);
        context.fill(left, top, left + 1, bottom, PANEL_HIGHLIGHT);
        context.fill(left, bottom - 1, right, bottom, PANEL_SHADOW);
        context.fill(right - 1, top, right, bottom, PANEL_SHADOW);
    }

    private void drawRows(GuiGraphicsExtractor context, List<CatalogEntry> entries, int mouseX, int mouseY) {
        Font font = this.client.font;
        this.hoveredRow = -1;

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = this.firstRow + row;

            if (index >= entries.size()) {
                break;
            }

            CatalogEntry entry = entries.get(index);
            int rowY = this.y + FIRST_ROW_Y + row * ROW_STEP;
            boolean hovered = mouseX >= this.x + 2 && mouseX < this.x + this.width - 2 && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 1;

            if (hovered) {
                this.hoveredRow = index;
            }

            if (!entry.conditionsMet()) {
                context.fill(this.x + 2, rowY - 1, this.x + this.width - 2, rowY + ROW_HEIGHT - 1, ROW_GATED);
            }

            if (hovered) {
                context.fill(this.x + 2, rowY - 1, this.x + this.width - 2, rowY + ROW_HEIGHT - 1, ROW_HOVER);
            }

            drawSlot(context, this.x + this.buyX, rowY);
            drawStack(context, entry.firstBuy(), this.x + this.buyX, rowY);

            if (!entry.secondBuy().isEmpty()) {
                drawSlot(context, this.x + this.buy2X, rowY);
                drawStack(context, entry.secondBuy(), this.x + this.buy2X, rowY);
            }

            context.text(font, Component.literal("->"), this.x + this.arrowX, rowY + 4, TEXT, false);
            drawSlot(context, this.x + this.sellX, rowY);
            drawStack(context, entry.sell(), this.x + this.sellX, rowY);

            if (this.showMarker && !entry.conditions().isEmpty()) {
                context.text(
                        font,
                        Component.literal("!"),
                        this.x + this.markerX,
                        rowY + 5,
                        entry.conditionsMet() ? TEXT_DIM : TEXT_GATED,
                        false
                );
            }

            if (this.showChance) {
                String chance = percent(overallChance(entry));
                context.text(
                        font,
                        Component.literal(chance),
                        this.x + this.width - 8 - font.width(chance),
                        rowY + 5,
                        TEXT_DIM,
                        false
                );
            }
        }
    }

    private void drawFooter(GuiGraphicsExtractor context, List<CatalogEntry> entries, int mouseX, int mouseY) {
        int pageCount = Math.max(1, Mth.positiveCeilDiv(entries.size(), ROWS_PER_PAGE));
        int page = this.firstRow / ROWS_PER_PAGE + 1;

        if (entries.size() > ROWS_PER_PAGE) {
            drawNavButton(context, NAV_LEFT_X, FOOTER_Y, "<", mouseX, mouseY);
            drawNavButton(context, this.navRightX, FOOTER_Y, ">", mouseX, mouseY);
        }

        TradeCatalogPacket.Payload payload = TradeCatalogClientState.current();
        Component footer = payload != null && payload.omitted() > 0
                ? label("catalog_truncated", entries.size(), entries.size() + payload.omitted())
                : label("catalog_page", page, pageCount);
        drawCentered(context, footer, FOOTER_Y + 6, TEXT);
    }

    private Component label(String key, Object... args) {
        return Component.translatable("gui.villagertradingplus." + key + (this.compact ? "_short" : ""), args);
    }

    private void drawTooltip(GuiGraphicsExtractor context, List<CatalogEntry> entries, int mouseX, int mouseY) {
        if (this.hoveredRow < 0 || this.hoveredRow >= entries.size()) {
            return;
        }

        CatalogEntry entry = entries.get(this.hoveredRow);
        int rowY = this.y + FIRST_ROW_Y + (this.hoveredRow - this.firstRow) * ROW_STEP;
        ItemStack hoveredStack = stackAt(entry, mouseX, mouseY, rowY);

        List<Component> lines = hoveredStack.isEmpty()
                ? new ArrayList<>()
                : Screen.getTooltipFromItem(this.client, hoveredStack);
        appendMetadata(lines, entry);

        context.setTooltipForNextFrame(
                this.client.font,
                lines,
                hoveredStack.isEmpty() ? Optional.empty() : hoveredStack.getTooltipImage(),
                mouseX,
                mouseY
        );
    }

    private ItemStack stackAt(CatalogEntry entry, int mouseX, int mouseY, int rowY) {
        if (mouseY < rowY || mouseY >= rowY + 16) {
            return ItemStack.EMPTY;
        }

        if (inSlot(mouseX, this.x + this.buyX)) {
            return entry.firstBuy();
        }

        if (inSlot(mouseX, this.x + this.buy2X)) {
            return entry.secondBuy();
        }

        if (inSlot(mouseX, this.x + this.sellX)) {
            return entry.sell();
        }

        return ItemStack.EMPTY;
    }

    private static boolean inSlot(int mouseX, int slotX) {
        return mouseX >= slotX && mouseX < slotX + 16;
    }

    private void appendMetadata(List<Component> lines, CatalogEntry entry) {
        if (!lines.isEmpty()) {
            lines.add(Component.empty());
        }

        if (entry.tierPoolSize() > 0) {
            lines.add(
                    Component.translatable(
                            "gui.villagertradingplus.tooltip.chance",
                            entry.tierPoolSize(),
                            entry.tierPicks(),
                            percent(tierChance(entry))
                    )
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        if (entry.isPooled()) {
            lines.add(
                    Component.translatable(
                            "gui.villagertradingplus.tooltip.pool_share",
                            entry.poolWeight(),
                            entry.poolTotalWeight(),
                            percent((float) entry.poolWeight() / entry.poolTotalWeight())
                    )
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        if (entry.poolShare() < 1.0f) {
            lines.add(
                    Component.translatable(
                            "gui.villagertradingplus.tooltip.combined_share",
                            percent(overallChance(entry))
                    )
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        if (!entry.conditions().isEmpty()) {
            lines.add(Component.empty());
            lines.add(
                    Component.translatable("gui.villagertradingplus.tooltip.conditions")
                            .withStyle(entry.conditionsMet() ? ChatFormatting.GREEN : ChatFormatting.GOLD)
            );
            for (ConditionInfo condition : entry.conditions()) {
                lines.add(
                        Component.literal(condition.satisfied() ? " ✔ " : " ✘ ")
                                .append(condition.description())
                                .withStyle(condition.satisfied() ? ChatFormatting.GREEN : ChatFormatting.RED)
                );
            }
        }

        lines.add(Component.empty());
        lines.add(Component.translatable("gui.villagertradingplus.tooltip.economics").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(
                Component.translatable("gui.villagertradingplus.tooltip.max_uses", entry.maxUses())
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
        lines.add(
                Component.translatable("gui.villagertradingplus.tooltip.experience", entry.villagerExperience())
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
        lines.add(
                Component.translatable(
                        "gui.villagertradingplus.tooltip.price_multiplier",
                        String.format(Locale.ROOT, "%.2f", entry.priceMultiplier())
                )
                        .withStyle(ChatFormatting.DARK_GRAY)
        );

        if (entry.demand() != 0) {
            lines.add(
                    Component.translatable("gui.villagertradingplus.tooltip.demand", entry.demand())
                            .withStyle(ChatFormatting.DARK_GRAY)
            );
        }

        if (entry.hasPriceRange()) {
            lines.add(
                    Component.translatable(
                            "gui.villagertradingplus.tooltip.price_range",
                            entry.minPrice(),
                            entry.maxPrice()
                    )
                            .withStyle(ChatFormatting.DARK_GRAY)
            );
        }
    }

    /// Every pool entry has the same odds. Weights apply only inside a `weighted_pool`.
    private static float tierChance(CatalogEntry entry) {
        if (entry.tierPoolSize() <= 0) {
            return 1.0f;
        }

        return Math.min(1.0f, (float) entry.tierPicks() / entry.tierPoolSize());
    }

    private static float overallChance(CatalogEntry entry) {
        return tierChance(entry) * entry.poolShare();
    }

    private static String percent(float value) {
        float pct = value * 100.0f;
        return pct < 10.0f && pct > 0.0f
                ? String.format(Locale.ROOT, "%.1f%%", pct)
                : String.format(Locale.ROOT, "%.0f%%", pct);
    }

    private List<CatalogEntry> entries() {
        TradeCatalogPacket.Payload payload = TradeCatalogClientState.current();
        return payload == null ? List.of() : payload.entries();
    }

    private int maxLevel() {
        TradeCatalogPacket.Payload payload = TradeCatalogClientState.current();
        return payload == null ? 1 : payload.maxLevel();
    }

    private void drawNavButton(
            GuiGraphicsExtractor context,
            int localX,
            int localY,
            String label,
            int mouseX,
            int mouseY
    ) {
        int left = this.x + localX;
        int top = this.y + localY;
        boolean hovered = mouseX >= left && mouseX < left + NAV_SIZE && mouseY >= top && mouseY < top + NAV_SIZE;

        context.fill(left, top, left + NAV_SIZE, top + NAV_SIZE, SLOT_BORDER);
        context.fill(left + 1, top + 1, left + NAV_SIZE - 1, top + NAV_SIZE - 1, hovered ? BUTTON_HOVER : BUTTON_FILL);

        Font font = this.client.font;
        context.text(font, label, left + (NAV_SIZE - font.width(label)) / 2, top + 6, BUTTON_LABEL, true);
    }

    private void drawSlot(GuiGraphicsExtractor context, int slotX, int slotY) {
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, SLOT_BORDER);
        context.fill(slotX, slotY, slotX + 16, slotY + 16, SLOT_FILL);
    }

    private void drawStack(GuiGraphicsExtractor context, ItemStack stack, int slotX, int slotY) {
        if (stack.isEmpty()) {
            return;
        }

        context.fakeItem(stack, slotX, slotY);
        context.itemDecorations(this.client.font, stack, slotX, slotY);
    }

    private void drawCentered(GuiGraphicsExtractor context, Component text, int localY, int color) {
        Font font = this.client.font;
        context.text(font, text, this.x + (this.width - font.width(text)) / 2, this.y + localY, color, false);
    }

    private static boolean inRect(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }
}
