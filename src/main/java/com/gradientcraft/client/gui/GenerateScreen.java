package com.gradientcraft.client.gui;

import com.gradientcraft.client.generate.ExprEngine;
import com.gradientcraft.client.generate.Rotate3D;
import com.gradientcraft.client.generate.ShapePreset;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * "Generate" tab: builds a WorldEdit //generate command from menus and shows a
 * live, rotating, fully client-side preview of the resulting shape. The preview
 * samples the expression over a normalized [-1,1] grid (exactly how //g maps a
 * cubic selection) and draws the surface voxels with a small software renderer.
 *
 * Actually running the command requires WorldEdit on the server; the Run button
 * sends the command via chat. The preview itself needs nothing but this mod.
 */
public class GenerateScreen extends Screen {

    private static final int PANEL_W = 330;
    private static final int PANEL_H = 264;

    // Persistent state across reopen / tab switches.
    private static int presetIndex = 1; // Torus
    private static double[] params = ShapePreset.PRESETS[1].defaults();
    private static String pattern = ShapePreset.PRESETS[1].defaultPattern;
    private static boolean hollow = ShapePreset.PRESETS[1].defaultHollow;
    private static int res = 24;
    private static boolean spin = true;
    private static String customExpr = "x^2+y^2+z^2 < 0.8^2";
    private static double yaw = 0.7, pitch = -0.5;
    private static double rotX = 0, rotY = 0, rotZ = 0; // X/Y/Z shape rotation (degrees)

    private int left, top;
    private EditBox patternBox, exprBox;
    private boolean suppressExpr = false;
    private boolean pendingRebuild = false;

    // preview sample data
    private boolean dirty = true;
    private int sres = 0;
    private final List<int[]> surface = new ArrayList<>(); // {gx,gy,gz,rgb}
    private String error = null;

    // drag-to-rotate
    private boolean rotating = false;
    private double lastMx, lastMy;

    private static final int[] WOOL = {
        0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C627, 0x70B919, 0xED8DAC, 0x3E4447,
        0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA02722, 0x141519
    };

    public GenerateScreen() { super(new TextComponent("Shape Generator")); }

    private ShapePreset preset() { return ShapePreset.PRESETS[presetIndex]; }
    private boolean isCustom() { return ShapePreset.isCustom(presetIndex); }

    /** The shape expression as shown/edited (not rotated). */
    private String displayExpr() {
        return isCustom() ? customExpr : preset().build(params);
    }

    /** The effective expression used for the command and preview (rotated). */
    private String currentExpr() {
        return Rotate3D.wrap(displayExpr(), rotX, rotY, rotZ);
    }

