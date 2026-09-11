package com.gradientcraft.client.gui;

import com.gradientcraft.client.BlockColorIndex;
import com.gradientcraft.client.palette.PaletteEngine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * "Palette" tab: builds an N-block building palette. The first three roles are
 * Base / Wall / Accent; extra slots are harmonizing blocks. A slider sets how
 * many slots to generate (3-9). Randomize rolls a fresh palette; lock the roles
 * you like (left-click a slot, or drag a block onto it) and Reshuffle re-rolls
 * only the unlocked ones. Fully client-side.
 */
public class PaletteScreen extends Screen implements GhostSlots {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 226;
    private static final int SLOT = 18;
    private static final int MAX_SLOTS = 9;

    private static final Block[] BLOCKS = new Block[MAX_SLOTS];
    private static final boolean[] LOCKED = new boolean[MAX_SLOTS];
    private static int count = 6;

    private int left, top;
    private final List<Rect2i> slotAreas = new ArrayList<>();
    private final List<Rect2i> invAreas = new ArrayList<>();
    private ItemStack dragging = ItemStack.EMPTY;

    public PaletteScreen() { super(new TextComponent("Building Palette")); }

    private static String fullRole(int i) {
        switch (i) {
            case 0: return "Base (Structural)";
            case 1: return "Wall / Texture";
            case 2: return "Accent";
            case 3: return "Trim / Detail";
            case 4: return "Secondary";
            case 5: return "Highlight";
            default: return "Extra " + (i + 1);
        }
    }
    private static String tagRole(int i) {
        switch (i) {
            case 0: return "Base"; case 1: return "Wall"; case 2: return "Acc";
            case 3: return "Trim"; case 4: return "2nd";  case 5: return "Hi";
            default: return "E" + (i + 1);
        }
    }

    @Override
    protected void init() {
        BlockColorIndex.ensureStarted();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        // tabs
        addRenderableWidget(new Button(left + 4, top - 22, 70, 20, new TextComponent("Gradient"),
                b -> Minecraft.getInstance().setScreen(new GradientScreen())));
        addRenderableWidget(new Button(left + 78, top - 22, 70, 20, new TextComponent("Generate"),
                b -> Minecraft.getInstance().setScreen(new GenerateScreen())));
        addRenderableWidget(new Button(left + 152, top - 22, 70, 20, new TextComponent("Colors"),
                b -> Minecraft.getInstance().setScreen(new ColorScreen())));
        Button tabPal = new Button(left + 226, top - 22, 70, 20, new TextComponent("Palette"), b -> {});
        tabPal.active = false;
        addRenderableWidget(tabPal);
        addRenderableWidget(new Button(left + 300, top - 22, 58, 20, new TextComponent("Lab"),
                b -> Minecraft.getInstance().setScreen(new LabScreen())));

        // slot-count slider
        addRenderableWidget(new CountSlider(left + 12, top + 80, 130, 16));

        // actions
        addRenderableWidget(new Button(left + 12, top + 100, 90, 20, new TextComponent("Randomize"),
                b -> randomizeAll()));
        addRenderableWidget(new Button(left + 106, top + 100, 90, 20, new TextComponent("Reshuffle"),
                b -> reshuffle()));
        addRenderableWidget(new Button(left + 200, top + 100, 88, 20, new TextComponent("Copy ids"),
                b -> copyIds()));

        computeSlots();
    }

    private void computeSlots() {
        slotAreas.clear();
        int pitch = SLOT + 4;
        int rowW = count * pitch - 4;
        int startX = left + (PANEL_W - rowW) / 2;
        for (int i = 0; i < count; i++) {
            slotAreas.add(new Rect2i(startX + i * pitch, top + 36, SLOT, SLOT));
        }
    }

    private void randomizeAll() {
        if (!BlockColorIndex.isReady()) return;
        for (int i = 0; i < MAX_SLOTS; i++) LOCKED[i] = false;
        Block[] res = PaletteEngine.generate(new Block[count], count, System.nanoTime());
        System.arraycopy(res, 0, BLOCKS, 0, count);
    }

    private void reshuffle() {
        if (!BlockColorIndex.isReady()) return;
        Block[] locked = new Block[count];
        for (int i = 0; i < count; i++) locked[i] = LOCKED[i] ? BLOCKS[i] : null;
        Block[] res = PaletteEngine.generate(locked, count, System.nanoTime());
        for (int i = 0; i < count; i++) if (!LOCKED[i]) BLOCKS[i] = res[i];
    }

    /** Fill only currently-empty slots (used on first open and when raising the count). */
    private void fillEmpty() {
        if (!BlockColorIndex.isReady()) return;
        Block[] anchors = new Block[count];
        for (int i = 0; i < count; i++) anchors[i] = BLOCKS[i]; // existing blocks anchor the rest
        Block[] res = PaletteEngine.generate(anchors, count, System.nanoTime());
        for (int i = 0; i < count; i++) if (BLOCKS[i] == null) BLOCKS[i] = res[i];
    }

