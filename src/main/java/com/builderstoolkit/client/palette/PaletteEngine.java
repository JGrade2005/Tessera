package com.builderstoolkit.client.palette;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.builderstoolkit.client.BlockColorIndex;
import com.builderstoolkit.client.BlockColorIndex.Entry;
import com.builderstoolkit.client.Lab;
import com.builderstoolkit.client.PaletteFilter;

/**
 * Builds a building palette of {@code count} blocks:
 * slot 0 base - dominant, fairly neutral, full-cube structural block
 * slot 1 wall - supporting block close in colour (analogous)
 * slot 2 accent - contrasting, more saturated block
 * slots 3+ - extras anchored on the core colours
 *
 * Locked slots are kept and act as anchors; the rest are derived. Seeded, so
 * reshuffling gives a fresh combination from the same anchors.
 */
public final class PaletteEngine {

    private PaletteEngine() {}

    /** Extras lean toward base/wall for cohesion, with an occasional accent. */
    private static final int[] ANCHOR_PATTERN = { 0, 1, 0, 1, 2 };

    private static final float[] NEUTRAL_LAB = { 70, 0, 0 };

    public static Entry[] generate(Entry[] locked, int count, long seed) {
        Random rnd = new Random(seed);
        List<Entry> all = BlockColorIndex.snapshot();
        List<Entry> pool = new ArrayList<Entry>();
        for (Entry e : all) {
            if (buildable(e)) pool.add(e);
        }
        if (pool.isEmpty()) pool = all;

        Entry[] out = new Entry[count];
        Set<Entry> used = new HashSet<Entry>();
        if (locked != null) {
            for (int i = 0; i < count && i < locked.length; i++) {
                if (locked[i] != null) {
                    out[i] = locked[i];
                    used.add(locked[i]);
                }
            }
        }
        if (pool.isEmpty()) return out;

        // slot 0: neutral base
        if (out[0] == null) {
            List<Entry> c = new ArrayList<Entry>();
            for (Entry e : pool) {
                if (!used.contains(e) && chroma(e.lab) < 14) c.add(e);
            }
            out[0] = pickOr(c, pool, used, rnd);
            used.add(out[0]);
        }
        float[] baseLab = labOf(out[0]);
        double baseChroma = chroma(baseLab);

        // slot 1: analogous wall
        if (count > 1 && out[1] == null) {
            List<Entry> c = near(pool, used, baseLab, 8, 32);
            if (c.isEmpty()) c = near(pool, used, baseLab, 3, 50);
            out[1] = pickOr(c, pool, used, rnd);
            used.add(out[1]);
        }

        // slot 2: contrasting accent
        if (count > 2 && out[2] == null) {
            List<Entry> colorful = new ArrayList<Entry>();
            for (Entry e : pool) {
                if (!used.contains(e) && chroma(e.lab) > 16) colorful.add(e);
            }
            List<Entry> c = colorful;
            if (baseChroma >= 10) {
                List<Entry> shifted = new ArrayList<Entry>();
                for (Entry e : colorful) {
                    if (hueDiff(baseLab, e.lab) > 40) shifted.add(e);
                }
                if (!shifted.isEmpty()) c = shifted;
            }
            out[2] = pickOr(c, pool, used, rnd);
            used.add(out[2]);
        }

        // slots 3+: extras anchored on a core colour, analogous and distinct
        int core = Math.min(3, count);
        for (int i = 3; i < count; i++) {
            if (out[i] != null) continue;
            float[] anchor = labOf(out[ANCHOR_PATTERN[(i - 3) % ANCHOR_PATTERN.length] % core]);
            List<Entry> c = near(pool, used, anchor, 6, 28);
            if (c.isEmpty()) c = near(pool, used, anchor, 3, 45);
            out[i] = pickOr(c, pool, used, rnd);
            used.add(out[i]);
        }
        return out;
    }

    /** Unused pool entries whose distance from {@code anchor} falls in [lo, hi]. */
    private static List<Entry> near(List<Entry> pool, Set<Entry> used, float[] anchor, double lo, double hi) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : pool) {
            if (used.contains(e)) continue;
            double d = Lab.deltaE(anchor, e.lab);
            if (d >= lo && d <= hi) out.add(e);
        }
        return out;
    }

    private static Entry pickOr(List<Entry> cand, List<Entry> pool, Set<Entry> used, Random rnd) {
        if (!cand.isEmpty()) return cand.get(rnd.nextInt(cand.size()));
        List<Entry> unused = new ArrayList<Entry>();
        for (Entry e : pool) {
            if (!used.contains(e)) unused.add(e);
        }
        if (!unused.isEmpty()) return unused.get(rnd.nextInt(unused.size()));
        return pool.get(rnd.nextInt(pool.size())); // last resort: allow a repeat
    }

    /** Whatever the shared filter toggles allow, same as the other tabs. */
    private static boolean buildable(Entry e) {
        return PaletteFilter.accepts(e);
    }

    private static float[] labOf(Entry e) {
        return e != null ? e.lab : NEUTRAL_LAB;
    }

    private static double chroma(float[] lab) {
        return Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]);
    }

    private static double hueDiff(float[] a, float[] b) {
        double da = Math.toDegrees(Math.atan2(a[2], a[1]));
        double db = Math.toDegrees(Math.atan2(b[2], b[1]));
        double d = Math.abs(da - db) % 360;
        return d > 180 ? 360 - d : d;
    }
}
