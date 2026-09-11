package com.gradientcraft.client.gui;

import com.gradientcraft.client.BlockColorIndex;
import com.gradientcraft.client.GradientEngine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully client-side gradient generator. Opened from a button on the survival
 * inventory screen. The player drops blocks into a row of "waypoint" ghost
 * slots (by dragging from their inventory, or from JEI), tunes settings, and
 * the mod fills in the in-between blocks from the filtered palette.
 *
 * Nothing here touches the server: it only reads the client inventory and
 * computes a suggested block list, which is what makes it server-safe.
 */
public class GradientScreen extends Screen implements GhostSlots {

    private static final int MAX_WAYPOINTS = 8;
    private static final int SLOT = 18;
    private static final int PANEL_W = 256;
    private static final int PANEL_H = 232;

    // Persist setup across reopen within a session.
    private static final List<Block> WAYPOINTS = new ArrayList<>();
    private static int length = 12;
    private static boolean allowDup = true;

    // Filter toggles (default off, so behavior matches the unfiltered version).
    private static boolean opaqueOnly = false;
    private static boolean fullOnly = false;
    private static boolean noBlockEntities = false;

    private static List<Block> result = new ArrayList<>();

    private int left, top;
    private final List<Rect2i> waypointAreas = new ArrayList<>();
    private final List<Rect2i> invAreas = new ArrayList<>();

    private ItemStack dragging = ItemStack.EMPTY;

    public GradientScreen() {
        super(new TextComponent("Gradient Generator"));
    }

