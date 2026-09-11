package com.gradientcraft.client.gui;

import com.gradientcraft.client.generate.ExprEngine;
import com.gradientcraft.client.generate.LabShape;
import com.gradientcraft.client.generate.Rotate3D;
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
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * "Lab" tab: a parametric shape sandbox. A grid of sliders and checkboxes drives
 * {@link LabShape}, which builds a WorldEdit expression that is previewed live.
 * Tune freely, hit Random to roll everything, Copy/Run the resulting command.
 * Fully client-side.
 */
public class LabScreen extends Screen {

    private static final int PANEL_W = 362;
    private static final int PANEL_H = 276;

    // shape slider descriptors: label, min, max, integer
    private static final String[] SL_LABEL = {
        "size", "square", "stretchX", "stretchY", "stretchZ", "taper", "twistAmt",
        "rippleF", "rippleA", "wobbleF", "wobbleA", "thick" };
    private static final double[] SL_MIN = { 0.3, 2, 0.5, 0.5, 0.5, 0, 0, 1, 0, 1, 0, 0.03 };
    private static final double[] SL_MAX = { 1.0, 8, 2.0, 2.0, 2.0, 0.6, 4, 12, 0.3, 10, 0.3, 0.3 };
    private static final boolean[] SL_INT = { false, false, false, false, false, false, false, true, false, true, false, false };
    private static final double[] LV = { 0.8, 2, 1, 1, 1, 0, 1.5, 5, 0.15, 5, 0.15, 0.1 };

    // X/Y/Z rotation (degrees), applied via Rotate3D
    private static final String[] ROT_LABEL = { "rotX", "rotY", "rotZ" };
    private static final double[] ROT = { 0, 0, 0 };

    private static final String[] CK_LABEL = { "twist", "ripple", "wobble", "hollow", "invert", "rainbow" };
    private static final boolean[] CV = new boolean[6];

    private static String pattern = "stone";
    private static int res = 22;
    private static boolean spin = true;
    private static double yaw = 0.7, pitch = -0.5;

    private static final int[] WOOL = {
        0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C627, 0x70B919, 0xED8DAC, 0x3E4447,
        0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA02722, 0x141519 };

    private int left, top;
    private EditBox patternBox;
    private boolean dirty = true;
    private int sres = 0;
    private final List<int[]> surface = new ArrayList<>();
    private String error = null;
    private boolean rotating = false;
    private double lastMx, lastMy;

    public LabScreen() { super(new TextComponent("Shape Lab")); }

    private String currentExpr() { return Rotate3D.wrap(LabShape.build(LV, CV), ROT[0], ROT[1], ROT[2]); }
    private String currentCommand() { return "//g " + pattern + " " + currentExpr(); }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        // tabs
        addRenderableWidget(new Button(left + 4, top - 22, 70, 20, new TextComponent("Gradient"),
                b -> Minecraft.getInstance().setScreen(new GradientScreen())));
        addRenderableWidget(new Button(left + 78, top - 22, 70, 20, new TextComponent("Generate"),
                b -> Minecraft.getInstance().setScreen(new GenerateScreen())));
        addRenderableWidget(new Button(left + 152, top - 22, 70, 20, new TextComponent("Colors"),
                b -> Minecraft.getInstance().setScreen(new ColorScreen())));
        addRenderableWidget(new Button(left + 226, top - 22, 70, 20, new TextComponent("Palette"),
                b -> Minecraft.getInstance().setScreen(new PaletteScreen())));
        Button tabLab = new Button(left + 300, top - 22, 58, 20, new TextComponent("Lab"), b -> {});
        tabLab.active = false;
        addRenderableWidget(tabLab);

        // control grid (2 columns): 12 shape sliders, 3 rotation sliders, 6 checkboxes
        int rowsPerCol = 11;
        int k = 0;
        for (int i = 0; i < SL_LABEL.length; i++)
            addSlider(k++, rowsPerCol, SL_LABEL[i], SL_MIN[i], SL_MAX[i], SL_INT[i], LV, i);
        for (int i = 0; i < ROT_LABEL.length; i++)
            addSlider(k++, rowsPerCol, ROT_LABEL[i], 0, 360, true, ROT, i);
        for (int j = 0; j < CK_LABEL.length; j++)
            addCheckbox(k++, rowsPerCol, j);

