package com.builderstoolkit.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiTextField;

import com.builderstoolkit.client.CommandQueue;
import com.builderstoolkit.client.blend.ShapeGradient;
import com.builderstoolkit.client.generate.Rotate3D;
import com.builderstoolkit.client.generate.ShapePreset;
import com.builderstoolkit.client.gui.widget.CycleButton;
import com.builderstoolkit.client.gui.widget.FlatButton;
import com.builderstoolkit.client.gui.widget.SliderButton;
import com.builderstoolkit.client.gui.widget.ToggleButton;

/**
 * Generate tab: builds a WorldEdit //generate command from menus and previews
 * the resulting shape locally. Running it needs WorldEdit on the server; the
 * preview needs nothing but this mod.
 */
public class GenerateScreen extends ToolkitScreen {

    private static final int PREVIEW = 150;

    // Persistent across reopen and tab switches.
    private static int presetIndex = 1; // Torus
    private static double[] params = ShapePreset.PRESETS[1].defaults();
    private static String pattern = ShapePreset.PRESETS[1].defaultPattern;
    private static boolean hollow = ShapePreset.PRESETS[1].defaultHollow;
    private static String customExpr = "x^2+y^2+z^2 < 0.8^2";
    private static int res = 24;
    private static boolean spin = true;
    private static double rotX = 0, rotY = 0, rotZ = 0;
    private static ShapeGradient.Axis gradientAxis = ShapeGradient.Axis.Y;
    private static double gradientDither = 0.35;

    private final ShapePreview preview = new ShapePreview();
    private boolean dirty = true;
    private boolean pendingRebuild;

    private GuiTextField patternBox;
    private GuiTextField exprBox;
    private String lastPattern = "";
    private String lastExpr = "";

    public GenerateScreen() {
        super("Shape Generator", Tab.GENERATE, 330, 282);
    }

    private static ShapePreset preset() {
        return ShapePreset.PRESETS[presetIndex];
    }

    private static boolean isCustom() {
        return ShapePreset.isCustom(presetIndex);
    }

    /** The expression as shown in the box, before rotation. */
    private static String displayExpr() {
        return isCustom() ? customExpr : preset().build(params);
    }

    private static String currentExpr() {
        return Rotate3D.wrap(displayExpr(), rotX, rotY, rotZ);
    }

    private static String currentCommand() {
        return "//g " + (hollow ? "-h " : "") + pattern + " " + currentExpr();
    }

    @Override
    protected void build() {
        buttonList.add(new FlatButton(left + 8, top + 26, 16, 18, "<", () -> cyclePreset(-1)));
        buttonList.add(new FlatButton(left + 140, top + 26, 16, 18, ">", () -> cyclePreset(1)));

        int py = top + 48;
        ShapePreset.Param[] ps = preset().params;
        for (int i = 0; i < ps.length; i++) {
            final int idx = i;
            ShapePreset.Param p = ps[i];
            buttonList.add(new SliderButton(left + 8, py, 148, 18, p.label, p.min, p.max, p.integer, params[idx], v -> {
                params[idx] = v;
                refreshExprBox();
                dirty = true;
            }));
            py += 20;
        }

        patternBox = new GuiTextField(this.fontRendererObj, left + 8, top + 116, 148, 16);
        patternBox.setMaxStringLength(128);
        patternBox.setText(pattern);
        lastPattern = pattern;

        buttonList.add(new ToggleButton(left + 8, top + 136, 70, 18, "Hollow", () -> hollow, () -> {
            hollow = !hollow;
            dirty = true;
        }));
        buttonList.add(new ToggleButton(left + 86, top + 136, 70, 18, "Spin", () -> spin, () -> spin = !spin));
        buttonList.add(new SliderButton(left + 8, top + 156, 148, 18, "preview res", 8, 32, true, res, v -> {
            res = (int) v;
            dirty = true;
        }));

        buttonList.add(new SliderButton(left + 8, top + 178, 46, 14, "rX", 0, 360, true, rotX, v -> {
            rotX = v;
            dirty = true;
        }));
        buttonList.add(new SliderButton(left + 56, top + 178, 46, 14, "rY", 0, 360, true, rotY, v -> {
            rotY = v;
            dirty = true;
        }));
        buttonList.add(new SliderButton(left + 104, top + 178, 46, 14, "rZ", 0, 360, true, rotZ, v -> {
            rotZ = v;
            dirty = true;
        }));

        buttonList.add(new CycleButton(left + 8, top + 202, 86, 16, "Axis", () -> gradientAxis.name(), () -> {
            ShapeGradient.Axis[] axes = ShapeGradient.Axis.values();
            gradientAxis = axes[(gradientAxis.ordinal() + 1) % axes.length];
        }));
        buttonList.add(
            new SliderButton(
                left + 98,
                top + 202,
                110,
                16,
                "dither",
                0,
                100,
                true,
                gradientDither * 100,
                v -> gradientDither = v / 100.0));
        buttonList.add(new FlatButton(left + 212, top + 202, 110, 16, "Use as pattern", this::useGradient));

        exprBox = new GuiTextField(this.fontRendererObj, left + 8, top + 224, panelW - 16, 16);
        exprBox.setMaxStringLength(512);
        exprBox.setText(displayExpr());
        lastExpr = exprBox.getText();

        buttonList
            .add(new FlatButton(left + 8, top + 246, 62, 18, "Copy cmd", () -> copyToClipboard(currentCommand())));
        buttonList.add(new FlatButton(left + 74, top + 246, 62, 18, "Run cmd", () -> sendChat(currentCommand())));
        buttonList.add(new FlatButton(left + 140, top + 246, 74, 18, "Copy grad", this::copyGradientScript));
        buttonList
            .add(new FlatButton(left + 218, top + 246, 104, 18, "Run gradient", this::runGradientScript).primary());

        dirty = true;
    }

