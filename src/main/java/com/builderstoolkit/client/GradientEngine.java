package com.builderstoolkit.client;

import java.util.ArrayList;
import java.util.List;

import com.builderstoolkit.client.BlockColorIndex.Entry;

/**
 * Produces an ordered strip of block variants transitioning through the given
 * waypoints. In-between steps are the nearest palette entry in LAB space.
 */
public final class GradientEngine {

    private GradientEngine() {}

    /**
     * Runs in O(length * palette) time and O(length) space, which is fine for a
     * one-shot click: length caps at 256 and the palette at a few thousand.
     *
     * @param waypoints ordered endpoint/midpoint variants (at least one)
     * @param length    total blocks in the output strip
     * @param allowDup  if false, avoids repeating the previous slot
     */
    public static List<Entry> generate(List<Entry> waypoints, int length, boolean allowDup) {
        List<Entry> out = new ArrayList<Entry>();
        if (waypoints == null || waypoints.isEmpty() || !BlockColorIndex.isReady()) return out;

        List<Entry> wp = new ArrayList<Entry>();
        for (Entry e : waypoints) {
            if (e != null) wp.add(e);
        }
        if (wp.isEmpty()) return out;
        if (wp.size() == 1) {
            out.add(wp.get(0));
            return out;
        }

        int n = wp.size();
        length = Math.max(length, n);

        // Place each waypoint at an evenly spaced index, keeping the order strict.
        int[] wpIndex = new int[n];
        for (int i = 0; i < n; i++) {
            wpIndex[i] = (int) Math.round((double) i * (length - 1) / (n - 1));
        }
        for (int i = 1; i < n; i++) {
            if (wpIndex[i] <= wpIndex[i - 1]) wpIndex[i] = wpIndex[i - 1] + 1;
        }
        wpIndex[n - 1] = length - 1;

        // Interpolate a target colour for every slot, segment by segment.
        float[][] target = new float[length][];
        for (int seg = 0; seg < n - 1; seg++) {
            int a = wpIndex[seg];
            int b = wpIndex[seg + 1];
            for (int i = a; i <= b; i++) {
                double t = (b == a) ? 0 : (double) (i - a) / (b - a);
                target[i] = Lab.lerp(wp.get(seg).lab, wp.get(seg + 1).lab, t);
            }
        }

        Entry prev = null;
        for (int i = 0; i < length; i++) {
            Entry chosen = forcedWaypoint(i, wpIndex, wp);
            if (chosen == null) chosen = nearest(target[i], allowDup ? null : prev);
            if (chosen == null) break; // empty palette
            out.add(chosen);
            prev = chosen;
        }
        return out;
    }

    private static Entry forcedWaypoint(int i, int[] wpIndex, List<Entry> wp) {
        for (int k = 0; k < wpIndex.length; k++) {
            if (wpIndex[k] == i) return wp.get(k);
        }
        return null;
    }

    /** Nearest palette entry to a LAB target, respecting the filter and optionally avoiding a repeat. */
    private static Entry nearest(float[] targetLab, Entry exclude) {
        Entry best = null;
        double bestDist = Double.MAX_VALUE;
        Entry fallback = null; // first filter-passing entry, in case exclude removed the only match

        List<Entry> palette = BlockColorIndex.palette();
        synchronized (palette) {
            for (int i = 0; i < palette.size(); i++) {
                Entry e = palette.get(i);
                if (!PaletteFilter.accepts(e)) continue;
                if (fallback == null) fallback = e;
                if (e == exclude) continue;
                double d = Lab.deltaE(targetLab, e.lab);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }
            if (best == null) best = fallback;
            if (best == null && !palette.isEmpty()) best = palette.get(0); // filter matched nothing
        }
        return best;
    }
}
