package com.builderstoolkit.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.builderstoolkit.client.PaletteFilter;
import com.builderstoolkit.client.gui.widget.FlatButton;
import com.builderstoolkit.client.gui.widget.ToggleButton;
import com.builderstoolkit.config.ModConfig;

/**
 * Shared chrome for the toolkit tabs: the panel, the tab strip, scrolling, item
 * drawing and tooltips. Subclasses supply their own widgets and body.
 *
 * Extends GuiContainer over an empty container purely so NEI draws its item
 * panel alongside these screens; NEI only overlays GuiContainer subclasses.
 * That also fixes the draw order for free, since GuiContainer paints the
 * background layer, then buttons, then leaves the overlay to us.
 */
public abstract class ToolkitScreen extends GuiContainer {

    /** The tabs, in display order. */
    public enum Tab {

        GRADIENT("Gradient"),
        GENERATE("Generate"),
        COLORS("Colors"),
        PALETTE("Palette"),
        LAB("Lab");

        final String label;

        Tab(String label) {
            this.label = label;
        }

        GuiScreen open() {
            switch (this) {
                case GENERATE:
                    return new GenerateScreen();
                case COLORS:
                    return new ColorScreen();
                case PALETTE:
                    return new PaletteScreen();
                case LAB:
                    return new LabScreen();
                default:
                    return new GradientScreen();
            }
        }

        /** Generate and Lab only produce WorldEdit commands, so the config can hide them. */
        boolean enabled() {
            switch (this) {
                case GENERATE:
                    return ModConfig.generateTabEnabled();
                case LAB:
                    return ModConfig.labTabEnabled();
                default:
                    return true;
            }
        }

        static List<Tab> visible() {
            List<Tab> out = new ArrayList<Tab>();
            for (Tab t : values()) {
                if (t.enabled()) out.add(t);
            }
            return out;
        }
    }

    private static final int TAB_GAP = 2;
    private static final int TAB_H = 18;
    private static final int SCROLL_STEP = 18;
    /** Clearance above the tab strip, so it sits below NEI's Item Subsets bar. */
    private static final int TOP_INSET = 36;
    private static final int BAR_W = 4;

    protected final String title;
    private final Tab tab;
    protected final int panelW;
    protected final int panelH;

    protected int left;
    protected int top;

    /** Starts pinned to the top of the panel; clamped on the first layout. */
    private int scroll = Integer.MAX_VALUE;
    private boolean draggingBar;

    private final RenderItem itemRender = new RenderItem();

    protected ToolkitScreen(String title, Tab tab, int panelW, int panelH) {
        super(new ToolkitContainer());
        this.title = title;
        this.tab = tab;
        this.panelW = panelW;
        this.panelH = panelH;
        this.xSize = panelW;
        this.ySize = panelH;
    }

    /** Add the widgets belonging to this screen. Called after the tab strip is built. */
    protected abstract void build();

    /** Paint the panel and its contents. Runs before the widgets are drawn. */
    protected abstract void drawContent(int mouseX, int mouseY, float partialTicks);

    /** Paint anything that must sit above the widgets, such as tooltips. */
    protected void drawOverlay(int mouseX, int mouseY) {}

    @Override
    public void initGui() {
        super.initGui();
        this.left = (this.width - panelW) / 2;
        this.scroll = clampScroll(this.scroll);
        this.top = (this.height - panelH) / 2 + this.scroll;
        // Tell NEI where we actually are, so its panel keeps clear of the screen.
        this.guiLeft = this.left;
        this.guiTop = this.top;
        this.buttonList.clear();
        addTabs();
        addMoveButton();
        build();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawContent(mouseX, mouseY, partialTicks);
        drawScrollbar();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // GuiContainer paints the background layer, then the widgets, and runs
        // whatever hooks NEI has injected along the way.
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawOverlay(mouseX, mouseY);
    }