    /**
     * Builds the shape out of the Gradient tab blocks by using them as the //g
     * pattern. WorldEdit scatters a weighted pattern at random within the
     * shape, so this gives a speckled mix of the gradient rather than a
     * directional fade: the shape decides where blocks go, not the gradient.
     */
    private void useGradient() {
        String p = GradientScreen.gradientPattern();
        if (p == null) return;
        pattern = p;
        if (patternBox != null) {
            patternBox.setText(p);
            lastPattern = p;
        }
        dirty = true;
    }

    /**
     * Emits one //g per gradient step, each filling a band of the shape, so the
     * gradient actually runs across the build instead of being scattered by
     * percentage the way a weighted pattern is.
     */
    private void copyGradientScript() {
        List<String> lines = gradientScript();
        if (lines.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        copyToClipboard(sb.toString());
    }

    /**
     * Runs the gradient passes directly. Chat takes one line at a time, so a
     * multi-command script cannot be pasted - it has to be sent for you.
     */
    private void runGradientScript() {
        if (CommandQueue.isRunning()) {
            CommandQueue.cancel();
            return;
        }
        List<String> lines = gradientScript();
        if (!lines.isEmpty()) CommandQueue.run(lines);
    }

    private List<String> gradientScript() {
        List<String> ids = GradientScreen.gradientBlockIds();
        if (ids.isEmpty()) return new ArrayList<String>();
        return ShapeGradient.build(currentExpr(), ids, gradientAxis, gradientDither, hollow);
    }

    private void cyclePreset(int dir) {
        int n = ShapePreset.PRESETS.length;
        presetIndex = (presetIndex + dir + n) % n;
        params = preset().defaults();
        pattern = preset().defaultPattern;
        hollow = preset().defaultHollow;
        if (isCustom() && customExpr.trim()
            .isEmpty()) customExpr = "x^2+y^2+z^2 < 0.8^2";
        dirty = true;
        // Deferred: we are inside a button callback, so the list is being iterated.
        pendingRebuild = true;
    }

    private void refreshExprBox() {
        if (exprBox == null) return;
        exprBox.setText(displayExpr());
        lastExpr = exprBox.getText();
    }

    /** Handle a user edit of the expression box. */
    private void onExprTyped(String s) {
        String t = s.trim();
        if (t.startsWith("/")) {
            Cmd c = parseCommand(t);
            if (c != null) {
                presetIndex = ShapePreset.PRESETS.length - 1; // Custom
                hollow = c.hollow;
                pattern = c.pattern;
                customExpr = c.expr;
                if (patternBox != null) {
                    patternBox.setText(c.pattern);
                    lastPattern = c.pattern;
                }
                exprBox.setText(c.expr); // strip the command and pattern from the box
                lastExpr = exprBox.getText();
                pendingRebuild = true;
                dirty = true;
                return;
            }
        }
        presetIndex = ShapePreset.PRESETS.length - 1; // manual edit means Custom
        customExpr = s;
        dirty = true;
    }

    /** Split a pasted "//g [flags] &lt;pattern&gt; &lt;expr&gt;". Returns null if it is not one. */
    private static Cmd parseCommand(String t) {
        String body = t;
        while (body.startsWith("/")) body = body.substring(1);

        int sp = firstWs(body);
        if (sp < 0) return null;
        body = body.substring(sp)
            .trim(); // drop the command word

        boolean hol = false;
        while (body.startsWith("-")) {
            int s2 = firstWs(body);
            String flag = s2 < 0 ? body : body.substring(0, s2);
            if (flag.indexOf('h') >= 0) hol = true;
            if (s2 < 0) return null; // flags only, no pattern or expression
            body = body.substring(s2)
                .trim();
        }

        int s3 = firstWs(body);
        if (s3 < 0) return null;
        String expr = body.substring(s3)
            .trim();
        if (expr.isEmpty()) return null;

        Cmd c = new Cmd();
        c.hollow = hol;
        c.pattern = body.substring(0, s3);
        c.expr = expr;
        return c;
    }

    private static int firstWs(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private static final class Cmd {

        boolean hollow;
        String pattern;
        String expr;
    }

    // ---- rendering ----

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (dirty) {
            dirty = false;
            preview.sample(currentExpr(), pattern, res);
        }
        drawPanel();

        text(preset().name, left + 28, top + 31, Theme.TEXT_ACCENT);
        text("pattern", left + 8, top + 107, Theme.TEXT_DIM);
        text(
            CommandQueue.isRunning()
                ? "running " + (CommandQueue.total() - CommandQueue.remaining())
                    + " / "
                    + CommandQueue.total()
                    + "  (click Run gradient to cancel)"
                : "gradient across shape",
            left + 8,
            top + 192,
            CommandQueue.isRunning() ? Theme.TEXT_ACCENT : Theme.TEXT_DIM);
        text(isCustom() ? "expression (editable)" : "expression", left + 8, top + 214, Theme.TEXT_DIM);
        textClipped(currentCommand(), left + 8, top + 268, panelW - 16, Theme.TEXT_OK);

        Rect box = previewBox();
        preview.draw(this, box.x, box.y, box.w, box.h, spin);
        textClipped(
            preview.status(),
            box.x,
            box.y + box.h + 3,
            box.w + 6,
            preview.error() != null ? Theme.TEXT_ERROR : Theme.TEXT_FAINT);

        if (patternBox != null) patternBox.drawTextBox();
        if (exprBox != null) exprBox.drawTextBox();

    }

    private Rect previewBox() {
        return new Rect(left + panelW - 158, top + 28, PREVIEW, PREVIEW);
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (patternBox != null) patternBox.mouseClicked(mouseX, mouseY, button);
        if (exprBox != null) exprBox.mouseClicked(mouseX, mouseY, button);
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
        if (keyCode != 1) { // let Esc through to close the screen
            if (patternBox != null && patternBox.isFocused()) {
                patternBox.textboxKeyTyped(typedChar, keyCode);
                return;
            }
            if (exprBox != null && exprBox.isFocused()) {
                exprBox.textboxKeyTyped(typedChar, keyCode);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        // 1.7.10 text fields have no change listener, so poll for edits.
        if (patternBox != null) {
            patternBox.updateCursorCounter();
            String now = patternBox.getText();
            if (!now.equals(lastPattern)) {
                lastPattern = now;
                pattern = now;
                dirty = true;
            }
        }
        if (exprBox != null) {
            exprBox.updateCursorCounter();
            String now = exprBox.getText();
            if (!now.equals(lastExpr)) {
                lastExpr = now;
                onExprTyped(now);
            }
        }
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
    }
}
