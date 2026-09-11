package com.builderstoolkit.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.builderstoolkit.client.BlockColorIndex;
import com.builderstoolkit.client.BlockColorIndex.Entry;
import com.builderstoolkit.client.GradientEngine;
import com.builderstoolkit.client.PaletteFilter;
import com.builderstoolkit.client.blend.BlendEngine;
import com.builderstoolkit.client.blend.WorldEditScript;
import com.builderstoolkit.client.gui.widget.CycleButton;
import com.builderstoolkit.client.gui.widget.FlatButton;
import com.builderstoolkit.client.gui.widget.SliderButton;
import com.builderstoolkit.client.gui.widget.ToggleButton;

/**
 * Gradient tab: drop blocks into the waypoint slots and the mod fills the
 * in-between steps from the indexed palette. Reads the client inventory only.
 */
public class GradientScreen extends ToolkitScreen implements GhostTarget {

    private static final int MAX_WAYPOINTS = 8;
    private static final int SLOT = 18;

    // Section geometry, shared by build() and drawScreen() so they cannot drift.
    private static final int SEC_X = 8;
    private static final int WAYPOINTS_Y = 26;
    private static final int FILTERS_Y = 68;
    private static final int STRIP_Y = 124;
    private static final int ACTIONS_Y = 166;
    private static final int RESULT_Y = 192;
    private static final int RESULT_H = 58;
    private static final int RESULT_GRID_Y = 208;
    private static final int BLEND_Y = 254;
    private static final int PREVIEW_Y = 372;
    private static final int PREVIEW_H = 72;
    /** Big enough that a block's texture is readable, small enough to show the whole wall. */
    private static final int PREVIEW_CELL = 6;
    private static final int PREVIEW_COLS = 38;
    private static final int PREVIEW_ROWS = PREVIEW_H / PREVIEW_CELL;
    /** Wall columns used for the WorldEdit percentages; the preview samples down from this. */
    private static final int BLEND_COLS = 58;

    // Kept across reopen within a session.
    private static final List<Entry> WAYPOINTS = new ArrayList<Entry>();
    private static List<Entry> result = new ArrayList<Entry>();
    private static int length = 12;
    private static boolean allowDup = true;

    // Blend settings, also kept across reopen.
    private static BlendEngine.Mode blendMode = BlendEngine.Mode.DITHER;
    private static double randomness = 0.30;
    private static double noiseScale = 8;
    private static int octaves = 3;
    private static int wallHeight = 24;
    private static long seed = 1234L;
    private static int[] blendGrid = new int[0];
    private static boolean blendDirty = true;

    private final List<Rect> waypointAreas = new ArrayList<Rect>();
    private final InventoryStrip inventory = new InventoryStrip();
    private ItemStack dragging;
    /** Set while painting, consumed by drawOverlay. */
    private Entry hoveredEntry;

    public GradientScreen() {
        super("Gradient Generator", Tab.GRADIENT, 256, 458);
    }

    private int secW() {
        return panelW - SEC_X * 2;
    }

    @Override
    protected void build() {
        BlockColorIndex.ensureStarted();

        waypointAreas.clear();
        for (int i = 0; i < MAX_WAYPOINTS; i++) {
            waypointAreas.add(new Rect(left + 12 + i * (SLOT + 2), top + 42, SLOT, SLOT));
        }

        addFilterToggles(left + 12, top + FILTERS_Y + 14, secW() - 8, 3, 16, 2);

        int sy = top + STRIP_Y + 14;
        buttonList.add(new FlatButton(left + 12, sy, 20, 20, "-", () -> length = Math.max(2, length - 1)));
        buttonList.add(new FlatButton(left + 34, sy, 20, 20, "+", () -> length = Math.min(256, length + 1)));
        buttonList.add(new ToggleButton(left + 144, sy, 100, 20, "Dupes", () -> allowDup, () -> allowDup = !allowDup));

        int ay = top + ACTIONS_Y;
        buttonList.add(new FlatButton(left + 12, ay, 60, 20, "Generate", this::generate).primary());
        buttonList.add(new FlatButton(left + 76, ay, 64, 20, "Reshuffle", this::reshuffle));
        buttonList.add(new FlatButton(left + 144, ay, 40, 20, "Clear", () -> {
            WAYPOINTS.clear();
            result.clear();
            blendDirty = true;
        }));
        buttonList.add(new FlatButton(left + 188, ay, 56, 20, "Copy IDs", this::copyIds));

        buildBlendControls();
    }

