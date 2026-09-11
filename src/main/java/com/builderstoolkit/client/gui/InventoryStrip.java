package com.builderstoolkit.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * The player inventory drawn as a drag source. Shared by the tabs that let you
 * drag a block into a slot.
 */
public final class InventoryStrip {

    public static final int SLOT = 18;
    private static final int COLS = 9;

    private final List<Rect> areas = new ArrayList<Rect>();

    public void draw(ToolkitScreen screen, int x, int y) {
        areas.clear();
        ItemStack[] items = items();
        if (items == null) return;

        for (int i = 0; i < items.length; i++) {
            int sx = x + (i % COLS) * SLOT;
            int sy = y + (i / COLS) * SLOT;
            areas.add(new Rect(sx, sy, SLOT, SLOT));
            screen.rect(sx, sy, sx + SLOT, sy + SLOT, Theme.SLOT_DARK);
            if (items[i] != null) screen.drawStack(items[i], sx + 1, sy + 1);
        }
    }

    /** Width of the drawn grid, for centring. */
    public static int width() {
        return COLS * SLOT;
    }

    /** The block stack under the cursor, or null if there is none to drag. */
    public ItemStack pickBlock(int mouseX, int mouseY) {
        ItemStack[] items = items();
        if (items == null) return null;
        for (int i = 0; i < areas.size() && i < items.length; i++) {
            if (!areas.get(i)
                .contains(mouseX, mouseY)) continue;
            ItemStack s = items[i];
            if (s != null && s.getItem() instanceof ItemBlock) return s.copy();
            return null;
        }
        return null;
    }

    private static ItemStack[] items() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? null : mc.thePlayer.inventory.mainInventory;
    }
}
