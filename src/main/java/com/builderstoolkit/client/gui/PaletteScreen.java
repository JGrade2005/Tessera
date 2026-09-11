package com.builderstoolkit.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.builderstoolkit.client.BlockColorIndex;
import com.builderstoolkit.client.BlockColorIndex.Entry;
import com.builderstoolkit.client.gui.widget.FlatButton;
import com.builderstoolkit.client.gui.widget.SliderButton;
import com.builderstoolkit.client.palette.PaletteEngine;

/**
 * Palette tab: builds an N-block building palette. Slots 0-2 are Base, Wall and
 * Accent; the rest harmonise with them. Lock the roles you like and reshuffle
 * the others.
 */
public class PaletteScreen extends ToolkitScreen implements GhostTarget {

    private static final int SLOT = 18;
    private static final int MAX_SLOTS = 9;

    private static final String[] ROLE_LONG = { "Base (Structural)", "Wall / Texture", "Accent", "Trim / Detail",
        "Secondary", "Highlight" };
    private static final String[] ROLE_SHORT = { "Base", "Wall", "Acc", "Trim", "2nd", "Hi" };

    private static final Entry[] SLOTS = new Entry[MAX_SLOTS];
    private static final boolean[] LOCKED = new boolean[MAX_SLOTS];
    private static int count = 6;

    private final List<Rect> slotAreas = new ArrayList<Rect>();
    private final InventoryStrip inventory = new InventoryStrip();
    private ItemStack dragging;
    /** Set while painting, consumed by drawOverlay. */
    private int hoveredRole = -1;

    public PaletteScreen() {
        super("Building Palette", Tab.PALETTE, 300, 262);
    }

    private static String roleLong(int i) {
        return i < ROLE_LONG.length ? ROLE_LONG[i] : "Extra " + (i + 1);
    }

    private static String roleShort(int i) {
        return i < ROLE_SHORT.length ? ROLE_SHORT[i] : "E" + (i + 1);
    }

    @Override
    protected void build() {
        BlockColorIndex.ensureStarted();

        buttonList.add(new SliderButton(left + 12, top + 80, 130, 16, "Slots", 3, MAX_SLOTS, true, count, v -> {
            int n = (int) v;
            if (n != count) {
                count = n;
                computeSlots();
                fillEmpty();
            }
        }));

        buttonList.add(new FlatButton(left + 12, top + 100, 90, 20, "Randomize", this::randomizeAll).primary());
        buttonList.add(new FlatButton(left + 106, top + 100, 90, 20, "Reshuffle", this::reshuffle));
        buttonList.add(new FlatButton(left + 200, top + 100, 88, 20, "Copy ids", this::copyIds));

        addFilterToggles(left + 8, top + 222, panelW - 16, 4, 16, 2);

        computeSlots();
    }

    @Override
    public boolean acceptGhost(int mouseX, int mouseY, ItemStack stack) {
        for (int i = 0; i < count && i < slotAreas.size(); i++) {
            if (!slotAreas.get(i)
                .contains(mouseX, mouseY)) continue;
            Entry e = BlockColorIndex.find(stack);
            if (e == null) return false;
            SLOTS[i] = e;
            LOCKED[i] = true; // choosing a block locks that role
            return true;
        }
        return false;
    }

    private void computeSlots() {
        slotAreas.clear();
        int pitch = SLOT + 4;
        int startX = left + (panelW - (count * pitch - 4)) / 2;
        for (int i = 0; i < count; i++) {
            slotAreas.add(new Rect(startX + i * pitch, top + 36, SLOT, SLOT));
        }
    }

    private void randomizeAll() {
        if (!BlockColorIndex.isReady()) return;
        Arrays.fill(LOCKED, false);
        Entry[] res = PaletteEngine.generate(new Entry[count], count, System.nanoTime());
        System.arraycopy(res, 0, SLOTS, 0, count);
    }

