package com.lion.villagertradingplus.client.screen;

import com.lion.villagertradingplus.client.TradeCatalogClientState;
import com.lion.villagertradingplus.tradeoffers.catalog.CatalogEntry;
import com.lion.villagertradingplus.tradeoffers.catalog.ConditionInfo;
import com.lion.villagertradingplus.tradeoffers.catalog.TradeCatalogPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Read-only trade catalogue panel, docked beside the vanilla trade screen.
/// The panel is hand-drawn, because it is painted from a TAIL hook after the screen widgets.
/// Its width adapts to the free gutter, which depends on the GUI scale.
/// Paging and scrolling stay on the client, because the server sends a whole tier in one packet.
public final class TradeCatalogPanel {

    private static final int MAX_WIDTH = 176;
    /// Below this width the rows clip, so the panel overlaps the trade screen instead.
    private static final int MIN_WIDTH = 73;
    private static final int COMPACT_WIDTH = 120;
    /// Equals the vanilla trade screen height, so both panels align at the top.
    private static final int HEIGHT = 166;

    private static final int DOCK_GAP = 2;
    private static final int SCREEN_MARGIN = 1;

    /// A slot border spans 18 units, so row spacing counts footprints, not icon widths.
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

    /// Vanilla draws trade items at z 250 and 300, so an overlapping panel is raised above them.
    private static final int OVERLAP_Z = 320;

    private static final int PANEL_BACKGROUND = 0xFFC6C6C6;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int ROW_HOVER = 0x40FFFFFF;
    private static final int ROW_GATED = 0x30FF7F27;
    private static final int BUTTON_FILL = 0xFF8B8B8B;
    private static final int BUTTON_HOVER = 0xFFA8A8A8;
    private static final int TEXT = 0x404040;
    private static final int TEXT_DIM = 0x707070;
    private static final int TEXT_GATED = 0xB06000;

    /// Matches the button ids that the merchant screen sends (130 + level).
    private static final int CATALOG_REQUEST_BASE = 130;

    private final MinecraftClient client;

    private boolean visible;
    private int level = 1;
    private int firstRow;

    private int x;
    private int y;

    // Recomputed by layout() for the space actually available.
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

    /// Hovered row from the last render, or -1. The tooltip pass reuses it.
    private int hoveredRow = -1;

    public TradeCatalogPanel(MinecraftClient client) {
        this.client = client;
        applyWidth(MAX_WIDTH);
    }

    public boolean isVisible() {
        return this.visible;
    }

    /// Toggles the panel and requests the tier when it opens. The server clamps the tier.
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

    /// Returns the button id to send, or -1 when no request is needed.
    private int requestLevel(int newLevel) {
        this.level = newLevel;
        this.firstRow = 0;
        TradeCatalogClientState.requesting();
        return CATALOG_REQUEST_BASE + newLevel;
    }

    /// Sizes the panel to the left gutter. It overlaps the trade screen only below MIN_WIDTH.
    public void layout(int merchantX, int merchantY, int screenWidth, int screenHeight) {
        applyWidth(MathHelper.clamp(merchantX - DOCK_GAP - SCREEN_MARGIN, MIN_WIDTH, MAX_WIDTH));

        this.x = Math.max(SCREEN_MARGIN, merchantX - DOCK_GAP - this.width);
        this.y = MathHelper.clamp(merchantY, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenHeight - HEIGHT - SCREEN_MARGIN));
        this.overlapping = this.x + this.width > merchantX;