    /** The blend controls live below the fold; scroll the panel to reach them. */
    private void buildBlendControls() {
        buttonList.add(new CycleButton(left + 12, top + BLEND_Y + 16, 110, 18, "Mode", () -> blendMode.name(), () -> {
            BlendEngine.Mode[] modes = BlendEngine.Mode.values();
            blendMode = modes[(blendMode.ordinal() + 1) % modes.length];
            blendDirty = true;
        }));
        buttonList.add(new FlatButton(left + 134, top + BLEND_Y + 16, 110, 18, "Reroll seed", () -> {
            seed = System.nanoTime();
            blendDirty = true;
        }));

        buttonList.add(
            new SliderButton(
                left + 12,
                top + BLEND_Y + 38,
                232,
                14,
                "randomness",
                0,
                100,
                true,
                randomness * 100,
                v -> {
                    randomness = v / 100.0;
                    blendDirty = true;
                }));
        buttonList.add(new SliderButton(left + 12, top + BLEND_Y + 56, 232, 14, "scale", 2, 32, true, noiseScale, v -> {
            noiseScale = v;
            blendDirty = true;
        }));
        buttonList.add(new SliderButton(left + 12, top + BLEND_Y + 74, 112, 14, "octaves", 1, 5, true, octaves, v -> {
            octaves = (int) v;
            blendDirty = true;
        }));
        buttonList
            .add(new SliderButton(left + 132, top + BLEND_Y + 74, 112, 14, "wall H", 8, 64, true, wallHeight, v -> {
                wallHeight = (int) v;
                blendDirty = true;
            }));

        buttonList
            .add(new FlatButton(left + 12, top + BLEND_Y + 94, 232, 18, "Copy WorldEdit script", this::copyScript));
    }

    private void rebuildBlend() {
        blendDirty = false;
        blendGrid = result.isEmpty() ? new int[0]
            : BlendEngine
                .build(BLEND_COLS, wallHeight, result.size(), blendMode, randomness, noiseScale, octaves, seed);
    }

    private void copyScript() {
        if (result.isEmpty()) return;
        if (blendDirty) rebuildBlend();
        List<String> ids = new ArrayList<String>();
        for (Entry e : result) {
            ids.add(e.worldEditId());
        }
        List<String> lines = WorldEditScript.build(blendGrid, BLEND_COLS, wallHeight, ids, blendMode);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        copyToClipboard(sb.toString());
    }

    /** The gradient as numeric WorldEdit ids, in order. Empty if none is generated. */
    public static List<String> gradientBlockIds() {
        List<String> ids = new ArrayList<String>();
        for (Entry e : result) {
            ids.add(e.worldEditId());
        }
        return ids;
    }

    /**
     * The current gradient as a WorldEdit weighted pattern, mixed using the
     * Blend settings so the mode and randomness carry across. Null when no
     * gradient has been generated yet.
     */
    public static String gradientPattern() {
        if (result.isEmpty()) return null;
        List<String> ids = new ArrayList<String>();
        for (Entry e : result) {
            ids.add(e.worldEditId());
        }
        int[] grid = BlendEngine
            .build(BLEND_COLS, wallHeight, result.size(), blendMode, randomness, noiseScale, octaves, seed);
        return WorldEditScript.pattern(BlendEngine.weights(grid, result.size()), ids);
    }

    @Override
    public boolean acceptGhost(int mouseX, int mouseY, ItemStack stack) {
        for (int i = 0; i < waypointAreas.size(); i++) {
            if (!waypointAreas.get(i)
                .contains(mouseX, mouseY)) continue;
            setWaypoint(Math.min(i, WAYPOINTS.size()), stack);
            return true;
        }
        return false;
    }

    private void generate() {
        if (!BlockColorIndex.isReady()) return;
        result = GradientEngine.generate(new ArrayList<Entry>(WAYPOINTS), length, allowDup);
        blendDirty = true;
    }

    /**
     * The next-best gradient: the same fit, run again with every block of the
     * current strip penalised, so what comes back is mostly different blocks
     * rather than the same ones in a different order. Waypoints are pinned, so
     * the endpoints you chose survive a reshuffle.
     */
    private void reshuffle() {
        if (!BlockColorIndex.isReady()) return;
        if (result.isEmpty()) {
            generate();
            return;
        }
        Set<Entry> avoid = new HashSet<Entry>(result);
        List<Entry> next = GradientEngine.generate(new ArrayList<Entry>(WAYPOINTS), length, allowDup, avoid);
        if (next.isEmpty()) return;
        result = next;
        blendDirty = true;
    }

