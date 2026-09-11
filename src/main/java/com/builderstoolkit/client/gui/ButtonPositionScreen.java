package com.builderstoolkit.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.builderstoolkit.client.gui.widget.FlatButton;
import com.builderstoolkit.config.ModConfig;

/**
 * Drag-to-place editor for the inventory button.
 *
 * Draws a stand-in of the survival inventory at the size and position the real
 * one uses, so what you see here is where the button lands. Reached by
 * shift-clicking the button itself.
 */
public class ButtonPositionScreen extends GuiScreen {

    private static final int INV_W = 176;
    private static final int INV_H = 166;
    private static final int BTN = 20;
    private static final int SLOT = 18;

    /** How far outside the inventory the button may be dragged. */
    private static final int MARGIN = 60;

    private final GuiScreen parent;

    private int invLeft;
    private int invTop;
    private int offsetX;
    private int offsetY;
    private boolean dragging;
    private int grabX;
    private int grabY;

    public ButtonPositionScreen(GuiScreen parent) {
        this.parent = parent;
        this.offsetX = ModConfig.buttonX();
        this.offsetY = ModConfig.buttonY();
    }

    @Override
    public void initGui() {
        this.invLeft = (this.width - INV_W) / 2;
        this.invTop = (this.height - INV_H) / 2;
        this.buttonList.clear();

        int y = invTop + INV_H + 24;
        this.buttonList.add(new FlatButton(invLeft, y, 84, 20, "Reset", () -> {
            offsetX = ModConfig.DEFAULT_BUTTON_X;
            offsetY = ModConfig.DEFAULT_BUTTON_Y;
        }));
        this.buttonList.add(new FlatButton(invLeft + 88, y, 84, 20, "Cancel", this::close));
        this.buttonList.add(new FlatButton(invLeft + INV_W - 84, y, 84, 20, "Save", () -> {
            ModConfig.setButtonPosition(offsetX, offsetY);
            close();
        }).primary());
    }

    private void close() {
        Minecraft.getMinecraft()
            .displayGuiScreen(parent);
    }

    private int btnX() {
        return invLeft + offsetX;
    }

    private int btnY() {
        return invTop + offsetY;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawCenteredString(
            this.fontRendererObj,
            "Drag the button to where you want it",
            this.width / 2,
            invTop - 44,
            Theme.TEXT);
        drawCenteredString(
            this.fontRendererObj,
            "Position is relative to the inventory, so it follows the GUI",
            this.width / 2,
            invTop - 32,
            Theme.TEXT_FAINT);

        drawMockInventory();

        // The button being placed.
        int bx = btnX();
        int by = btnY();
        drawRect(bx - 1, by - 1, bx + BTN + 1, by + BTN + 1, Theme.LOCKED);
        drawRect(bx, by, bx + BTN, by + BTN, Theme.WIDGET);
        drawCenteredString(this.fontRendererObj, "G", bx + BTN / 2, by + (BTN - 8) / 2, Theme.TEXT);

        drawCenteredString(
            this.fontRendererObj,
            "x " + offsetX + "   y " + offsetY,
            this.width / 2,
            invTop + INV_H + 10,
            Theme.TEXT_FAINT);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** A recognisable stand-in for the vanilla inventory, at its real size. */
    private void drawMockInventory() {
        drawRect(invLeft - 1, invTop - 1, invLeft + INV_W + 1, invTop + INV_H + 1, 0xFF373737);
        drawRect(invLeft, invTop, invLeft + INV_W, invTop + INV_H, 0xFFC6C6C6);
        this.fontRendererObj.drawString("Crafting", invLeft + 97, invTop + 6, 0x404040);

        // main grid, then the hotbar below it
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                mockSlot(invLeft + 7 + col * SLOT, invTop + 83 + row * SLOT);
            }
        }
        for (int col = 0; col < 9; col++) {
            mockSlot(invLeft + 7 + col * SLOT, invTop + 141);
        }
        // player pane
        drawRect(invLeft + 7, invTop + 7, invLeft + 7 + 54, invTop + 7 + 70, 0xFF8B8B8B);
    }

    private void mockSlot(int x, int y) {
        drawRect(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int bx = btnX();
        int by = btnY();
        if (button == 0 && mouseX >= bx && mouseX < bx + BTN && mouseY >= by && mouseY < by + BTN) {
            dragging = true;
            grabX = mouseX - bx;
            grabY = mouseY - by;
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceClick) {
        if (!dragging) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
            return;
        }
        offsetX = clamp(mouseX - grabX - invLeft, -MARGIN, INV_W + MARGIN - BTN);
        offsetY = clamp(mouseY - grabY - invTop, -MARGIN, INV_H + MARGIN - BTN);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) dragging = false;
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof FlatButton) ((FlatButton) button).onClick();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
