package com.builderstoolkit.client;

import com.builderstoolkit.client.BlockColorIndex.Entry;

/**
 * Which indexed blocks the tools are allowed to pick from.
 *
 * Shared by the Gradient, Colours and Palette tabs rather than held per screen,
 * so a block excluded in one is excluded in all: switching tabs should not
 * quietly change what you are choosing between.
 */
public final class PaletteFilter {

    /** Display labels, in toggle order. */
    private static final String[] LABELS = { "Opaque", "Full", "No-TE", "Sides", "No-Ore" };

    /** Ores are off by default: their speckle never reads as a clean gradient step. */
    private static final boolean[] ON = { false, false, false, false, true };

    private PaletteFilter() {}

    public static int count() {
        return LABELS.length;
    }

    public static String label(int index) {
        return LABELS[index];
    }

    public static boolean get(int index) {
        return ON[index];
    }

    public static void toggle(int index) {
        ON[index] = !ON[index];
    }

    /**
     * @param e a candidate block variant
     * @return true when it survives every active filter
     */
    public static boolean accepts(Entry e) {
        if (e == null) return false;
        if (ON[0] && e.opaqueFraction < 0.999f) return false;
        if (ON[1] && !e.fullBlock) return false;
        if (ON[2] && e.tileEntity) return false;
        if (ON[3] && !e.sameOnAllSides) return false;
        if (ON[4] && e.ore) return false;
        return true;
    }

    /** How many indexed blocks currently pass, for the status lines. */
    public static int passing() {
        int n = 0;
        for (Entry e : BlockColorIndex.snapshot()) {
            if (accepts(e)) n++;
        }
        return n;
    }
}