    private String currentCommand() {
        return "//g " + (hollow ? "-h " : "") + pattern + " " + currentExpr();
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        // tabs (above the panel)
        Button tabG = new Button(left + 4, top - 22, 70, 20, new TextComponent("Gradient"),
                b -> Minecraft.getInstance().setScreen(new GradientScreen()));
        addRenderableWidget(tabG);
        Button tabGen = new Button(left + 78, top - 22, 70, 20, new TextComponent("Generate"), b -> {});
        tabGen.active = false;
        addRenderableWidget(tabGen);
        addRenderableWidget(new Button(left + 152, top - 22, 70, 20, new TextComponent("Colors"),
                b -> Minecraft.getInstance().setScreen(new ColorScreen())));
        addRenderableWidget(new Button(left + 226, top - 22, 70, 20, new TextComponent("Palette"),
                b -> Minecraft.getInstance().setScreen(new PaletteScreen())));
        addRenderableWidget(new Button(left + 300, top - 22, 58, 20, new TextComponent("Lab"),
                b -> Minecraft.getInstance().setScreen(new LabScreen())));

        // preset arrows
        addRenderableWidget(new Button(left + 8, top + 26, 16, 18, new TextComponent("<"),
                b -> cyclePreset(-1)));
        addRenderableWidget(new Button(left + 140, top + 26, 16, 18, new TextComponent(">"),
                b -> cyclePreset(1)));

        // parameter sliders for the current preset
        int py = top + 48;
        ShapePreset.Param[] ps = preset().params;
        for (int i = 0; i < ps.length; i++) {
            final int idx = i;
            ShapePreset.Param par = ps[i];
            addRenderableWidget(new ValueSlider(left + 8, py, 148, 18, par.label,
                    par.min, par.max, par.integer, params[idx], v -> {
                        params[idx] = v;
                        refreshExpr();
                        dirty = true;
                    }));
            py += 20;
        }

        // pattern box
        patternBox = new EditBox(this.font, left + 8, top + 116, 148, 16, new TextComponent("pattern"));
        patternBox.setMaxLength(128);
        patternBox.setValue(pattern);
        patternBox.setResponder(s -> { pattern = s; dirty = true; });
        addRenderableWidget(patternBox);

        // hollow + spin toggles, resolution slider
        addRenderableWidget(new Button(left + 8, top + 136, 70, 18, new TextComponent(hollowLabel()),
                b -> { hollow = !hollow; b.setMessage(new TextComponent(hollowLabel())); }));
        addRenderableWidget(new Button(left + 86, top + 136, 70, 18, new TextComponent(spinLabel()),
                b -> { spin = !spin; b.setMessage(new TextComponent(spinLabel())); }));
        addRenderableWidget(new ValueSlider(left + 8, top + 156, 148, 18, "preview res",
                8, 32, true, res, v -> { res = (int) v; dirty = true; }));

        // X/Y/Z rotation - applies to ANY shape (preset, natural, or custom)
        addRenderableWidget(new ValueSlider(left + 8, top + 178, 46, 14, "rX",
                0, 360, true, rotX, v -> { rotX = v; dirty = true; }));
        addRenderableWidget(new ValueSlider(left + 56, top + 178, 46, 14, "rY",
                0, 360, true, rotY, v -> { rotY = v; dirty = true; }));
        addRenderableWidget(new ValueSlider(left + 104, top + 178, 46, 14, "rZ",
                0, 360, true, rotZ, v -> { rotZ = v; dirty = true; }));

        // expression box (editable; editing switches to Custom)
        exprBox = new EditBox(this.font, left + 8, top + 206, PANEL_W - 16, 16, new TextComponent("expr"));
        exprBox.setMaxLength(512);
        suppressExpr = true; exprBox.setValue(displayExpr()); suppressExpr = false;
        exprBox.setEditable(true); // editing a preset's expression switches to Custom
        exprBox.setResponder(s -> {
            if (suppressExpr) return;
            String t = s.trim();
            if (t.startsWith("/")) {                 // a full //generate command was pasted
                Cmd c = parseCommand(t);
                if (c != null) {
                    presetIndex = ShapePreset.PRESETS.length - 1; // Custom
                    hollow = c.hollow;
                    pattern = c.pattern;
                    customExpr = c.expr;
                    suppressExpr = true;
                    if (patternBox != null) patternBox.setValue(c.pattern);
                    exprBox.setValue(c.expr);          // strip command+pattern from the box
                    suppressExpr = false;
                    pendingRebuild = true;             // refresh labels/widgets next tick
                    dirty = true;
                    return;
                }
            }
            presetIndex = ShapePreset.PRESETS.length - 1; // manual edit => Custom
            customExpr = s;
            dirty = true;
        });
        addRenderableWidget(exprBox);

        // copy / run
        addRenderableWidget(new Button(left + 8, top + 228, 70, 18, new TextComponent("Copy cmd"),
                b -> Minecraft.getInstance().keyboardHandler.setClipboard(currentCommand())));
        addRenderableWidget(new Button(left + 86, top + 228, 70, 18, new TextComponent("Run in chat"),
                b -> runCommand()));

        dirty = true;
    }

    private void cyclePreset(int dir) {
        int n = ShapePreset.PRESETS.length;
        presetIndex = (presetIndex + dir + n) % n;
        params = preset().defaults();
        pattern = preset().defaultPattern;
        hollow = preset().defaultHollow;
        if (isCustom() && (customExpr == null || customExpr.isBlank())) {
            customExpr = "x^2+y^2+z^2 < 0.8^2";
        }
        dirty = true;
        this.clearWidgets();
        this.init(); // rebuild widgets for the new preset
    }

    private void refreshExpr() {
        if (exprBox != null) { suppressExpr = true; exprBox.setValue(displayExpr()); suppressExpr = false; }
    }

