package com.builderstoolkit.client.gui;

import net.minecraft.item.ItemStack;

/**
 * A screen with slots that accept a block dropped from outside, such as NEI's
 * item panel. Deliberately free of NEI types so the screens keep compiling and
 * running when NEI is absent.
 */
public interface GhostTarget {

    /**
     * @return true if the stack landed in a slot at that position
     */
    boolean acceptGhost(int mouseX, int mouseY, ItemStack stack);
}
