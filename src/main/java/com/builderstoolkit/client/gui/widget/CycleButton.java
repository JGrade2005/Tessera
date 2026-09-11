package com.builderstoolkit.client.gui.widget;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;

/** Button that steps through a set of values, showing the current one. */
public final class CycleButton extends FlatButton {

    private final String label;
    private final Supplier<String> value;

    public CycleButton(int x, int y, int width, int height, String label, Supplier<String> value, Runnable next) {
        super(x, y, width, height, label, next);
        this.label = label;
        this.value = value;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        this.displayString = label + ": " + value.get();
        super.drawButton(mc, mouseX, mouseY);
    }
}
