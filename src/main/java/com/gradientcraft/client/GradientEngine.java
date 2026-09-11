package com.gradientcraft.client;

import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces an ordered list of blocks that transitions through the given
 * waypoint blocks. Intermediate steps are chosen from the filtered palette
 * by nearest color in LAB space.
 */
public final class GradientEngine {

    private GradientEngine() {}

    /**
     * Palette filter controlled by the in-GUI toggle buttons. Applies only to
     * the in-between blocks the engine picks; user-chosen waypoints are always
     * kept regardless of these settings.
     */
    public record Filter(boolean opaqueOnly, boolean fullOnly, boolean noBlockEntities) {
        public static final Filter NONE = new Filter(false, false, false);

        public boolean accepts(BlockColorIndex.Entry e) {
            if (opaqueOnly && e.opaqueFraction < 0.999f) return false;
            if (fullOnly && !e.fullBlock) return false;
            if (noBlockEntities && e.blockEntity) return false;
            return true;
        }
    }

    /**
     * @param waypoints   ordered endpoint/midpoint blocks the user dragged in (>= 1)
     * @param length      total number of blocks in the output strip
     * @param allowDup    if false, avoids picking the same block as the previous slot
     * @param filter      palette restrictions from the GUI toggles
     */
    public static List<Block> generate(List<Block> waypoints, int length, boolean allowDup, Filter filter) {
        List<Block> out = new ArrayList<>();
        if (waypoints.isEmpty() || !BlockColorIndex.isReady()) return out;
        if (filter == null) filter = Filter.NONE;

        // Resolve waypoint colors; drop any without a known color.
        List<Block> wp = new ArrayList<>();
        List<float[]> wpLab = new ArrayList<>();
        for (Block b : waypoints) {
            float[] lab = BlockColorIndex.labOf(b);
            if (lab != null) { wp.add(b); wpLab.add(lab); }
        }
        if (wp.isEmpty()) return out;
        if (wp.size() == 1) { out.add(wp.get(0)); return out; }

        length = Math.max(length, wp.size());

        // Place each waypoint at an evenly spaced index along the strip.
        int n = wp.size();
        int[] wpIndex = new int[n];
        for (int i = 0; i < n; i++) {
            wpIndex[i] = (int) Math.round((double) i * (length - 1) / (n - 1));
        }
        for (int i = 1; i < n; i++) {
            if (wpIndex[i] <= wpIndex[i - 1]) wpIndex[i] = wpIndex[i - 1] + 1;
        }
        wpIndex[n - 1] = length - 1;

        // Build a target LAB color for every slot by linear interpolation per segment.
        float[][] target = new float[length][];
        for (int seg = 0; seg < n - 1; seg++) {
            int a = wpIndex[seg];
            int b = wpIndex[seg + 1];
            float[] ca = wpLab.get(seg);
            float[] cb = wpLab.get(seg + 1);
            for (int i = a; i <= b; i++) {
                double t = (b == a) ? 0 : (double) (i - a) / (b - a);
                target[i] = Lab.lerp(ca, cb, t);
            }
        }

        // Snap each slot to a block. Waypoint slots are forced to the exact block.
        Block prev = null;
        for (int i = 0; i < length; i++) {
            Block chosen = forcedWaypoint(i, wpIndex, wp);
            if (chosen == null) {
                chosen = nearest(target[i], allowDup ? null : prev, filter);
            }
            out.add(chosen);
            prev = chosen;
        }
        return out;
    }

    private static Block forcedWaypoint(int i, int[] wpIndex, List<Block> wp) {
        for (int k = 0; k < wpIndex.length; k++) {
            if (wpIndex[k] == i) return wp.get(k);
        }
        return null;
    }

    /** Nearest palette block to a LAB target, respecting the filter and optionally avoiding a repeat. */
    private static Block nearest(float[] targetLab, Block exclude, Filter filter) {
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        Block fallbackAny = null; // first filter-passing block, used if exclude removed the only match

        for (BlockColorIndex.Entry e : BlockColorIndex.palette()) {
            if (!filter.accepts(e)) continue;
            if (fallbackAny == null) fallbackAny = e.block;
            if (e.block == exclude) continue;
            double d = Lab.deltaE(targetLab, e.lab);
            if (d < bestDist) { bestDist = d; best = e.block; }
        }
        if (best == null) best = fallbackAny;       // filter left only the excluded block
        if (best == null && !BlockColorIndex.palette().isEmpty()) {
            best = BlockColorIndex.palette().get(0).block; // filter matched nothing at all
        }
        return best;
    }
}
