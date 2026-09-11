package com.builderstoolkit.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.builderstoolkit.client.generate.ExprEngine;

/**
 * Client-side preview of a WorldEdit //generate expression: samples it over the
 * normalised [-1,1] cube the command maps a cubic selection onto, keeps the
 * surface voxels, and draws them with a small painter-ordered renderer.
 */
public final class ShapePreview {

    private static final int[] WOOL = { 0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C627, 0x70B919, 0xED8DAC, 0x3E4447,
        0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA02722, 0x141519 };

    /** {gx, gy, gz, rgb} per surface voxel. */
    private final List<int[]> surface = new ArrayList<int[]>();
    private int res;
    private String error;

    private double yaw = 0.7;
    private double pitch = -0.5;
    private boolean rotating;
    private int lastMx;
    private int lastMy;

    public String error() {
        return error;
    }

    public String status() {
        if (error != null) return "Error: " + error;
        return surface.size() + " surface blocks @ res " + res;
    }

    /** Re-evaluate the expression. O(resolution^3) evaluations. */
    public void sample(String expr, String pattern, int resolution) {
        surface.clear();
        error = null;
        res = Math.max(8, resolution);

        ExprEngine.Compiled prog;
        try {
            prog = ExprEngine.compile(expr);
        } catch (RuntimeException ex) {
            error = ex.getMessage();
            return;
        }

        int n = res;
        boolean[] occupied = new boolean[n * n * n];
        int[] color = new int[n * n * n];
        int base = baseColorFor(pattern);

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
                    if (!r.placed) continue;
                    int idx = (gx * n + gy) * n + gz;
                    occupied[idx] = true;
                    color[idx] = r.hasData ? WOOL[((int) Math.floor(r.data)) & 15] : base;
                }
            }
        }

        // Keep only voxels with an empty neighbour or on the border.
        for (int gx = 0; gx < n; gx++) {
            for (int gy = 0; gy < n; gy++) {
                for (int gz = 0; gz < n; gz++) {
                    int idx = (gx * n + gy) * n + gz;
                    if (!occupied[idx]) continue;
                    if (gx == 0 || gy == 0
                        || gz == 0
                        || gx == n - 1
                        || gy == n - 1
                        || gz == n - 1
                        || !occupied[((gx - 1) * n + gy) * n + gz]
                        || !occupied[((gx + 1) * n + gy) * n + gz]
                        || !occupied[(gx * n + (gy - 1)) * n + gz]
                        || !occupied[(gx * n + (gy + 1)) * n + gz]
                        || !occupied[(gx * n + gy) * n + (gz - 1)]
                        || !occupied[(gx * n + gy) * n + (gz + 1)]) {
                        surface.add(new int[] { gx, gy, gz, color[idx] });
                    }
                }
            }
        }
    }

    public void draw(ToolkitScreen screen, int bx, int by, int bw, int bh, boolean spin) {
        screen.rect(bx, by, bx + bw, by + bh, 0xFF06060A);
        if (spin && !rotating) yaw += 0.02;
        if (error != null || surface.isEmpty()) return;

        int n = res;
        double cx = bx + bw / 2.0;
        double cy = by + bh / 2.0;
        double scale = (Math.min(bw, bh) / 2.0) / 1.25;
        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);

        List<double[]> pts = new ArrayList<double[]>(surface.size()); // {sx, sy, depth, rgb}
        double minD = Double.MAX_VALUE;
        double maxD = -Double.MAX_VALUE;
        for (int[] s : surface) {
            double mx = -1.0 + 2.0 * s[0] / (n - 1);
            double my = -1.0 + 2.0 * s[1] / (n - 1);
            double mz = -1.0 + 2.0 * s[2] / (n - 1);
            double rx = mx * cosY + mz * sinY;
            double rz = -mx * sinY + mz * cosY;
            double ry2 = my * cosP - rz * sinP;
            double rz2 = my * sinP + rz * cosP;
            pts.add(new double[] { cx + rx * scale, cy - ry2 * scale, rz2, s[3] });
            if (rz2 < minD) minD = rz2;
            if (rz2 > maxD) maxD = rz2;
        }

        Collections.sort(pts, new Comparator<double[]>() {

            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[2], b[2]); // far first
            }
        });

        double side = Math.max(2.0, (2.0 * scale) / (n - 1) * 1.4);
        double range = Math.max(1e-6, maxD - minD);
        for (double[] q : pts) {
            double shade = 0.45 + 0.55 * ((q[2] - minD) / range);
            int rgb = (int) q[3];
            int r = shaded((rgb >> 16) & 0xFF, shade);
            int g = shaded((rgb >> 8) & 0xFF, shade);
            int b = shaded(rgb & 0xFF, shade);

            int x0 = Math.max(bx, (int) Math.round(q[0] - side / 2));
            int y0 = Math.max(by, (int) Math.round(q[1] - side / 2));
            int x1 = Math.min(bx + bw, (int) Math.round(q[0] + side / 2));
            int y1 = Math.min(by + bh, (int) Math.round(q[1] + side / 2));
            if (x1 > x0 && y1 > y0) {
                screen.rect(x0, y0, x1, y1, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    // ---- drag to rotate ----

    public boolean mousePressed(int mouseX, int mouseY, Rect box) {
        if (!box.contains(mouseX, mouseY)) return false;
        rotating = true;
        lastMx = mouseX;
        lastMy = mouseY;
        return true;
    }

    public void mouseDragged(int mouseX, int mouseY) {
        if (!rotating) return;
        yaw += (mouseX - lastMx) * 0.01;
        pitch = Math.max(-1.5, Math.min(1.5, pitch + (mouseY - lastMy) * 0.01));
        lastMx = mouseX;
        lastMy = mouseY;
    }

    public void mouseReleased() {
        rotating = false;
    }

    // ---- helpers ----

    private static int shaded(int channel, double shade) {
        int v = (int) (channel * shade);
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** Rough colour for the preview when the pattern names a familiar block. */
    static int baseColorFor(String pattern) {
        String s = pattern == null ? "" : pattern.toLowerCase(Locale.ROOT);
        if (s.contains("glass")) return 0x88AACC;
        if (s.contains("oak") || s.contains("log") || s.contains("wood") || s.contains("plank")) return 0x9A7B4F;
        if (s.contains("clay")) return 0x975D43;
        if (s.contains("wool")) return 0xE9ECEC;
        if (s.contains("red")) return 0xB02622;
        if (s.contains("white")) return 0xE9ECEC;
        if (s.contains("leaves") || s.contains("grass") || s.contains("green")) return 0x5E8A3A;
        if (s.contains("sand")) return 0xDBCE9A;
        if (s.contains("water")) return 0x3A5FD9;
        if (s.contains("dirt") || s.contains("brown")) return 0x7A5A3A;
        if (s.contains("stone")) return 0x9A9A9A;
        return 0xA8A8B0;
    }
}