    /**
     * The container exists only so NEI recognises these screens. Neutralise the
     * two places GuiContainer would otherwise act on it: slot clicks send a
     * window-click packet against window 0 (the real player inventory), and the
     * default close handler drops whatever is on the cursor.
     */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int button, int modifier) {
        // no slots, no packets
    }

    @Override
    public void onGuiClosed() {
        // deliberately not calling super
    }

    // ---- scrolling ----

    private boolean scrollable() {
        return scrollMin() < scrollMax();
    }

    private int scrollMax() {
        return (TAB_H + TOP_INSET) - (this.height - panelH) / 2;
    }

    private int scrollMin() {
        int base = (this.height - panelH) / 2;
        int lowestTop = Math.min(TAB_H + TOP_INSET, this.height - panelH - 8);
        return Math.min(lowestTop - base, scrollMax());
    }

    private int clampScroll(int value) {
        int min = scrollMin();
        int max = scrollMax();
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Panels taller than the window scroll with the wheel. Widgets are placed
     * relative to {@code top}, so rebuilding after a scroll moves everything
     * together and needs no clipping.
     */
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || !scrollable()) return;
        int before = this.scroll;
        this.scroll = clampScroll(this.scroll + (wheel > 0 ? SCROLL_STEP : -SCROLL_STEP));
        if (this.scroll != before) rebuild();
    }

    /** Track and thumb down the right edge, sized to the visible fraction. */
    private void drawScrollbar() {
        if (!scrollable()) return;

        int x = left + panelW + 2;
        int trackTop = TAB_H + TOP_INSET;
        int trackH = this.height - trackTop - 8;
        if (trackH <= 0) return;

        drawRect(x, trackTop, x + BAR_W, trackTop + trackH, Theme.SLOT_DARK);

        double visible = Math.min(1.0, (double) trackH / panelH);
        int thumbH = Math.max(12, (int) (trackH * visible));
        int range = scrollMax() - scrollMin();
        double progress = range == 0 ? 0 : (double) (scrollMax() - scroll) / range;
        int thumbY = trackTop + (int) ((trackH - thumbH) * progress);

        drawRect(x, thumbY, x + BAR_W, thumbY + thumbH, Theme.WIDGET_HOVER);
    }

    /** Maps a cursor position on the track to a scroll offset. */
    private void scrollToBar(int mouseY) {
        int trackTop = TAB_H + TOP_INSET;
        int trackH = this.height - trackTop - 8;
        if (trackH <= 0) return;
        double progress = (mouseY - trackTop) / (double) trackH;
        progress = progress < 0 ? 0 : (progress > 1 ? 1 : progress);
        int before = this.scroll;
        this.scroll = clampScroll(scrollMax() - (int) Math.round(progress * (scrollMax() - scrollMin())));
        if (this.scroll != before) rebuild();
    }

    private boolean overBar(int mouseX) {
        return mouseX >= left + panelW + 2 && mouseX <= left + panelW + 2 + BAR_W;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && scrollable() && overBar(mouseX)) {
            draggingBar = true;
            scrollToBar(mouseY);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceClick) {
        if (draggingBar) {
            scrollToBar(mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) draggingBar = false;
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    // ---- tabs ----

    /** Tabs divide the panel width exactly, so the strip always ends flush with the panel. */
    private void addTabs() {
        List<Tab> tabs = Tab.visible();
        if (tabs.isEmpty()) return;
        int gaps = TAB_GAP * (tabs.size() - 1);
        int tabW = (panelW - gaps) / tabs.size();
        int extra = panelW - gaps - tabW * tabs.size(); // spread the rounding remainder

        int x = left;
        for (int i = 0; i < tabs.size(); i++) {
            final Tab t = tabs.get(i);
            int w = tabW + (i < extra ? 1 : 0);
            FlatButton b = new FlatButton(x, top - TAB_H - 2, w, TAB_H, t.label, new Runnable() {

                @Override
                public void run() {
                    Minecraft.getMinecraft()
                        .displayGuiScreen(t.open());
                }
            });
            b.enabled = t != tab;
            this.buttonList.add(b);
            x += w + TAB_GAP;
        }
    }

    /** Opens the drag-to-place editor for the inventory button. */
    private void addMoveButton() {
        this.buttonList.add(new FlatButton(left + panelW - 54, top + 4, 50, 14, "Move G", new Runnable() {

            @Override
            public void run() {
                Minecraft.getMinecraft()
                    .displayGuiScreen(new ButtonPositionScreen(ToolkitScreen.this));
            }
        }));
    }

    /**
     * Lays the shared palette filters out in a grid. Every tab that picks blocks
     * shows the same four, so they cannot drift apart.
     */
    protected void addFilterToggles(int x, int y, int totalWidth, int columns, int rowHeight, int rowGap) {
        int gap = 2;
        int cellW = (totalWidth - gap * (columns - 1)) / columns;
        for (int i = 0; i < PaletteFilter.count(); i++) {
            final int index = i;
            int col = i % columns;
            int row = i / columns;
            this.buttonList.add(
                new ToggleButton(
                    x + col * (cellW + gap),
                    y + row * (rowHeight + rowGap),
                    cellW,
                    rowHeight,
                    PaletteFilter.label(index),
                    () -> PaletteFilter.get(index),
                    () -> PaletteFilter.toggle(index)));
        }
    }

    /** Rebuild every widget, e.g. after a control changes the layout. */
    protected void rebuild() {
        initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof FlatButton) ((FlatButton) button).onClick();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---- drawing helpers ----

    protected void drawPanel() {
        // GuiContainer has already dimmed the world behind us.
        drawRect(left, top, left + panelW, top + panelH, Theme.PANEL);
        drawRect(left, top, left + panelW, top + 22, Theme.TITLE_BAR);
        this.fontRendererObj.drawString(title, left + 8, top + 7, Theme.TEXT);
    }

    /** Exposes Gui.drawRect to helpers in this package that are not Gui subclasses. */
    protected void rect(int x1, int y1, int x2, int y2, int color) {
        drawRect(x1, y1, x2, y2, color);
    }

    protected void text(String s, int x, int y, int color) {
        this.fontRendererObj.drawString(s, x, y, color);
    }

    /** Draw {@code s} clipped to {@code maxWidth} pixels. */
    protected void textClipped(String s, int x, int y, int maxWidth, int color) {
        this.fontRendererObj.drawString(this.fontRendererObj.trimStringToWidth(s, maxWidth), x, y, color);
    }

    protected void drawSlot(int x, int y, int size, int border) {
        drawRect(x - 1, y - 1, x + size + 1, y + size + 1, border);
        drawRect(x, y, x + size, y + size, Theme.SLOT);
        if (Theme.STYLE == Theme.Style.BEVEL) {
            // Inverted bevel, so slots read as recessed next to raised buttons.
            drawRect(x, y, x + size, y + 1, Theme.WIDGET_EDGE);
            drawRect(x, y, x + 1, y + size, Theme.WIDGET_EDGE);
            drawRect(x, y + size - 1, x + size, y + size, Theme.BEVEL_LIGHT);
            drawRect(x + size - 1, y, x + size, y + size, Theme.BEVEL_LIGHT);
        }
    }

    /**
     * Draws a labelled control group. Themes that group by spacing alone
     * (Contrast) draw only the label; the rest draw a well behind it.
     */
    protected void drawSection(String label, int x, int y, int w, int h) {
        if ((Theme.SECTION_EDGE >>> 24) != 0) drawRect(x, y, x + w, y + h, Theme.SECTION_EDGE);
        if ((Theme.SECTION >>> 24) != 0) drawRect(x + 1, y + 1, x + w - 1, y + h - 1, Theme.SECTION);
        if (label != null) text(label, x + 4, y + 4, Theme.TEXT_DIM);
    }

    protected void drawStack(ItemStack stack, int x, int y) {
        if (stack == null) return;
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        itemRender.zLevel = 100.0F;
        try {
            itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, x, y);
        } catch (Throwable ignored) {
            // a handful of modded items throw while rendering outside a container
        }
        itemRender.zLevel = 0.0F;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    /**
     * Flat tooltip. Written out rather than calling the vanilla helper so it
     * matches the panel styling and stays clear of the screen edges.
     */
    protected void drawTooltip(List<String> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) return;

        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, this.fontRendererObj.getStringWidth(line));
        }
        int boxW = textW + 8;
        int boxH = lines.size() * 10 + 6;

        int x = Math.min(mouseX + 10, this.width - boxW - 2);
        int y = Math.max(2, Math.min(mouseY - 6, this.height - boxH - 2));

        // Items render at zLevel 100 with depth testing on, and Gui.drawRect is
        // static at z=0 - so without this the tooltip is occluded by the very
        // block icons it describes. Depth off makes paint order win, as vanilla
        // tooltips do.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);

        drawRect(x, y, x + boxW, y + boxH, withAlpha(Theme.BORDER, 0xE6));
        drawRect(x + 1, y + 1, x + boxW - 1, y + boxH - 1, withAlpha(Theme.PANEL, 0xD4));
        for (int i = 0; i < lines.size(); i++) {
            this.fontRendererObj
                .drawStringWithShadow(lines.get(i), x + 4, y + 4 + i * 10, i == 0 ? Theme.TEXT : Theme.TEXT_FAINT);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** Replaces the alpha channel of a packed ARGB colour. */
    protected static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0xFFFFFF);
    }

    protected static void copyToClipboard(String s) {
        if (s != null && s.length() > 0) setClipboardString(s);
    }

    protected void sendChat(String command) {
        if (this.mc.thePlayer != null) this.mc.thePlayer.sendChatMessage(command);
    }
}
