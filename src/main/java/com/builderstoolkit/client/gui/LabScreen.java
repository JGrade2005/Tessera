package com.builderstoolkit.client.gui;

import java.util.Random;

import net.minecraft.client.gui.GuiTextField;

import com.builderstoolkit.client.generate.LabShape;
import com.builderstoolkit.client.generate.Rotate3D;
import com.builderstoolkit.client.gui.widget.FlatButton;
import com.builderstoolkit.client.gui.widget.SliderButton;
import com.builderstoolkit.client.gui.widget.ToggleButton;

/**
 * Lab tab: a parametric shape sandbox. The sliders and toggles drive
 * {@link LabShape}, which builds a WorldEdit expression previewed live.
 */
public class LabScreen extends ToolkitScreen {

    private static final String[] SL_LABEL = { "size", "square", "stretchX", "stretchY", "stretchZ", "taper",
        "twistAmt", "rippleF", "rippleA", "wobbleF", "wobbleA", "thick" };
    private static final double[] SL_MIN = { 0.3, 2, 0.5, 0.5, 0.5, 0, 0, 1, 0, 1, 0, 0.03 };
    private static final double[] SL_MAX = { 1.0, 8, 2.0, 2.0, 2.0, 0.6, 4, 12, 0.3, 10, 0.3, 0.3 };
    private static final boolean[] SL_INT = { false, false, false, false, false, false, false, true, false, true, false,
        false };
    private static final double[] DEFAULTS = { 0.8, 2, 1, 1, 1, 0, 1.5, 5, 0.15, 5, 0.15, 0.1 };

    private static final String[] ROT_LABEL = { "rotX", "rotY", "rotZ" };
    private static final String[] CK_LABEL = { "twist", "ripple", "wobble", "hollow", "invert", "rainbow" };

    private static final int ROWS_PER_COL = 11;
    private static final int PREVIEW = 150;

    // Persistent across reopen and tab switches.
    private static final double[] LV = DEFAULTS.clone();
    private static final double[] ROT = new double[3];
    private static final boolean[] CV = new boolean[CK_LABEL.length];
    private static String pattern = "stone";
    private static int res = 22;
    private static boolean spin = true;

    private final ShapePreview preview = new ShapePreview();
    private boolean dirty = true;
    private boolean pendingRebuild;
    private GuiTextField patternBox;
    private String lastPattern = "";

    public LabScreen() {
        super("Shape Lab", Tab.LAB, 362, 276);
    }

    private static String currentExpr() {
        return Rotate3D.wrap(LabShape.build(LV, CV), ROT[0], ROT[1], ROT[2]);
    }

    private static String currentCommand() {
        return "//g " + pattern + " " + currentExpr();
    }

    @Override
    protected void build() {
        int slot = 0;
        for (int i = 0; i < SL_LABEL.length; i++) {
            addSlider(slot++, SL_LABEL[i], SL_MIN[i], SL_MAX[i], SL_INT[i], LV, i);
        }
        for (int i = 0; i < ROT_LABEL.length; i++) {
            addSlider(slot++, ROT_LABEL[i], 0, 360, true, ROT, i);
        }
        for (int i = 0; i < CK_LABEL.length; i++) {
            addToggle(slot++, i);
        }

        patternBox = new GuiTextField(this.fontRendererObj, left + 8, top + 212, 120, 16);
        patternBox.setMaxStringLength(128);
        patternBox.setText(pattern);
        lastPattern = pattern;

        // Rebuilds are deferred: these run while the button list is being iterated.
        buttonList.add(new FlatButton(left + 8, top + 232, 70, 18, "Random", () -> {
            randomize();
            pendingRebuild = true;
        }));
        buttonList.add(new FlatButton(left + 82, top + 232, 60, 18, "Reset", () -> {
            reset();
            pendingRebuild = true;
        }));
        buttonList
            .add(new FlatButton(left + 146, top + 232, 84, 18, "Copy cmd", () -> copyToClipboard(currentCommand())));
        buttonList
            .add(new FlatButton(left + 234, top + 232, 70, 18, "Run", () -> sendChat(currentCommand())).primary());

        buttonList.add(new SliderButton(left + 206, top + 186, 100, 14, "res", 8, 32, true, res, v -> {
            res = (int) v;
            dirty = true;
        }));

        dirty = true;
    }

    private void addSlider(int slot, String label, double min, double max, boolean integer, final double[] target,
        final int idx) {
        int x = left + 8 + (slot / ROWS_PER_COL) * 94;
        int y = top + 30 + (slot % ROWS_PER_COL) * 16;
        buttonList.add(new SliderButton(x, y, 90, 14, label, min, max, integer, target[idx], v -> {
            target[idx] = v;
            dirty = true;
        }));
    }

    private void addToggle(int slot, final int idx) {
        int x = left + 8 + (slot / ROWS_PER_COL) * 94;
        int y = top + 30 + (slot % ROWS_PER_COL) * 16;
        buttonList.add(new ToggleButton(x, y, 90, 14, CK_LABEL[idx], () -> CV[idx], () -> {
            CV[idx] = !CV[idx];
            dirty = true;
        }));
    }

    private void randomize() {
        Random r = new Random();
        for (int i = 0; i < LV.length; i++) {
            double v = SL_MIN[i] + r.nextDouble() * (SL_MAX[i] - SL_MIN[i]);
            LV[i] = SL_INT[i] ? Math.round(v) : v;
        }
        for (int i = 0; i < ROT.length; i++) ROT[i] = r.nextInt(360);
        for (int i = 0; i < CV.length; i++) CV[i] = r.nextBoolean();
        dirty = true;
    }

    private void reset() {
        System.arraycopy(DEFAULTS, 0, LV, 0, DEFAULTS.length);
        for (int i = 0; i < ROT.length; i++) ROT[i] = 0;
        for (int i = 0; i < CV.length; i++) CV[i] = false;
        dirty = true;
    }

    // ---- rendering ----

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (dirty) {
            dirty = false;
            preview.sample(currentExpr(), pattern, res);
        }
        drawPanel();

        Rect box = previewBox();
        preview.draw(this, box.x, box.y, box.w, box.h, spin);
        textClipped(
            preview.status(),
            box.x,
            box.y + box.h + 18,
            box.w + 4,
            preview.error() != null ? Theme.TEXT_ERROR : Theme.TEXT_FAINT);
        textClipped(currentCommand(), left + 8, top + 256, panelW - 16, Theme.TEXT_OK);

        if (patternBox != null) patternBox.drawTextBox();

    }

    private Rect previewBox() {
        return new Rect(left + 206, top + 28, PREVIEW, PREVIEW);
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (patternBox != null) patternBox.mouseClicked(mouseX, mouseY, button);
        if (preview.mousePressed(mouseX, mouseY, previewBox())) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceClick) {
        preview.mouseDragged(mouseX, mouseY);
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) preview.mouseReleased();
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode != 1 && patternBox != null && patternBox.isFocused()) {
            patternBox.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        if (patternBox != null) {
            patternBox.updateCursorCounter();
            String now = patternBox.getText();
            if (!now.equals(lastPattern)) {
                lastPattern = now;
                pattern = now;
                dirty = true;
            }
        }
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
    }
}
