package com.builderstoolkit.compat.nei;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.builderstoolkit.client.gui.GhostTarget;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;

/**
 * Lets blocks be dragged out of the NEI item panel into the waypoint slots.
 *
 * Every method is implemented, not just the one we care about: older NEI builds
 * declare them all abstract, and only recent ones give them defaults. Relying on
 * the defaults throws AbstractMethodError on anything older.
 */
public class ToolkitNEIHandler implements INEIGuiHandler {

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        if (!(gui instanceof GhostTarget) || draggedStack == null) return false;
        return ((GhostTarget) gui).acceptGhost(mousex, mousey, draggedStack);
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return null;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return false;
    }
}