        // pattern + actions
        patternBox = new EditBox(this.font, left + 8, top + 212, 120, 16, new TextComponent("pattern"));
        patternBox.setMaxLength(128);
        patternBox.setValue(pattern);
        patternBox.setResponder(s -> { pattern = s; dirty = true; });
        addRenderableWidget(patternBox);

        addRenderableWidget(new Button(left + 8, top + 232, 70, 18, new TextComponent("Random"),
                b -> { randomize(); rebuild(); }));
        addRenderableWidget(new Button(left + 82, top + 232, 60, 18, new TextComponent("Reset"),
                b -> { reset(); rebuild(); }));
        addRenderableWidget(new Button(left + 146, top + 232, 84, 18, new TextComponent("Copy cmd"),
                b -> Minecraft.getInstance().keyboardHandler.setClipboard(currentCommand())));
        addRenderableWidget(new Button(left + 234, top + 232, 70, 18, new TextComponent("Run"),
                b -> { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.chat(currentCommand()); }));

        // small preview-res control under the preview
        addRenderableWidget(new ValueSlider(left + 206, top + 186, 100, 14, "res",
                8, 32, true, res, v -> { res = (int) v; dirty = true; }));

        dirty = true;
    }

    private void addSlider(int flat, int rowsPerCol, String label, double min, double max,
                           boolean integer, double[] target, int idx) {
        int col = flat / rowsPerCol, row = flat % rowsPerCol;
        int x = left + 8 + col * 94, y = top + 30 + row * 16;
        addRenderableWidget(new ValueSlider(x, y, 90, 14, label, min, max, integer, target[idx],
                v -> { target[idx] = v; dirty = true; }));
    }

    private void addCheckbox(int flat, int rowsPerCol, int idx) {
        int col = flat / rowsPerCol, row = flat % rowsPerCol;
        int x = left + 8 + col * 94, y = top + 30 + row * 16;
        addRenderableWidget(new Button(x, y, 90, 14, new TextComponent(ckLabel(idx)),
                b -> { CV[idx] = !CV[idx]; b.setMessage(new TextComponent(ckLabel(idx))); dirty = true; }));
    }

    private static String ckLabel(int i) { return CK_LABEL[i] + ": " + (CV[i] ? "ON" : "OFF"); }

    private void rebuild() { this.clearWidgets(); this.init(); }

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
        double[] def = { 0.8, 2, 1, 1, 1, 0, 1.5, 5, 0.15, 5, 0.15, 0.1 };
        System.arraycopy(def, 0, LV, 0, def.length);
        for (int i = 0; i < ROT.length; i++) ROT[i] = 0;
        for (int i = 0; i < CV.length; i++) CV[i] = false;
        dirty = true;
    }

    // ---- preview sampling (same approach as the Generate tab) ----

    private void resample() {
        dirty = false;
        surface.clear();
        error = null;
        sres = Math.max(8, res);
        ExprEngine.Compiled prog;
        try { prog = ExprEngine.compile(currentExpr()); }
        catch (RuntimeException ex) { error = ex.getMessage(); return; }

        int n = sres;
        boolean[] occ = new boolean[n * n * n];
        int[] col = new int[n * n * n];
        int base = baseColorFor(pattern);
        for (int gx = 0; gx < n; gx++) {
            double x = -1.0 + 2.0 * gx / (n - 1);
            for (int gy = 0; gy < n; gy++) {
                double y = -1.0 + 2.0 * gy / (n - 1);
                for (int gz = 0; gz < n; gz++) {
                    double z = -1.0 + 2.0 * gz / (n - 1);
                    ExprEngine.Result r;
                    try { r = prog.eval(x, y, z); } catch (RuntimeException ex) { continue; }
                    if (r.placed) {
                        int idx = (gx * n + gy) * n + gz;
                        occ[idx] = true;
                        col[idx] = r.hasData ? WOOL[((int) Math.floor(r.data)) & 15] : base;
                    }
                }
            }
        }
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
        if (s.contains("leaves") || s.contains("grass") || s.contains("green")) return 0x5E8A3A;
        if (s.contains("sand")) return 0xDBCE9A;
        if (s.contains("water")) return 0x3A5FD9;
        if (s.contains("stone")) return 0x9A9A9A;
        return 0xA8A8B0;
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        if (dirty) resample();
        this.renderBackground(pose);
        fill(pose, left, top, left + PANEL_W, top + PANEL_H, 0xF0101018);
        fill(pose, left, top, left + PANEL_W, top + 22, 0xFF2B2B3A);
        this.font.draw(pose, this.title, left + 8, top + 7, 0xFFFFFF);

        int pvX = left + 206, pvY = top + 28, pvW = 150, pvH = 150;
        fill(pose, pvX, pvY, pvX + pvW, pvY + pvH, 0xFF06060A);
        renderPreview(pose, pvX, pvY, pvW, pvH);

        String status = error != null ? ("Error: " + error)
                : (surface.size() + " surface blocks @ res " + sres);
        this.font.draw(pose, new TextComponent(this.font.plainSubstrByWidth(status, pvW + 4)),
                pvX, pvY + pvH + 18, error != null ? 0xFF7070 : 0xA0A0B0);

        // command preview
        this.font.draw(pose, new TextComponent(this.font.plainSubstrByWidth(currentCommand(), PANEL_W - 16)),
                left + 8, top + 256, 0x90C090);

        super.render(pose, mouseX, mouseY, partial);

        if (error == null && !surface.isEmpty()) { /* preview already drawn */ }
    }

    private void renderPreview(PoseStack pose, int bx, int by, int bw, int bh) {
        if (spin && !rotating) yaw += 0.02;
        if (error != null || surface.isEmpty()) return;
        int n = sres;
        double cx = bx + bw / 2.0, cy = by + bh / 2.0;
        double scale = (Math.min(bw, bh) / 2.0) / 1.25;
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw), cosP = Math.cos(pitch), sinP = Math.sin(pitch);
        List<double[]> pts = new ArrayList<>(surface.size());
        double minD = Double.MAX_VALUE, maxD = -Double.MAX_VALUE;
        for (int[] s : surface) {
            double mx = -1.0 + 2.0 * s[0] / (n - 1), my = -1.0 + 2.0 * s[1] / (n - 1), mz = -1.0 + 2.0 * s[2] / (n - 1);
            double rx = mx * cosY + mz * sinY, rz = -mx * sinY + mz * cosY;
            double ry2 = my * cosP - rz * sinP, rz2 = my * sinP + rz * cosP;
            pts.add(new double[] { cx + rx * scale, cy - ry2 * scale, rz2, s[3] });
            if (rz2 < minD) minD = rz2;
            if (rz2 > maxD) maxD = rz2;
        }
        pts.sort((a, b) -> Double.compare(a[2], b[2]));
        double side = Math.max(2.0, (2.0 * scale) / (n - 1) * 1.4);
        double range = Math.max(1e-6, maxD - minD);
        for (double[] q : pts) {
            double shade = 0.45 + 0.55 * ((q[2] - minD) / range);
            int rgb = (int) q[3];
            int r = cl(((rgb >> 16) & 0xFF) * shade), g = cl(((rgb >> 8) & 0xFF) * shade), b = cl((rgb & 0xFF) * shade);
            int x0 = Math.max(bx, (int) Math.round(q[0] - side / 2)), y0 = Math.max(by, (int) Math.round(q[1] - side / 2));
            int x1 = Math.min(bx + bw, (int) Math.round(q[0] + side / 2)), y1 = Math.min(by + bh, (int) Math.round(q[1] + side / 2));
            if (x1 > x0 && y1 > y0) fill(pose, x0, y0, x1, y1, 0xFF000000 | (r << 16) | (g << 8) | b);
        }
    }

    private static int cl(double v) { return Math.max(0, Math.min(255, (int) v)); }

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
            String v = integer ? String.valueOf((long) real()) : String.format(Locale.ROOT, "%.2f", real());
            setMessage(new TextComponent(label + ": " + v));
        }
        @Override protected void applyValue() { onSet.accept(real()); }
    }
    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int pvX = left + 206, pvY = top + 28, pvW = 150, pvH = 150;
        if (mx >= pvX && mx < pvX + pvW && my >= pvY && my < pvY + pvH) {
            rotating = true; lastMx = mx; lastMy = my; return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (rotating) {
            yaw += (mx - lastMx) * 0.01;
            pitch = Math.max(-1.5, Math.min(1.5, pitch + (my - lastMy) * 0.01));
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
    public void tick() { if (patternBox != null) patternBox.tick(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