    private void runCommand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.chat(currentCommand());
    }

    /** Split a pasted "//g [flags] <pattern> <expr>" into its parts. Returns null if not a command. */
    private static Cmd parseCommand(String t) {
        String body = t;
        while (body.startsWith("/")) body = body.substring(1); // strip leading slashes
        int sp = firstWs(body);
        if (sp < 0) return null;
        body = body.substring(sp).trim();                       // drop the command word (g/generate)
        boolean hol = false;
        while (body.startsWith("-")) {                          // consume flags
            int s2 = firstWs(body);
            String flag = s2 < 0 ? body : body.substring(0, s2);
            if (flag.indexOf('h') >= 0) hol = true;
            if (s2 < 0) { body = ""; break; }
            body = body.substring(s2).trim();
        }
        int s3 = firstWs(body);
        if (s3 < 0) return null;
        String pat = body.substring(0, s3);                    // pattern is the next token
        String expr = body.substring(s3).trim();               // the rest is the expression
        if (expr.isEmpty()) return null;
        Cmd c = new Cmd();
        c.hollow = hol; c.pattern = pat; c.expr = expr;
        return c;
    }

    private static int firstWs(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    private static final class Cmd { boolean hollow; String pattern; String expr; }

    private static String hollowLabel() { return "Hollow: " + (hollow ? "ON" : "OFF"); }
    private static String spinLabel()   { return "Spin: " + (spin ? "ON" : "OFF"); }

    // ---- sampling ----

    private void resample() {
        dirty = false;
        surface.clear();
        error = null;
        sres = Math.max(8, res);
        ExprEngine.Compiled prog;
        try {
            prog = ExprEngine.compile(currentExpr());
        } catch (RuntimeException ex) {
            error = ex.getMessage();
            return;
        }
        int n = sres;
        boolean[] occ = new boolean[n * n * n];
        int[] col = new int[n * n * n];
        int baseColor = baseColorFor(pattern);
        for (int gx = 0; gx < n; gx++) {
            double x = -1.0 + 2.0 * gx / (n - 1);
            for (int gy = 0; gy < n; gy++) {
                double y = -1.0 + 2.0 * gy / (n - 1);
                for (int gz = 0; gz < n; gz++) {
                    double z = -1.0 + 2.0 * gz / (n - 1);
                    ExprEngine.Result r;
                    try {
                        r = prog.eval(x, y, z);
                    } catch (RuntimeException ex) {
                        continue;
                    }
                    if (r.placed) {
                        int idx = (gx * n + gy) * n + gz;
                        occ[idx] = true;
                        col[idx] = r.hasData ? WOOL[((int) Math.floor(r.data)) & 15] : baseColor;
                    }
                }
            }
        }
        // keep only surface voxels (any empty neighbor or on border)
        for (int gx = 0; gx < n; gx++)
            for (int gy = 0; gy < n; gy++)
                for (int gz = 0; gz < n; gz++) {
                    int idx = (gx * n + gy) * n + gz;
                    if (!occ[idx]) continue;
                    if (gx == 0 || gy == 0 || gz == 0 || gx == n - 1 || gy == n - 1 || gz == n - 1
                            || !occ[((gx - 1) * n + gy) * n + gz] || !occ[((gx + 1) * n + gy) * n + gz]
                            || !occ[(gx * n + (gy - 1)) * n + gz] || !occ[(gx * n + (gy + 1)) * n + gz]
                            || !occ[(gx * n + gy) * n + (gz - 1)] || !occ[(gx * n + gy) * n + (gz + 1)]) {
                        surface.add(new int[] { gx, gy, gz, col[idx] });
                    }
                }
    }

    private static int baseColorFor(String p) {
        String s = p == null ? "" : p.toLowerCase(Locale.ROOT);
        if (s.contains("glass")) return 0x88AACC;
        if (s.contains("oak") || s.contains("log") || s.contains("wood") || s.contains("plank")) return 0x9A7B4F;
        if (s.contains("red")) return 0xB02622;
        if (s.contains("white")) return 0xE9ECEC;
        if (s.contains("grass") || s.contains("green")) return 0x5E8A3A;
        if (s.contains("sand")) return 0xDBCE9A;
        if (s.contains("dirt") || s.contains("brown")) return 0x7A5A3A;
        if (s.contains("stone")) return 0x9A9A9A;
        return 0xA8A8B0;
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        if (dirty) resample();
        this.renderBackground(pose);

        // panel + title
        fill(pose, left, top, left + PANEL_W, top + PANEL_H, 0xF0101018);
        fill(pose, left, top, left + PANEL_W, top + 22, 0xFF2B2B3A);
        this.font.draw(pose, this.title, left + 8, top + 7, 0xFFFFFF);

        // preset name
        this.font.draw(pose, new TextComponent(preset().name), left + 28, top + 31, 0xFFE08A);

        // labels
        this.font.draw(pose, new TextComponent("pattern"), left + 8, top + 107, 0xC8C8D8);
        this.font.draw(pose, new TextComponent(isCustom() ? "expression (editable)" : "expression"),
                left + 8, top + 197, 0xC8C8D8);

        // command preview (truncated)
        String cmd = currentCommand();
        String shown = this.font.plainSubstrByWidth(cmd, PANEL_W - 16);
        this.font.draw(pose, new TextComponent(shown), left + 8, top + 250, 0x90C090);

        // preview box
        int pvX = left + PANEL_W - 158, pvY = top + 28, pvW = 150, pvH = 150;
        fill(pose, pvX, pvY, pvX + pvW, pvY + pvH, 0xFF06060A);
        fill(pose, pvX, pvY, pvX + pvW, pvY + 1, 0xFF333344);
        renderPreview(pose, pvX, pvY, pvW, pvH);

        // status under preview
        String status = error != null ? ("Error: " + error)
                : (surface.size() + " surface blocks @ res " + sres);
        this.font.draw(pose, new TextComponent(this.font.plainSubstrByWidth(status, pvW + 6)),
                pvX, pvY + pvH + 3, error != null ? 0xFF7070 : 0xA0A0B0);

        super.render(pose, mouseX, mouseY, partial); // widgets
    }

    private void renderPreview(PoseStack pose, int bx, int by, int bw, int bh) {
        if (spin && !rotating) yaw += 0.02;
        if (error != null || surface.isEmpty()) return;

        int n = sres;
        double cx = bx + bw / 2.0, cy = by + bh / 2.0;
        double scale = (Math.min(bw, bh) / 2.0) / 1.25;
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);

        // project
        List<double[]> pts = new ArrayList<>(surface.size()); // {sx, sy, depth, rgb}
        double minD = Double.MAX_VALUE, maxD = -Double.MAX_VALUE;
        for (int[] s : surface) {
            double mx = -1.0 + 2.0 * s[0] / (n - 1);
            double my = -1.0 + 2.0 * s[1] / (n - 1);
            double mz = -1.0 + 2.0 * s[2] / (n - 1);
            double rx = mx * cosY + mz * sinY;
            double rz = -mx * sinY + mz * cosY;
            double ry2 = my * cosP - rz * sinP;
            double rz2 = my * sinP + rz * cosP;
            double sx = cx + rx * scale;
            double sy = cy - ry2 * scale;
            pts.add(new double[] { sx, sy, rz2, s[3] });
            if (rz2 < minD) minD = rz2;
            if (rz2 > maxD) maxD = rz2;
        }
        pts.sort((a, b) -> Double.compare(a[2], b[2])); // far first

        double side = Math.max(2.0, (2.0 * scale) / (n - 1) * 1.4);
        double range = Math.max(1e-6, maxD - minD);
        for (double[] q : pts) {
            double norm = (q[2] - minD) / range;      // 0 far .. 1 near
            double shade = 0.45 + 0.55 * norm;
            int rgb = (int) q[3];
            int r = clampShade(((rgb >> 16) & 0xFF) * shade);
            int g = clampShade(((rgb >> 8) & 0xFF) * shade);
            int b = clampShade((rgb & 0xFF) * shade);
            int x0 = (int) Math.round(q[0] - side / 2);
            int y0 = (int) Math.round(q[1] - side / 2);
            int x1 = (int) Math.round(q[0] + side / 2);
            int y1 = (int) Math.round(q[1] + side / 2);
            // clip to box
            x0 = Math.max(bx, x0); y0 = Math.max(by, y0);
            x1 = Math.min(bx + bw, x1); y1 = Math.min(by + bh, y1);
            if (x1 > x0 && y1 > y0) fill(pose, x0, y0, x1, y1, 0xFF000000 | (r << 16) | (g << 8) | b);
        }
    }

    private static int clampShade(double v) { return Math.max(0, Math.min(255, (int) v)); }

    // ---- inner slider ----

    private class ValueSlider extends AbstractSliderButton {
        final double min, max; final boolean integer; final String label; final DoubleConsumer onSet;
        ValueSlider(int x, int y, int w, int h, String label, double min, double max,
                    boolean integer, double initial, DoubleConsumer onSet) {
            super(x, y, w, h, new TextComponent(""), clamp01((initial - min) / (max - min)));
            this.min = min; this.max = max; this.integer = integer; this.label = label; this.onSet = onSet;
            updateMessage();
        }
        private double real() { double r = min + (max - min) * this.value; return integer ? Math.round(r) : r; }
        @Override protected void updateMessage() {
            String v = integer ? String.valueOf((long) real())
                               : String.format(Locale.ROOT, "%.2f", real());
            setMessage(new TextComponent(label + ": " + v));
        }
        @Override protected void applyValue() { onSet.accept(real()); }
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int pvX = left + PANEL_W - 158, pvY = top + 28, pvW = 150, pvH = 150;
        if (mx >= pvX && mx < pvX + pvW && my >= pvY && my < pvY + pvH) {
            rotating = true; lastMx = mx; lastMy = my; return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (rotating) {
            yaw += (mx - lastMx) * 0.01;
            pitch += (my - lastMy) * 0.01;
            pitch = Math.max(-1.5, Math.min(1.5, pitch));
            lastMx = mx; lastMy = my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        rotating = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void tick() {
        if (pendingRebuild) { pendingRebuild = false; this.clearWidgets(); this.init(); }
        if (patternBox != null) patternBox.tick();
        if (exprBox != null) exprBox.tick();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