    private void copyIds() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (BLOCKS[i] == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Registry.BLOCK.getKey(BLOCKS[i]));
        }
        if (sb.length() > 0) Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    }

    // ---- GhostSlots ----
    @Override public List<Rect2i> getWaypointAreas() { return slotAreas; }
    @Override public int guiLeft()   { return left; }
    @Override public int guiTop()    { return top; }
    @Override public int guiWidth()  { return PANEL_W; }
    @Override public int guiHeight() { return PANEL_H; }

    @Override
    public void setWaypointFromStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= count) return;
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return;
        BLOCKS[slot] = bi.getBlock();
        LOCKED[slot] = true; // choosing a block locks that role
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        computeSlots();
        if (BlockColorIndex.isReady() && allEmpty()) fillEmpty();

        this.renderBackground(pose);
        fill(pose, left, top, left + PANEL_W, top + PANEL_H, 0xF0101018);
        fill(pose, left, top, left + PANEL_W, top + 22, 0xFF2B2B3A);
        this.font.draw(pose, this.title, left + 8, top + 7, 0xFFFFFF);

        // slots + short labels
        int hoveredRole = -1;
        for (int i = 0; i < count; i++) {
            Rect2i r = slotAreas.get(i);
            fill(pose, r.getX() - 1, r.getY() - 1, r.getX() + SLOT + 1, r.getY() + SLOT + 1,
                    LOCKED[i] ? 0xFFFFD060 : 0xFF202030);
            fill(pose, r.getX(), r.getY(), r.getX() + SLOT, r.getY() + SLOT, 0xFF3A3A4A);
            if (BLOCKS[i] != null) this.itemRenderer.renderAndDecorateItem(new ItemStack(BLOCKS[i]), r.getX() + 1, r.getY() + 1);
            String tag = tagRole(i);
            this.font.draw(pose, new TextComponent(tag), r.getX() + 9 - this.font.width(tag) / 2, top + 56, 0xC0C0D0);
            if (mouseX >= r.getX() && mouseX < r.getX() + 16 && mouseY >= r.getY() && mouseY < r.getY() + 16)
                hoveredRole = i;
        }

        // info line for hovered slot, else a hint
        String info;
        if (hoveredRole >= 0) {
            String name = BLOCKS[hoveredRole] != null ? new ItemStack(BLOCKS[hoveredRole]).getHoverName().getString() : "(empty)";
            info = fullRole(hoveredRole) + " - " + name + (LOCKED[hoveredRole] ? "  [locked]" : "");
        } else {
            info = "left-click = lock,  right-click = clear,  drag a block to set";
        }
        this.font.draw(pose, new TextComponent(this.font.plainSubstrByWidth(info, PANEL_W - 16)),
                left + 8, top + 68, 0xA0A0B0);

        // inventory
        this.font.draw(pose, new TextComponent("Inventory"), left + 12, top + 124, 0xC8C8D8);
        drawInventory(pose);

        String status;
        switch (BlockColorIndex.state()) {
            case READY -> status = "Lock the roles you like, then Reshuffle the rest";
            case INDEXING -> status = "Indexing " + BlockColorIndex.progress() + "/" + BlockColorIndex.total();
            case FAILED -> status = "Color index failed";
            default -> status = "Starting...";
        }
        this.font.draw(pose, new TextComponent(status), left + 8, top + PANEL_H - 11, 0xA0A0B0);

        super.render(pose, mouseX, mouseY, partial);

        if (!dragging.isEmpty()) this.itemRenderer.renderAndDecorateItem(dragging, mouseX - 8, mouseY - 8);
        if (hoveredRole >= 0 && BLOCKS[hoveredRole] != null)
            renderTooltip(pose, new ItemStack(BLOCKS[hoveredRole]), mouseX, mouseY);
    }

    private boolean allEmpty() {
        for (int i = 0; i < count; i++) if (BLOCKS[i] != null) return false;
        return true;
    }

    private void drawInventory(PoseStack pose) {
        invAreas.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        NonNullList<ItemStack> items = mc.player.getInventory().items;
        int ix = left + (PANEL_W - 9 * SLOT) / 2;
        int iy = top + 134;
        for (int i = 0; i < items.size(); i++) {
            int x = ix + (i % 9) * SLOT, y = iy + (i / 9) * SLOT;
            invAreas.add(new Rect2i(x, y, SLOT, SLOT));
            fill(pose, x, y, x + SLOT, y + SLOT, 0xFF2A2A38);
            ItemStack s = items.get(i);
            if (!s.isEmpty()) this.itemRenderer.renderAndDecorateItem(s, x + 1, y + 1);
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < count; i++) {
            if (contains(slotAreas.get(i), mx, my)) {
                if (button == 1) { BLOCKS[i] = null; LOCKED[i] = false; }     // right: clear
                else if (button == 0) { LOCKED[i] = !LOCKED[i]; }             // left: toggle lock
                return true;
            }
        }
        if (button == 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                NonNullList<ItemStack> items = mc.player.getInventory().items;
                for (int i = 0; i < invAreas.size(); i++) {
                    if (contains(invAreas.get(i), mx, my)) {
                        ItemStack s = items.get(i);
                        if (!s.isEmpty() && s.getItem() instanceof BlockItem) { dragging = s.copy(); return true; }
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging.isEmpty()) {
            for (int i = 0; i < count; i++) {
                if (contains(slotAreas.get(i), mx, my)) { setWaypointFromStack(i, dragging); break; }
            }
            dragging = ItemStack.EMPTY;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private static boolean contains(Rect2i r, double x, double y) {
        return x >= r.getX() && x < r.getX() + r.getWidth() && y >= r.getY() && y < r.getY() + r.getHeight();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // slot-count slider (3..9)
    private class CountSlider extends AbstractSliderButton {
        CountSlider(int x, int y, int w, int h) {
            super(x, y, w, h, new TextComponent(""), (count - 3) / 6.0);
            updateMessage();
        }
        @Override protected void updateMessage() { setMessage(new TextComponent("Slots: " + count)); }
        @Override protected void applyValue() {
            int n = 3 + (int) Math.round(this.value * 6);
            if (n != count) { count = n; fillEmpty(); }
        }
    }
}
