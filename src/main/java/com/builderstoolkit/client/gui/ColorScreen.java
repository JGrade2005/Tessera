package com.builderstoolkit.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiTextField;

import com.builderstoolkit.client.BlockColorIndex;
import com.builderstoolkit.client.BlockColorIndex.Entry;
import com.builderstoolkit.client.Lab;
import com.builderstoolkit.client.PaletteFilter;
import com.builderstoolkit.client.gui.widget.SliderButton;

/**
 * Colours tab: pick a colour from an HSV wheel, brightness slider or hex code,
 * and see the closest blocks ranked by perceptual distance in CIELAB.
 */
public class ColorScreen extends ToolkitScreen {

    private static final int RESULTS = 28;
    private static final int COLS = 7;
    private static final int WHEEL_R = 60;

    private static float hue = 0f;
    private static float sat = 0f;
    private static float val = 1f;

    private int wheelCx;
    private int wheelCy;
    private boolean draggingWheel;

    private GuiTextField hexBox;
    private String lastHex = "";
    private boolean pendingRebuild;
    /** Set while painting, consumed by drawOverlay. */
    private Entry hoveredEntry;

    private boolean dirty = true;
    private int targetRgb = 0xFFFFFF;
    private final List<Entry> results = new ArrayList<Entry>();

    public ColorScreen() {
        super("Colour Picker", Tab.COLORS, 300, 266);
    }

    @Override
    protected void build() {
        BlockColorIndex.ensureStarted();

        wheelCx = left + 16 + WHEEL_R;
        wheelCy = top + 36 + WHEEL_R;

        buttonList.add(new SliderButton(left + 16, top + 162, 2 * WHEEL_R, 16, "brightness", 0, 1, false, val, v -> {
            val = (float) v;
            dirty = true;
            syncHexBox();
        }));

        addFilterToggles(left + 8, top + 236, panelW - 16, 4, 16, 2);

        hexBox = new GuiTextField(this.fontRendererObj, left + 16, top + 184, 2 * WHEEL_R, 16);
        hexBox.setMaxStringLength(7);
        hexBox.setText(currentHex());
        lastHex = hexBox.getText();

        dirty = true;
    }

    private static String currentHex() {
        return String.format(Locale.ROOT, "#%06X", hsvToRgb(hue, sat, val) & 0xFFFFFF);
    }

    private void syncHexBox() {
        if (hexBox == null) return;
        hexBox.setText(currentHex());
        lastHex = hexBox.getText();
    }