        if (this.x + this.width > screenWidth) {
            this.x = Math.max(SCREEN_MARGIN, screenWidth - this.width - SCREEN_MARGIN);
        }
    }

    /// Centres the slot block and drops decorations before the slots clip.
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

    /// Returns the button id to send to the server, or -1 when the click is local or misses.
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

    /// Scrolls by whole rows and stops at the start of the last page, so the last page stays reachable.
    private void scrollRows(int delta) {
        int size = entries().size();
        int lastPageStart = Math.max(0, (MathHelper.ceilDiv(size, ROWS_PER_PAGE) - 1) * ROWS_PER_PAGE);
        this.firstRow = MathHelper.clamp(this.firstRow + delta, 0, lastPageStart);
    }

    private static int wrapLevel(int level, int maxLevel) {
        return Math.floorMod(level - 1, maxLevel) + 1;
    }

    private void playClick() {
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        // Raised above vanilla trade items (z 250) and arrow (z 300), so an overlapping panel is not pierced.
        context.getMatrices().push();
        if (this.overlapping) {
            context.getMatrices().translate(0.0f, 0.0f, OVERLAP_Z);
        }

        drawPanel(context, mouseX, mouseY);

        context.getMatrices().pop();
    }

    private void drawPanel(DrawContext context, int mouseX, int mouseY) {
        TextRenderer font = this.client.textRenderer;
        drawFrame(context);

        // Adopt the tier the server served, because it may clamp the requested tier.
        TradeCatalogPacket.Payload payload = TradeCatalogClientState.current();
        if (payload != null && !TradeCatalogClientState.isLoading()) {
            this.level = payload.level();
        }

        Text title = label("catalog_title", this.level);
        context.drawText(font, title, this.x + (this.width - font.getWidth(title)) / 2, this.y + 6, TEXT, false);

        if (maxLevel() > 1) {
            drawNavButton(context, NAV_LEFT_X, NAV_Y, "<", mouseX, mouseY);
            drawNavButton(context, this.navRightX, NAV_Y, ">", mouseX, mouseY);
        }

        if (TradeCatalogClientState.isLoading()) {
            drawCentered(context, Text.translatable("gui.villagertradingplus.catalog_loading"), FIRST_ROW_Y + 24, TEXT_DIM);
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

        // Last, so it lands above the vanilla trade screen's own tooltip pass.
        drawTooltip(context, entries, mouseX, mouseY);
    }

    private void drawFrame(DrawContext context) {
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

    private void drawRows(DrawContext context, List<CatalogEntry> entries, int mouseX, int mouseY) {
        TextRenderer font = this.client.textRenderer;
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
            context.drawText(font, Text.literal("->"), this.x + this.arrowX, rowY + 4, TEXT, false);
            drawSlot(context, this.x + this.sellX, rowY);
            drawStack(context, entry.sell(), this.x + this.sellX, rowY);

            if (this.showMarker && !entry.conditions().isEmpty()) {
                context.drawText(font, Text.literal("!"), this.x + this.markerX, rowY + 5,
                        entry.conditionsMet() ? TEXT_DIM : TEXT_GATED, false);
            }

            if (this.showChance) {
                String chance = percent(overallChance(entry));
                context.drawText(font, Text.literal(chance),
                        this.x + this.width - 8 - font.getWidth(chance), rowY + 5, TEXT_DIM, false);
            }
        }
    }

    private void drawFooter(DrawContext context, List<CatalogEntry> entries, int mouseX, int mouseY) {
        int pageCount = Math.max(1, MathHelper.ceilDiv(entries.size(), ROWS_PER_PAGE));
        int page = this.firstRow / ROWS_PER_PAGE + 1;

        if (entries.size() > ROWS_PER_PAGE) {
            drawNavButton(context, NAV_LEFT_X, FOOTER_Y, "<", mouseX, mouseY);
            drawNavButton(context, this.navRightX, FOOTER_Y, ">", mouseX, mouseY);
        }

        TradeCatalogPacket.Payload payload = TradeCatalogClientState.current();
        Text footer = payload != null && payload.omitted() > 0
                ? label("catalog_truncated", entries.size(), entries.size() + payload.omitted())
                : label("catalog_page", page, pageCount);
        drawCentered(context, footer, FOOTER_Y + 6, TEXT);
    }

    /// Picks the short label when the panel is too narrow for the full one.
    private Text label(String key, Object... args) {
        return Text.translatable("gui.villagertradingplus." + key + (this.compact ? "_short" : ""), args);
    }

    private void drawTooltip(DrawContext context, List<CatalogEntry> entries, int mouseX, int mouseY) {
        if (this.hoveredRow < 0 || this.hoveredRow >= entries.size()) {
            return;
        }

        CatalogEntry entry = entries.get(this.hoveredRow);
        int rowY = this.y + FIRST_ROW_Y + (this.hoveredRow - this.firstRow) * ROW_STEP;
        ItemStack hoveredStack = stackAt(entry, mouseX, mouseY, rowY);

        List<Text> lines = hoveredStack.isEmpty()
                ? new ArrayList<>()
                : Screen.getTooltipFromItem(this.client, hoveredStack);
        appendMetadata(lines, entry);

        context.drawTooltip(this.client.textRenderer, lines,
                hoveredStack.isEmpty() ? Optional.empty() : hoveredStack.getTooltipData(),
                mouseX, mouseY);
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

    /// Appends probability, gating and pricing metadata that a TradeOffer cannot carry.
    private void appendMetadata(List<Text> lines, CatalogEntry entry) {
        if (!lines.isEmpty()) {
            lines.add(Text.empty());
        }

        if (entry.tierPoolSize() > 0) {
            lines.add(Text.translatable("gui.villagertradingplus.tooltip.chance",
                            entry.tierPoolSize(), entry.tierPicks(), percent(tierChance(entry)))
                    .formatted(Formatting.GRAY));
        }
        if (entry.isPooled()) {
            lines.add(Text.translatable("gui.villagertradingplus.tooltip.pool_share",
                            entry.poolWeight(), entry.poolTotalWeight(),
                            percent((float) entry.poolWeight() / entry.poolTotalWeight()))
                    .formatted(Formatting.GRAY));
        }
        if (entry.poolShare() < 1.0f) {
            lines.add(Text.translatable("gui.villagertradingplus.tooltip.combined_share",
                            percent(overallChance(entry)))
                    .formatted(Formatting.GRAY));
        }

        if (!entry.conditions().isEmpty()) {
            lines.add(Text.empty());
            lines.add(Text.translatable("gui.villagertradingplus.tooltip.conditions")
                    .formatted(entry.conditionsMet() ? Formatting.GREEN : Formatting.GOLD));
            for (ConditionInfo condition : entry.conditions()) {
                lines.add(Text.literal(condition.satisfied() ? " ✔ " : " ✘ ")
                        .append(condition.description())
                        .formatted(condition.satisfied() ? Formatting.GREEN : Formatting.RED));
            }
        }

        lines.add(Text.empty());
        lines.add(Text.translatable("gui.villagertradingplus.tooltip.economics").formatted(Formatting.DARK_GRAY));
        lines.add(Text.translatable("gui.villagertradingplus.tooltip.max_uses", entry.maxUses())
                .formatted(Formatting.DARK_GRAY));
        lines.add(Text.translatable("gui.villagertradingplus.tooltip.experience", entry.villagerExperience())
                .formatted(Formatting.DARK_GRAY));
        lines.add(Text.translatable("gui.villagertradingplus.tooltip.price_multiplier",
                        String.format(Locale.ROOT, "%.2f", entry.priceMultiplier()))
                .formatted(Formatting.DARK_GRAY));
        if (entry.demand() != 0) {
            lines.add(Text.translatable("gui.villagertradingplus.tooltip.demand", entry.demand())
                    .formatted(Formatting.DARK_GRAY));
        }
        if (entry.hasPriceRange()) {
            lines.add(Text.translatable("gui.villagertradingplus.tooltip.price_range",
                            entry.minPrice(), entry.maxPrice())
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    /// Vanilla picks tier entries uniformly. JSON weights apply only inside a weighted_pool.
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

    private void drawNavButton(DrawContext context, int localX, int localY, String label, int mouseX, int mouseY) {
        int left = this.x + localX;
        int top = this.y + localY;
        boolean hovered = mouseX >= left && mouseX < left + NAV_SIZE && mouseY >= top && mouseY < top + NAV_SIZE;

        context.fill(left, top, left + NAV_SIZE, top + NAV_SIZE, SLOT_BORDER);
        context.fill(left + 1, top + 1, left + NAV_SIZE - 1, top + NAV_SIZE - 1,
                hovered ? BUTTON_HOVER : BUTTON_FILL);

        TextRenderer font = this.client.textRenderer;
        context.drawText(font, label, left + (NAV_SIZE - font.getWidth(label)) / 2, top + 6, 0xFFFFFF, true);
    }

    private void drawSlot(DrawContext context, int slotX, int slotY) {
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, SLOT_BORDER);
        context.fill(slotX, slotY, slotX + 16, slotY + 16, SLOT_FILL);
    }

    private void drawStack(DrawContext context, ItemStack stack, int slotX, int slotY) {
        if (stack.isEmpty()) {
            return;
        }
        context.drawItemWithoutEntity(stack, slotX, slotY);
        context.drawItemInSlot(this.client.textRenderer, stack, slotX, slotY);
    }

    private void drawCentered(DrawContext context, Text text, int localY, int color) {
        TextRenderer font = this.client.textRenderer;
        context.drawText(font, text, this.x + (this.width - font.getWidth(text)) / 2, this.y + localY, color, false);
    }

    private static boolean inRect(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }
}
