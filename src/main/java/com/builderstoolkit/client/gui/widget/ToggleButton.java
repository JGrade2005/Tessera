package com.builderstoolkit.client.gui.widget;

import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;

/**
 * On/off button that reads its state back from the owning screen each frame, so
 * the label stays correct without the screen having to relabel it by hand.
 */
public final class ToggleButton extends FlatButton {

    private final String label;
    private final BooleanSupplier state;

    public ToggleButton(int x, int y, int width, int height, String label, BooleanSupplier state, Runnable toggle) {
        super(x, y, width, height, label, toggle);
        this.label = label;
        this.state = state;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        this.displayString = label + ": " + (state.getAsBoolean() ? "ON" : "OFF");
        super.drawButton(mc, mouseX, mouseY);
    }
}
