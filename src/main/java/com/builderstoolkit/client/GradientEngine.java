package com.builderstoolkit.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.builderstoolkit.client.BlockColorIndex.Entry;

/**
 * Produces an ordered strip of block variants transitioning through the given
 * waypoints. In-between steps are the best-fitting palette entry for an
 * interpolated LAB target.
 *
 * Best fit is not the same as nearest colour. A block whose average matches but
 * whose texture is a two-tone mess reads as noise in the wall, so a candidate is
 * scored on its distance to the target plus a share of its own colour spread.
 * That is what makes the strip look like a gradient rather than a list of
 * technically-closest blocks.
 */
public final class GradientEngine {

    /**
     * Weight on a block's internal colour spread, in deltaE per unit of RMS
     * spread. At 0.30 a busy texture (spread ~40) has to be about 12 deltaE
     * closer than a flat one to win, which is roughly "clearly a better match".
     */
    private static final double UNIFORMITY = 0.30;

    /**
     * Score added to blocks the caller asked to avoid. Large enough to hand the
     * slot to a different block, small enough that the replacement is still in
     * the right part of the palette.
     */
    private static final double AVOID_PENALTY = 15.0;

    private GradientEngine() {}

    /** Convenience for the common case of no blocks to avoid. */
    public static List<Entry> generate(List<Entry> waypoints, int length, boolean allowDup) {
        return generate(waypoints, length, allowDup, Collections.<Entry>emptySet());
    }

    /**
     * Runs in O(length * palette) time and O(length) space, which is fine for a
     * one-shot click: length caps at 256 and the palette at a few thousand.
     *
     * @param waypoints ordered endpoint/midpoint variants (at least one)
     * @param length    total blocks in the output strip
     * @param allowDup  if false, every slot gets a block no other slot uses
     * @param avoid     blocks to steer away from, so a reshuffle lands elsewhere
     */
    public static List<Entry> generate(List<Entry> waypoints, int length, boolean allowDup, Set<Entry> avoid) {
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

        // With duplicates off, every block already placed is off the table - not just
        // the one before this slot. Excluding only the previous pick still lets a
        // strip alternate between two blocks, which is not what "no duplicates" means.
        Set<Entry> used = allowDup ? null : new HashSet<Entry>();
        // Waypoints are reserved from the start. Without this, a waypoint can win an
        // earlier slot on merit and then be forced into its own slot as well, which
        // is one duplicate that "no duplicates" would not have caught.
        if (used != null) used.addAll(wp);
        for (int i = 0; i < length; i++) {
            Entry chosen = forcedWaypoint(i, wpIndex, wp);
            if (chosen == null) chosen = bestFit(target[i], used, avoid);
            if (chosen == null) break; // empty palette
            out.add(chosen);
            if (used != null) used.add(chosen);
        }
        return out;
    }

    private static Entry forcedWaypoint(int i, int[] wpIndex, List<Entry> wp) {
        for (int k = 0; k < wpIndex.length; k++) {
            if (wpIndex[k] == i) return wp.get(k);
        }
        return null;
    }

    /**
     * Lowest-scoring palette entry for a LAB target, respecting the filter.
     *
     * @param exclude blocks already spoken for, or null to allow repeats
     */
    private static Entry bestFit(float[] targetLab, Set<Entry> exclude, Set<Entry> avoid) {
        Entry best = null;
        double bestScore = Double.MAX_VALUE;
        Entry fallback = null; // first filter-passing entry, in case exclude left nothing

        List<Entry> palette = BlockColorIndex.palette();
        synchronized (palette) {
            for (int i = 0; i < palette.size(); i++) {
                Entry e = palette.get(i);
                if (!PaletteFilter.accepts(e)) continue;
                if (fallback == null) fallback = e;
                if (exclude != null && exclude.contains(e)) continue;

                double score = Lab.deltaE(targetLab, e.lab) + UNIFORMITY * e.deviation;
                if (avoid.contains(e)) score += AVOID_PENALTY;
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            if (best == null) best = fallback;
            if (best == null && !palette.isEmpty()) best = palette.get(0); // filter matched nothing
        }
        return best;
    }
}
