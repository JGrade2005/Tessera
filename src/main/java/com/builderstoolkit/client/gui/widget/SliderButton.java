package com.builderstoolkit.client.gui.widget;

import java.util.Locale;
import java.util.function.DoubleConsumer;

import net.minecraft.client.Minecraft;

import com.builderstoolkit.client.gui.Theme;

/**
 * Flat slider over an arbitrary [min, max] range. 1.7.10 only ships a slider
 * bound to the options screen, and the vanilla widget texture does not tile at
 * the compact heights the Lab grid uses, so this draws itself.
 */
public final class SliderButton extends FlatButton {

    private final double min;
    private final double max;
    private final boolean integer;
    private final String label;
    private final DoubleConsumer onChange;

    private double fraction;
    private boolean dragging;

    public SliderButton(int x, int y, int width, int height, String label, double min, double max, boolean integer,
        double initial, DoubleConsumer onChange) {
        super(x, y, width, height, "", null);
        this.label = label;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.onChange = onChange;
        this.fraction = max > min ? clamp01((initial - min) / (max - min)) : 0.0;
    }

    public double value() {
        double v = min + (max - min) * fraction;
        return integer ? Math.round(v) : v;
    }

    private void setFromMouse(int mouseX) {
        int span = this.width - 4;
        fraction = span <= 0 ? 0 : clamp01((mouseX - (this.xPosition + 2)) / (double) span);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!super.mousePressed(mc, mouseX, mouseY)) return false;
        setFromMouse(mouseX);
        dragging = true;
        if (onChange != null) onChange.accept(value());
        return true;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (!dragging) return;
        double before = fraction;
        setFromMouse(mouseX);
        if (fraction != before && onChange != null) onChange.accept(value());
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) return;

        int x2 = this.xPosition + this.width;
        int y2 = this.yPosition + this.height;
        boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < x2 && mouseY < y2;

        // Track uses the theme widget style so sliders match the buttons.
        drawFrame(this.xPosition, this.yPosition, x2, y2, Theme.WIDGET_OFF, Theme.WIDGET_EDGE, false);

        // filled portion, then the knob
        int span = this.width - 4;
        int fill = (int) Math.round(fraction * span);
        if (fill > 0) {
            drawRect(
                this.xPosition + 2,
                this.yPosition + 2,
                this.xPosition + 2 + fill,
                y2 - 2,
                hovered ? Theme.WIDGET_HOVER : Theme.WIDGET);
        }
        int knob = this.xPosition + 2 + fill;
        drawRect(knob - 1, this.yPosition + 1, knob + 1, y2 - 1, Theme.TEXT_DIM);

        this.displayString = label + ": " + format();
        String text = mc.fontRenderer.trimStringToWidth(this.displayString, this.width - 4);
        drawCenteredString(
            mc.fontRenderer,
            text,
            this.xPosition + this.width / 2,
            this.yPosition + (this.height - 8) / 2,
            Theme.TEXT);

        mouseDragged(mc, mouseX, mouseY);
    }

    private String format() {
        double v = value();
        return integer ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