    @Override
    protected void init() {
        BlockColorIndex.ensureStarted();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        // Waypoint slot hit-areas.
        waypointAreas.clear();
        int wy = top + 38;
        for (int i = 0; i < MAX_WAYPOINTS; i++) {
            waypointAreas.add(new Rect2i(left + 12 + i * (SLOT + 2), wy, SLOT, SLOT));
        }

        // --- Settings row: filter toggles ---
        int sy = top + 70;
        addRenderableWidget(new Button(left + 12, sy, 78, 18, new TextComponent(opaqueLabel()),
                b -> { opaqueOnly = !opaqueOnly; b.setMessage(new TextComponent(opaqueLabel())); }));
        addRenderableWidget(new Button(left + 92, sy, 78, 18, new TextComponent(fullLabel()),
                b -> { fullOnly = !fullOnly; b.setMessage(new TextComponent(fullLabel())); }));
        addRenderableWidget(new Button(left + 172, sy, 72, 18, new TextComponent(teLabel()),
                b -> { noBlockEntities = !noBlockEntities; b.setMessage(new TextComponent(teLabel())); }));

        // --- Length + duplicates row ---
        int cy = top + 94;
        addRenderableWidget(new Button(left + 12, cy, 20, 20, new TextComponent("-"),
                b -> { length = Math.max(2, length - 1); }));
        addRenderableWidget(new Button(left + 86, cy, 20, 20, new TextComponent("+"),
                b -> { length = Math.min(256, length + 1); }));
        addRenderableWidget(new Button(left + 112, cy, 90, 20, new TextComponent(dupLabel()),
                b -> { allowDup = !allowDup; b.setMessage(new TextComponent(dupLabel())); }));

        // --- Action row ---
        int by = top + 118;
        addRenderableWidget(new Button(left + 12, by, 80, 20, new TextComponent("Generate"),
                b -> doGenerate()));
        addRenderableWidget(new Button(left + 96, by, 60, 20, new TextComponent("Clear"),
                b -> { WAYPOINTS.clear(); result.clear(); }));
        addRenderableWidget(new Button(left + 160, by, 84, 20, new TextComponent("Copy IDs"),
                b -> copyIds()));

        // Tabs (above the panel) to switch between the two tools.
        Button tabGrad = new Button(left + 4, top - 22, 70, 20, new TextComponent("Gradient"), b -> {});
        tabGrad.active = false;
        addRenderableWidget(tabGrad);
        addRenderableWidget(new Button(left + 78, top - 22, 70, 20, new TextComponent("Generate"),
                b -> Minecraft.getInstance().setScreen(new GenerateScreen())));
        addRenderableWidget(new Button(left + 152, top - 22, 70, 20, new TextComponent("Colors"),
                b -> Minecraft.getInstance().setScreen(new ColorScreen())));
        addRenderableWidget(new Button(left + 226, top - 22, 70, 20, new TextComponent("Palette"),
                b -> Minecraft.getInstance().setScreen(new PaletteScreen())));
        addRenderableWidget(new Button(left + 300, top - 22, 58, 20, new TextComponent("Lab"),
                b -> Minecraft.getInstance().setScreen(new LabScreen())));
    }

    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }
    private static String opaqueLabel() { return "Opaque: " + onOff(opaqueOnly); }
    private static String fullLabel()   { return "Full: " + onOff(fullOnly); }
    private static String teLabel()     { return "No-TE: " + onOff(noBlockEntities); }
    private static String dupLabel()    { return "Dupes: " + onOff(allowDup); }

    private GradientEngine.Filter currentFilter() {
        return new GradientEngine.Filter(opaqueOnly, fullOnly, noBlockEntities);
    }

    private void doGenerate() {
        if (!BlockColorIndex.isReady()) return;
        result = GradientEngine.generate(new ArrayList<>(WAYPOINTS), length, allowDup, currentFilter());
    }

    private void copyIds() {
        if (result.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.size(); i++) {
            ResourceLocation id = Registry.BLOCK.getKey(result.get(i));
            if (i > 0) sb.append(", ");
            sb.append(id);
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    }

    // ----- JEI hooks (also used by the inventory drag handler) -----

    public List<Rect2i> getWaypointAreas() { return waypointAreas; }

    // Geometry exposed so the JEI plugin can supply IGuiProperties. We report
    // only the main panel here (not the inventory panel, which sits to the
    // LEFT) so JEI draws its ingredient list to the right with room to spare.
    public int guiLeft()   { return left; }
    public int guiTop()    { return top; }
    public int guiWidth()  { return PANEL_W; }
    public int guiHeight() { return PANEL_H; }

    /** Assign a block (from a stack) to a waypoint slot index. Public for JEI compat. */
    public void setWaypointFromStack(int slot, ItemStack stack) {
        Block b = blockFromStack(stack);
        if (b == null) return;
        if (slot < WAYPOINTS.size()) {
            WAYPOINTS.set(slot, b);
        } else if (WAYPOINTS.size() < MAX_WAYPOINTS) {
            WAYPOINTS.add(b);
        }
    }

    private static Block blockFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof BlockItem bi) return bi.getBlock();
        return null;
    }

    /** How many palette blocks survive the current filter (for the GUI status line). */
    private int filteredPaletteSize() {
        GradientEngine.Filter f = currentFilter();
        int n = 0;
        for (BlockColorIndex.Entry e : BlockColorIndex.palette()) {
            if (f.accepts(e)) n++;
        }
        return n;
    }

    // ----- rendering -----

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        this.renderBackground(pose);

        // panel + title bar
        fill(pose, left, top, left + PANEL_W, top + PANEL_H, 0xF0101018);
        fill(pose, left, top, left + PANEL_W, top + 22, 0xFF2B2B3A);
        this.font.draw(pose, this.title, left + 8, top + 7, 0xFFFFFF);

        // waypoints
        this.font.draw(pose, new TextComponent("Waypoints  (drag from inventory or JEI)"),
                left + 12, top + 26, 0xC8C8D8);
        for (int i = 0; i < waypointAreas.size(); i++) {
            Rect2i r = waypointAreas.get(i);
            fill(pose, r.getX() - 1, r.getY() - 1, r.getX() + SLOT + 1, r.getY() + SLOT + 1, 0xFF000000);
            fill(pose, r.getX(), r.getY(), r.getX() + SLOT, r.getY() + SLOT, 0xFF3A3A4A);
            if (i < WAYPOINTS.size()) {
                this.itemRenderer.renderAndDecorateItem(new ItemStack(WAYPOINTS.get(i)),
                        r.getX() + 1, r.getY() + 1);
            }
        }

        // settings label
        this.font.draw(pose, new TextComponent("Filters:"), left + 12, top + 60, 0xC8C8D8);

        // length label (between the - and + buttons)
        this.font.draw(pose, new TextComponent("Len " + length), left + 36, top + 100, 0xFFFFFF);

        // results
        this.font.draw(pose, new TextComponent("Result:"), left + 12, top + 144, 0xC8C8D8);
        Block hovered = null;
        int rx = left + 12, ry = top + 156;
        int perRow = (PANEL_W - 24) / SLOT;
        for (int i = 0; i < result.size(); i++) {
            int col = i % perRow;
            int row = i / perRow;
            int x = rx + col * SLOT;
            int y = ry + row * SLOT;
            if (y > top + PANEL_H - 18) break; // clip overflow
            this.itemRenderer.renderAndDecorateItem(new ItemStack(result.get(i)), x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                hovered = result.get(i);
            }
        }

        // status line
        this.font.draw(pose, statusText(), left + 8, top + PANEL_H - 12, 0xA0A0B0);

        // inventory drag source
        drawInventory(pose);

        super.render(pose, mouseX, mouseY, partial); // buttons

        if (!dragging.isEmpty()) {
            this.itemRenderer.renderAndDecorateItem(dragging, mouseX - 8, mouseY - 8);
        }
        if (hovered != null) {
            renderTooltip(pose, new ItemStack(hovered), mouseX, mouseY);
        }
    }

    private void drawInventory(PoseStack pose) {
        invAreas.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        NonNullList<ItemStack> items = mc.player.getInventory().items; // 36 slots
        int ix = left - (9 * SLOT) - 8;   // to the LEFT of the panel (JEI list goes right)
        int iy = top + 8;
        this.font.draw(pose, new TextComponent("Inventory"), ix, iy - 10, 0xC8C8D8);
        for (int i = 0; i < items.size(); i++) {
            int col = i % 9;
            int row = i / 9;
            int x = ix + col * SLOT;
            int y = iy + row * SLOT;
            invAreas.add(new Rect2i(x, y, SLOT, SLOT));
            fill(pose, x, y, x + SLOT, y + SLOT, 0xFF2A2A38);
            ItemStack s = items.get(i);
            if (!s.isEmpty()) this.itemRenderer.renderAndDecorateItem(s, x + 1, y + 1);
        }
    }

    private String statusText() {
        switch (BlockColorIndex.state()) {
            case NOT_STARTED: return "Color index: starting...";
            case INDEXING:    return "Indexing blocks " + BlockColorIndex.progress()
                                   + " / " + BlockColorIndex.total();
            case READY:       return "Palette: " + filteredPaletteSize()
                                   + " / " + BlockColorIndex.palette().size() + " blocks pass filters";
            case FAILED:      return "Color index failed (see log)";
            default:          return "";
        }
    }

    // ----- input -----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) { // right-click clears a waypoint
            for (int i = 0; i < waypointAreas.size(); i++) {
                if (contains(waypointAreas.get(i), mx, my) && i < WAYPOINTS.size()) {
                    WAYPOINTS.remove(i);
                    return true;
                }
            }
        }
        if (button == 0) { // left-press on an inventory item begins a drag
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                NonNullList<ItemStack> items = mc.player.getInventory().items;
                for (int i = 0; i < invAreas.size(); i++) {
                    if (contains(invAreas.get(i), mx, my)) {
                        ItemStack s = items.get(i);
                        if (!s.isEmpty() && s.getItem() instanceof BlockItem) {
                            dragging = s.copy();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging.isEmpty()) {
            for (int i = 0; i < waypointAreas.size(); i++) {
                if (contains(waypointAreas.get(i), mx, my)) {
                    int slot = Math.min(i, WAYPOINTS.size());
                    setWaypointFromStack(slot, dragging);
                    break;
                }
            }
            dragging = ItemStack.EMPTY;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private static boolean contains(Rect2i r, double x, double y) {
        return x >= r.getX() && x < r.getX() + r.getWidth()
            && y >= r.getY() && y < r.getY() + r.getHeight();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
