package com.builderstoolkit.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import com.builderstoolkit.client.gui.Theme;

/**
 * Button carrying its own click action, so screens do not dispatch on button
 * ids. Drawn with plain rectangles rather than the vanilla widget texture,
 * which only tiles correctly at 20px tall, and restyled by the active theme.
 */
public class FlatButton extends GuiButton {

    private final Runnable action;
    private boolean primary;

    public FlatButton(int x, int y, int width, int height, String text, Runnable action) {
        super(0, x, y, width, height, text);
        this.action = action;
    }

    /** Marks this as the screen's main action, which the theme colours distinctly. */
    public FlatButton primary() {
        this.primary = true;
        return this;
    }

    public void onClick() {
        if (action != null) action.run();
    }

    /** Background colour; subclasses override to signal state. */
    protected int bodyColor(boolean hovered) {
        if (primary) return Theme.PRIMARY;
        if (!this.enabled) return Theme.WIDGET_OFF;
        return hovered ? Theme.WIDGET_HOVER : Theme.WIDGET;
    }

    protected int edgeColor() {
        return primary ? Theme.PRIMARY_EDGE : Theme.WIDGET_EDGE;
    }

    protected int labelColor() {
        if (primary) return Theme.PRIMARY_TEXT;
        return this.enabled ? Theme.TEXT : Theme.TEXT_FAINT;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) return;

        int x2 = this.xPosition + this.width;
        int y2 = this.yPosition + this.height;
        boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < x2 && mouseY < y2;

        drawFrame(this.xPosition, this.yPosition, x2, y2, bodyColor(hovered), edgeColor(), hovered);

        if (this.displayString != null && this.displayString.length() > 0) {
            String text = mc.fontRenderer.trimStringToWidth(this.displayString, this.width - 4);
            drawCenteredString(
                mc.fontRenderer,
                text,
                this.xPosition + this.width / 2,
                this.yPosition + (this.height - 8) / 2,
                labelColor());
        }
    }

    /** Shared widget body, drawn according to the active theme style. */
    static void drawFrame(int x1, int y1, int x2, int y2, int body, int edge, boolean hovered) {
        switch (Theme.STYLE) {
            case OUTLINE:
                // Border only; hover fills faintly instead of brightening.
                drawRect(x1, y1, x2, y2, hovered ? Theme.WIDGET_HOVER : body);
                drawRect(x1, y1, x2, y1 + 1, edge);
                drawRect(x1, y2 - 1, x2, y2, edge);
                drawRect(x1, y1, x1 + 1, y2, edge);
                drawRect(x2 - 1, y1, x2, y2, edge);
                break;
            case BEVEL:
                // Raised: light along the top and left, dark along the bottom and right.
                drawRect(x1, y1, x2, y2, body);
                drawRect(x1, y1, x2, y1 + 1, Theme.BEVEL_LIGHT);
                drawRect(x1, y1, x1 + 1, y2, Theme.BEVEL_LIGHT);
                drawRect(x1, y2 - 1, x2, y2, edge);
                drawRect(x2 - 1, y1, x2, y2, edge);
                break;
            default:
                drawRect(x1, y1, x2, y2, edge);
                drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, body);
                break;
        }
    }
}