    private void reshuffle() {
        if (!BlockColorIndex.isReady()) return;
        Entry[] locked = new Entry[count];
        for (int i = 0; i < count; i++) {
            locked[i] = LOCKED[i] ? SLOTS[i] : null;
        }
        Entry[] res = PaletteEngine.generate(locked, count, System.nanoTime());
        for (int i = 0; i < count; i++) {
            if (!LOCKED[i]) SLOTS[i] = res[i];
        }
    }

    /** Fill only empty slots, anchoring on whatever is already chosen. */
    private void fillEmpty() {
        if (!BlockColorIndex.isReady()) return;
        Entry[] anchors = new Entry[count];
        System.arraycopy(SLOTS, 0, anchors, 0, count);
        Entry[] res = PaletteEngine.generate(anchors, count, System.nanoTime());
        for (int i = 0; i < count; i++) {
            if (SLOTS[i] == null) SLOTS[i] = res[i];
        }
    }

    private void copyIds() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (SLOTS[i] == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(SLOTS[i].id());
        }
        copyToClipboard(sb.toString());
    }

    private boolean allEmpty() {
        for (int i = 0; i < count; i++) {
            if (SLOTS[i] != null) return false;
        }
        return true;
    }

    // ---- rendering ----

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (BlockColorIndex.isReady() && allEmpty()) fillEmpty();
        drawPanel();

        hoveredRole = -1;
        for (int i = 0; i < count; i++) {
            Rect r = slotAreas.get(i);
            drawSlot(r.x, r.y, SLOT, LOCKED[i] ? Theme.LOCKED : Theme.BORDER);
            if (SLOTS[i] != null) drawStack(SLOTS[i].stack, r.x + 1, r.y + 1);

            String tag = roleShort(i);
            text(tag, r.x + 9 - this.fontRendererObj.getStringWidth(tag) / 2, top + 56, Theme.TEXT_DIM);
            if (r.contains(mouseX, mouseY)) hoveredRole = i;
        }

        String info;
        if (hoveredRole >= 0) {
            String name = SLOTS[hoveredRole] != null ? SLOTS[hoveredRole].displayName() : "(empty)";
            info = roleLong(hoveredRole) + " - " + name + (LOCKED[hoveredRole] ? "  [locked]" : "");
        } else {
            info = "left-click = lock,  right-click = clear,  drag a block to set";
        }
        textClipped(info, left + 8, top + 68, panelW - 16, Theme.TEXT_FAINT);

        text("Inventory", left + 12, top + 124, Theme.TEXT_DIM);
        text("Filters", left + 8, top + 212, Theme.TEXT_DIM);
        inventory.draw(this, left + (panelW - InventoryStrip.width()) / 2, top + 134);

        textClipped(statusText(), left + 8, top + panelH - 11, panelW - 16, Theme.TEXT_FAINT);

    }

    @Override
    protected void drawOverlay(int mouseX, int mouseY) {
        if (dragging != null) drawStack(dragging, mouseX - 8, mouseY - 8);
        if (hoveredRole >= 0 && SLOTS[hoveredRole] != null) {
            Entry e = SLOTS[hoveredRole];
            drawTooltip(Arrays.asList(e.displayName(), e.id()), mouseX, mouseY);
        }
    }

    private static String statusText() {
        switch (BlockColorIndex.state()) {
            case READY:
                return "Lock the roles you like, then Reshuffle the rest";
            case INDEXING:
                return "Indexing " + BlockColorIndex.progress() + "/" + BlockColorIndex.total();
            case FAILED:
                return "Colour index failed";
            default:
                return "Starting...";
        }
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        for (int i = 0; i < count; i++) {
            if (!slotAreas.get(i)
                .contains(mouseX, mouseY)) continue;
            if (button == 1) {
                SLOTS[i] = null;
                LOCKED[i] = false;
            } else if (button == 0) {
                LOCKED[i] = !LOCKED[i];
            }
            return;
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
            for (int i = 0; i < count; i++) {
                if (!slotAreas.get(i)
                    .contains(mouseX, mouseY)) continue;
                Entry e = BlockColorIndex.find(dragging);
                if (e != null) {
                    SLOTS[i] = e;
                    LOCKED[i] = true; // choosing a block locks that role
                }
                break;
            }
            dragging = null;
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }
}