    private void copyIds() {
        if (result.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Entry e : result) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.id());
        }
        copyToClipboard(sb.toString());
    }

    private void setWaypoint(int slot, ItemStack stack) {
        Entry e = BlockColorIndex.find(stack);
        if (e == null) return;
        if (slot < WAYPOINTS.size()) {
            WAYPOINTS.set(slot, e);
        } else if (WAYPOINTS.size() < MAX_WAYPOINTS) {
            WAYPOINTS.add(e);
        }
    }

    // ---- rendering ----

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        drawPanel();

        drawSection("Waypoints  (drag from inventory)", left + SEC_X, top + WAYPOINTS_Y, secW(), 38);
        for (int i = 0; i < waypointAreas.size(); i++) {
            Rect r = waypointAreas.get(i);
            drawSlot(r.x, r.y, SLOT, Theme.BORDER);
            if (i < WAYPOINTS.size()) drawStack(WAYPOINTS.get(i).stack, r.x + 1, r.y + 1);
        }

        drawSection("Filters", left + SEC_X, top + FILTERS_Y, secW(), 52);
        drawSection("Strip", left + SEC_X, top + STRIP_Y, secW(), 36);
        text("Len " + length, left + 60, top + STRIP_Y + 20, Theme.TEXT);

        drawSection("Result", left + SEC_X, top + RESULT_Y, secW(), RESULT_H);
        hoveredEntry = drawResult(mouseX, mouseY);

        drawSection("Blend  (2D wall preview)", left + SEC_X, top + BLEND_Y, secW(), 190);
        drawBlendPreview();

        textClipped(statusText(), left + 8, top + panelH - 12, panelW - 16, Theme.TEXT_FAINT);

        text("Inventory", inventoryX(), top - 2, Theme.TEXT_DIM);
        inventory.draw(this, inventoryX(), top + 8);

    }

    @Override
    protected void drawOverlay(int mouseX, int mouseY) {
        if (dragging != null) drawStack(dragging, mouseX - 8, mouseY - 8);
        if (hoveredEntry != null) {
            drawTooltip(Arrays.asList(hoveredEntry.displayName(), hoveredEntry.id()), mouseX, mouseY);
        }
    }

    /**
     * Draws the wall using each block's real side texture, which is the face a
     * wall actually shows. The full wall is sampled down to the preview grid, so
     * what you see is the whole gradient at the size it reads from a distance.
     */
    private void drawBlendPreview() {
        int x0 = left + 12;
        int y0 = top + PREVIEW_Y;
        int w = PREVIEW_COLS * PREVIEW_CELL;
        int h = PREVIEW_ROWS * PREVIEW_CELL;

        rect(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, Theme.BORDER);
        rect(x0, y0, x0 + w, y0 + h, Theme.SLOT_DARK);

        if (result.isEmpty()) {
            text("Generate a gradient first", x0 + 6, y0 + h / 2 - 4, Theme.TEXT_FAINT);
            return;
        }
        if (blendDirty) rebuildBlend();
        if (blendGrid.length == 0) return;

        beginBlockFaces();
        for (int py = 0; py < PREVIEW_ROWS; py++) {
            int gy = py * wallHeight / PREVIEW_ROWS;
            for (int px = 0; px < PREVIEW_COLS; px++) {
                int gx = px * BLEND_COLS / PREVIEW_COLS;
                int idx = blendGrid[gy * BLEND_COLS + gx];
                if (idx < 0 || idx >= result.size()) continue;

                Entry e = result.get(idx);
                IIcon icon = e.sideIcon();
                int cx = x0 + px * PREVIEW_CELL;
                int cy = y0 + py * PREVIEW_CELL;
                if (icon == null) rect(cx, cy, cx + PREVIEW_CELL, cy + PREVIEW_CELL, 0xFF000000 | e.rgb);
                else drawBlockFace(icon, e.tint, cx, cy, PREVIEW_CELL, PREVIEW_CELL);
            }
        }
        endBlockFaces();
    }

    /** Draws the result strip and returns the entry under the cursor, if any. */
    private Entry drawResult(int mouseX, int mouseY) {
        Entry hovered = null;
        int perRow = (secW() - 8) / SLOT;
        int limit = top + RESULT_Y + RESULT_H - 20;
        for (int i = 0; i < result.size(); i++) {
            int x = left + 12 + (i % perRow) * SLOT;
            int y = top + RESULT_GRID_Y + (i / perRow) * SLOT;
            if (y > limit) break; // clip overflow
            drawStack(result.get(i).stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = result.get(i);
        }
        return hovered;
    }

    private int inventoryX() {
        return left - InventoryStrip.width() - 8; // to the left of the panel
    }

    private String statusText() {
        switch (BlockColorIndex.state()) {
            case NOT_STARTED:
                return "Colour index: starting...";
            case INDEXING:
                return "Indexing blocks " + BlockColorIndex.progress() + " / " + BlockColorIndex.total();
            case READY:
                return "Palette: " + PaletteFilter.passing()
                    + " / "
                    + BlockColorIndex.palette()
                        .size()
                    + " pass filters";
            case FAILED:
                return "Colour index failed (see log)";
            default:
                return "";
        }
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1) { // right-click clears a waypoint
            for (int i = 0; i < waypointAreas.size(); i++) {
                if (waypointAreas.get(i)
                    .contains(mouseX, mouseY) && i < WAYPOINTS.size()) {
                    WAYPOINTS.remove(i);
                    return;
                }
            }
        }
        if (button == 0) {
            ItemStack picked = inventory.pickBlock(mouseX, mouseY);
            if (picked != null) {
                dragging = picked;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0 && dragging != null) {
            for (int i = 0; i < waypointAreas.size(); i++) {
                if (waypointAreas.get(i)
                    .contains(mouseX, mouseY)) {
                    setWaypoint(Math.min(i, WAYPOINTS.size()), dragging);
                    break;
                }
            }
            dragging = null;
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }
}