    /** Parse a typed hex colour; rebuilds widgets so the brightness slider follows. */
    private void onHexTyped(String s) {
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return;
        int rgb;
        try {
            rgb = Integer.parseInt(t, 16);
        } catch (NumberFormatException ignored) {
            return;
        }
        float[] hsv = rgbToHsv((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        dirty = true;
        pendingRebuild = true;
    }

    private void recompute() {
        dirty = false;
        targetRgb = hsvToRgb(hue, sat, val);
        results.clear();
        if (!BlockColorIndex.isReady()) return;

        final float[] target = Lab.rgbToLab((targetRgb >> 16) & 0xFF, (targetRgb >> 8) & 0xFF, targetRgb & 0xFF);

        List<Entry> all = new ArrayList<Entry>();
        for (Entry e : BlockColorIndex.snapshot()) {
            if (PaletteFilter.accepts(e)) all.add(e);
        }
        Collections.sort(all, new Comparator<Entry>() {

            @Override
            public int compare(Entry a, Entry b) {
                return Double.compare(Lab.deltaE(target, a.lab), Lab.deltaE(target, b.lab));
            }
        });
        for (int i = 0; i < Math.min(RESULTS, all.size()); i++) {
            results.add(all.get(i));
        }
    }

    // ---- rendering ----

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (dirty) recompute();
        drawPanel();

        drawWheel();

        rect(left + 16, top + 204, left + 40, top + 222, 0xFF000000 | targetRgb);
        text(currentHex(), left + 46, top + 209, Theme.TEXT_DIM);

        text("Closest blocks", left + 150, top + 26, Theme.TEXT_DIM);
        hoveredEntry = drawResults(mouseX, mouseY);

        textClipped(statusText(), left + 150, top + 40 + 4 * 18 + 6, panelW - 158, Theme.TEXT_FAINT);
        text("click a block to copy its id", left + 150, top + 212, 0x707080);
        text("Filters", left + 8, top + 226, Theme.TEXT_DIM);

        if (hexBox != null) hexBox.drawTextBox();

    }

    @Override
    protected void drawOverlay(int mouseX, int mouseY) {
        if (hoveredEntry != null) {
            drawTooltip(Arrays.asList(hoveredEntry.displayName(), hoveredEntry.id()), mouseX, mouseY);
        }
    }

    private Entry drawResults(int mouseX, int mouseY) {
        Entry hovered = null;
        for (int i = 0; i < results.size(); i++) {
            int x = left + 150 + (i % COLS) * 18;
            int y = top + 40 + (i / COLS) * 18;
            if (i == 0) rect(x - 1, y - 1, x + 17, y + 17, Theme.LOCKED); // best match
            drawStack(results.get(i).stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = results.get(i);
        }
        return hovered;
    }

    private String statusText() {
        switch (BlockColorIndex.state()) {
            case READY:
                return results.isEmpty() ? "No blocks indexed"
                    : results.get(0)
                        .displayName();
            case INDEXING:
                return "Indexing " + BlockColorIndex.progress() + "/" + BlockColorIndex.total();
            case FAILED:
                return "Index failed";
            default:
                return "Starting...";
        }
    }

    /** Coarse 3px cells: fine enough to read, cheap enough to redraw every frame. */
    private void drawWheel() {
        int cell = 3;
        for (int py = wheelCy - WHEEL_R; py < wheelCy + WHEEL_R; py += cell) {
            for (int px = wheelCx - WHEEL_R; px < wheelCx + WHEEL_R; px += cell) {
                double dx = px + cell / 2.0 - wheelCx;
                double dy = py + cell / 2.0 - wheelCy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > WHEEL_R) continue;
                float h = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
                float s = (float) Math.min(1.0, dist / WHEEL_R);
                rect(px, py, px + cell, py + cell, 0xFF000000 | hsvToRgb(h, s, val));
            }
        }
        double ang = Math.toRadians(hue);
        int mx = (int) Math.round(wheelCx + Math.cos(ang) * sat * WHEEL_R);
        int my = (int) Math.round(wheelCy + Math.sin(ang) * sat * WHEEL_R);
        rect(mx - 3, my - 3, mx + 3, my + 3, 0xFF000000);
        rect(mx - 2, my - 2, mx + 2, my + 2, 0xFFFFFFFF);
    }

    // ---- colour maths ----

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (h < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (h < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (h < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (h < 300) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == rf) h = 60 * (((gf - bf) / d) % 6);
        else if (max == gf) h = 60 * ((bf - rf) / d + 2);
        else h = 60 * ((rf - gf) / d + 4);
        if (h < 0) h += 360;
        return new float[] { h, max == 0 ? 0 : d / max, max };
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (hexBox != null) hexBox.mouseClicked(mouseX, mouseY, button);

        if (inWheel(mouseX, mouseY)) {
            draggingWheel = true;
            pick(mouseX, mouseY);
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            int x = left + 150 + (i % COLS) * 18;
            int y = top + 40 + (i / COLS) * 18;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                copyToClipboard(
                    results.get(i)
                        .id());
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceClick) {
        if (draggingWheel) {
            pick(mouseX, mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) draggingWheel = false;
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (hexBox != null && hexBox.isFocused()) {
            if (keyCode == 1) { // Esc still closes
                super.keyTyped(typedChar, keyCode);
                return;
            }
            hexBox.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private boolean inWheel(int mouseX, int mouseY) {
        double dx = mouseX - wheelCx;
        double dy = mouseY - wheelCy;
        return Math.sqrt(dx * dx + dy * dy) <= WHEEL_R + 2;
    }

    private void pick(int mouseX, int mouseY) {
        double dx = mouseX - wheelCx;
        double dy = mouseY - wheelCy;
        hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
        sat = (float) Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / WHEEL_R);
        dirty = true;
        syncHexBox();
    }

    @Override
    public void updateScreen() {
        if (hexBox != null) {
            hexBox.updateCursorCounter();
            // 1.7.10 text fields have no change listener, so poll for edits.
            String now = hexBox.getText();
            if (!now.equals(lastHex)) {
                lastHex = now;
                onHexTyped(now);
            }
        }
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
    }
}
