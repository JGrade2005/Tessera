package com.gradientcraft.client.gui;

import com.gradientcraft.client.BlockColorIndex;
import com.gradientcraft.client.Lab;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Colors" tab: pick a color from an HSV wheel (+ brightness slider, or a hex
 * code) and see the blocks whose average texture color is closest, ranked by
 * perceptual distance in CIELAB. Reuses the same block-color index as the
 * Gradient tab. Fully client-side.
 */
public class ColorScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 232;
    private static final int RESULTS = 28; // closest blocks to show
    private static final int COLS = 7;

    // persistent picked color
    private static float hue = 0f, sat = 0f, val = 1f;

    private int left, top;
    private int wheelCx, wheelCy, wheelR;
    private boolean draggingWheel = false;

    private EditBox hexBox;
    private boolean suppressHex = false;

    private boolean dirty = true;
    private int targetRgb = 0xFFFFFF;
    private final List<BlockColorIndex.Entry> results = new ArrayList<>();

    public ColorScreen() { super(new TextComponent("Color Picker")); }

    @Override
    protected void init() {
        BlockColorIndex.ensureStarted();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        wheelR = 60;
        wheelCx = left + 16 + wheelR;
        wheelCy = top + 36 + wheelR;

        // tabs
        addRenderableWidget(new Button(left + 4, top - 22, 70, 20, new TextComponent("Gradient"),
                b -> Minecraft.getInstance().setScreen(new GradientScreen())));
        addRenderableWidget(new Button(left + 78, top - 22, 70, 20, new TextComponent("Generate"),
                b -> Minecraft.getInstance().setScreen(new GenerateScreen())));
        Button tabCol = new Button(left + 152, top - 22, 70, 20, new TextComponent("Colors"), b -> {});
        tabCol.active = false;
        addRenderableWidget(tabCol);
        addRenderableWidget(new Button(left + 226, top - 22, 70, 20, new TextComponent("Palette"),
                b -> Minecraft.getInstance().setScreen(new PaletteScreen())));
        addRenderableWidget(new Button(left + 300, top - 22, 58, 20, new TextComponent("Lab"),
                b -> Minecraft.getInstance().setScreen(new LabScreen())));

        // brightness slider
        addRenderableWidget(new ValSlider(left + 16, top + 162, 2 * wheelR, 16));

        // hex input
        hexBox = new EditBox(this.font, left + 16, top + 184, 2 * wheelR, 16, new TextComponent("hex"));
        hexBox.setMaxLength(7);
        suppressHex = true; hexBox.setValue(currentHex()); suppressHex = false;
        hexBox.setResponder(this::onHexTyped);
        addRenderableWidget(hexBox);

        dirty = true;
    }

    private String currentHex() {
        int rgb = hsvToRgb(hue, sat, val);
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private void onHexTyped(String s) {
        if (suppressHex) return;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 6) {
            try {
                int rgb = Integer.parseInt(t, 16);
                float[] hsv = rgbToHsv((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                hue = hsv[0]; sat = hsv[1]; val = hsv[2];
                dirty = true;
                rebuildSoon = true; // refresh the brightness slider position next tick
            } catch (NumberFormatException ignored) { }
        }
    }

    private boolean rebuildSoon = false;

    // ---- matching ----

    private void recompute() {
        dirty = false;
        targetRgb = hsvToRgb(hue, sat, val);
        results.clear();
        if (!BlockColorIndex.isReady()) return;
        float[] targetLab = Lab.rgbToLab((targetRgb >> 16) & 0xFF, (targetRgb >> 8) & 0xFF, targetRgb & 0xFF);

        List<BlockColorIndex.Entry> all = new ArrayList<>(BlockColorIndex.palette());
        all.sort((a, b) -> Double.compare(Lab.deltaE(targetLab, a.lab), Lab.deltaE(targetLab, b.lab)));
        for (int i = 0; i < Math.min(RESULTS, all.size()); i++) results.add(all.get(i));
    }

    // ---- rendering ----

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        if (dirty) recompute();
        this.renderBackground(pose);

        fill(pose, left, top, left + PANEL_W, top + PANEL_H, 0xF0101018);
        fill(pose, left, top, left + PANEL_W, top + 22, 0xFF2B2B3A);
        this.font.draw(pose, this.title, left + 8, top + 7, 0xFFFFFF);

        drawWheel(pose);

        // picked-color swatch + label
        fill(pose, left + 16, top + 204, left + 40, top + 222, 0xFF000000 | targetRgb);
        this.font.draw(pose, new TextComponent(currentHex()), left + 46, top + 209, 0xC8C8D8);

        // results
        this.font.draw(pose, new TextComponent("Closest blocks"), left + 150, top + 26, 0xC8C8D8);
        BlockColorIndex.Entry hovered = null;
        int gx0 = left + 150, gy0 = top + 40;
        for (int i = 0; i < results.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int x = gx0 + col * 18, y = gy0 + row * 18;
            BlockColorIndex.Entry e = results.get(i);
            if (i == 0) fill(pose, x - 1, y - 1, x + 17, y + 17, 0xFFFFD060); // highlight best match
            this.itemRenderer.renderAndDecorateItem(e.stack, x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = e;
        }

        // status / best-match name
        String status;
        switch (BlockColorIndex.state()) {
            case READY -> status = results.isEmpty() ? "No blocks indexed"
                    : results.get(0).stack.getHoverName().getString();
            case INDEXING -> status = "Indexing " + BlockColorIndex.progress() + "/" + BlockColorIndex.total();
            case FAILED -> status = "Index failed";
            default -> status = "Starting...";
        }
        this.font.draw(pose, new TextComponent(this.font.plainSubstrByWidth(status, PANEL_W - 158)),
                left + 150, top + 40 + 4 * 18 + 6, 0xA0A0B0);

        this.font.draw(pose, new TextComponent("click a block to copy its id"),
                left + 150, top + PANEL_H - 12, 0x707080);

        super.render(pose, mouseX, mouseY, partial);

        if (hovered != null) renderTooltip(pose, hovered.stack, mouseX, mouseY);
    }

    private void drawWheel(PoseStack pose) {
        int cell = 3;
        for (int py = wheelCy - wheelR; py < wheelCy + wheelR; py += cell) {
            for (int px = wheelCx - wheelR; px < wheelCx + wheelR; px += cell) {
                double dx = px + cell / 2.0 - wheelCx;
                double dy = py + cell / 2.0 - wheelCy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > wheelR) continue;
                float h = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
                float s = (float) Math.min(1.0, dist / wheelR);
                int rgb = hsvToRgb(h, s, val);
                fill(pose, px, py, px + cell, py + cell, 0xFF000000 | rgb);
            }
        }
        // marker at current hue/sat
        double ang = Math.toRadians(hue);
        int mx = (int) Math.round(wheelCx + Math.cos(ang) * sat * wheelR);
        int my = (int) Math.round(wheelCy + Math.sin(ang) * sat * wheelR);
        fill(pose, mx - 3, my - 3, mx + 3, my + 3, 0xFF000000);
        fill(pose, mx - 2, my - 2, mx + 2, my + 2, 0xFFFFFFFF);
    }

    // ---- color math ----

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 60)       { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else              { r = c; g = 0; b = x; }
        int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == rf) h = 60 * (((gf - bf) / d) % 6);
        else if (max == gf) h = 60 * ((bf - rf) / d + 2);
        else                h = 60 * ((rf - gf) / d + 4);
        if (h < 0) h += 360;
        float s = max == 0 ? 0 : d / max;
        return new float[] { h, s, max };
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inWheel(mx, my)) { draggingWheel = true; pick(mx, my); return true; }
        // click a result to copy its id
        int gx0 = left + 150, gy0 = top + 40;
        for (int i = 0; i < results.size(); i++) {
            int x = gx0 + (i % COLS) * 18, y = gy0 + (i / COLS) * 18;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                Minecraft.getInstance().keyboardHandler.setClipboard(
                        String.valueOf(Registry.BLOCK.getKey(results.get(i).block)));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingWheel) { pick(mx, my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingWheel = false;
        return super.mouseReleased(mx, my, button);
    }

    private boolean inWheel(double mx, double my) {
        double dx = mx - wheelCx, dy = my - wheelCy;
        return Math.sqrt(dx * dx + dy * dy) <= wheelR + 2;
    }

    private void pick(double mx, double my) {
        double dx = mx - wheelCx, dy = my - wheelCy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
        sat = (float) Math.min(1.0, dist / wheelR);
        dirty = true;
        if (hexBox != null) { suppressHex = true; hexBox.setValue(currentHex()); suppressHex = false; }
    }

    @Override
    public void tick() {
        if (rebuildSoon) { rebuildSoon = false; this.clearWidgets(); this.init(); }
        if (hexBox != null) hexBox.tick();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // brightness slider
    private class ValSlider extends AbstractSliderButton {
        ValSlider(int x, int y, int w, int h) {
            super(x, y, w, h, new TextComponent(""), val);
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(new TextComponent("brightness: " + Math.round(val * 100) + "%"));
        }
        @Override protected void applyValue() {
            val = (float) this.value;
            dirty = true;
            if (hexBox != null) { suppressHex = true; hexBox.setValue(currentHex()); suppressHex = false; }
        }
    }
}
