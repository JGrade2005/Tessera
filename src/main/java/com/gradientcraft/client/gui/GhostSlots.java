package com.gradientcraft.client.gui;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Implemented by screens that accept blocks dragged from the inventory or JEI
 * into a row of ghost slots. The JEI plugin registers one shared handler for
 * every implementor, and uses the geometry methods to make JEI show its
 * ingredient list beside the screen.
 */
public interface GhostSlots {
    List<Rect2i> getWaypointAreas();
    void setWaypointFromStack(int slot, ItemStack stack);
    int guiLeft();
    int guiTop();
    int guiWidth();
    int guiHeight();
}
