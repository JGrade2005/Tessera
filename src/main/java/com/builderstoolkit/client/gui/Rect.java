package com.builderstoolkit.client.gui;

/** Minimal hit-test rectangle; 1.7.10 has no Rect2i. */
public final class Rect {

    public final int x;
    public final int y;
    public final int w;
    public final int h;

    public Rect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
