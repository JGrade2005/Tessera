package com.builderstoolkit.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * Empty container backing the toolkit screens.
 *
 * The screens hold no inventory, but NEI only draws its item panel over a
 * GuiContainer, so being one is what makes NEI visible here. It has no slots,
 * so nothing is ever transferred and the server is never told about it.
 */
public class ToolkitContainer extends Container {

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
