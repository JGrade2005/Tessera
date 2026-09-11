package com.gradientcraft.client.palette;

import com.gradientcraft.client.BlockColorIndex;
import com.gradientcraft.client.Lab;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds a building palette of {@code count} blocks from the indexed colors:
 *   - slot 0: base   - a dominant, fairly neutral, full-cube structural block
 *   - slot 1: wall   - a supporting/texture block close in color (analogous)
 *   - slot 2: accent - a contrasting, more saturated block that pops
 *   - slots 3+:        extra blocks that harmonize, anchored on the core colors
 *
 * Any slot may be locked (non-null in {@code locked}); locked blocks are kept
 * and used as anchors, the rest are derived. Seeded so "reshuffle" gives a
 * fresh combination from the same locked anchors.
 */
public final class PaletteEngine {

    private PaletteEngine() {}

    // extras lean toward base/wall (more cohesive), with occasional accent.
    private static final int[] ANCHOR_PATTERN = { 0, 1, 0, 1, 2 };

    public static Block[] generate(Block[] locked, int count, long seed) {
        Random rnd = new Random(seed);
        List<BlockColorIndex.Entry> all = BlockColorIndex.palette();
        List<BlockColorIndex.Entry> pool = filter(all, PaletteEngine::buildable);
        if (pool.isEmpty()) pool = new ArrayList<>(all);

        Block[] out = new Block[count];
        Set<Block> used = new HashSet<>();
        if (locked != null) {
            for (int i = 0; i < count && i < locked.length; i++) {
                if (locked[i] != null) { out[i] = locked[i]; used.add(locked[i]); }
            }
        }
        if (pool.isEmpty()) return out;

        // slot 0: neutral base
        if (out[0] == null) {
            List<BlockColorIndex.Entry> n = filter(pool, e -> !used.contains(e.block) && chroma(e.lab) < 14);
            out[0] = pickOr(n, pool, used, rnd);
            used.add(out[0]);
        }
        final float[] baseLab = getLab(out[0]);
        double baseChroma = chroma(baseLab);

        // slot 1: analogous wall
        if (count > 1 && out[1] == null) {
            List<BlockColorIndex.Entry> c = filter(pool, e -> !used.contains(e.block) && within(baseLab, e.lab, 8, 32));
            if (c.isEmpty()) c = filter(pool, e -> !used.contains(e.block) && within(baseLab, e.lab, 3, 50));
            out[1] = pickOr(c, pool, used, rnd);
            used.add(out[1]);
        }

        // slot 2: contrasting accent
        if (count > 2 && out[2] == null) {
            List<BlockColorIndex.Entry> colorful = filter(pool, e -> !used.contains(e.block) && chroma(e.lab) > 16);
            List<BlockColorIndex.Entry> c = colorful;
            if (baseChroma >= 10) {
                List<BlockColorIndex.Entry> ct = filter(colorful, e -> hueDiff(baseLab, e.lab) > 40);
                if (!ct.isEmpty()) c = ct;
            }
            out[2] = pickOr(c, pool, used, rnd);
            used.add(out[2]);
        }

        // slots 3+: extras anchored on a core color, analogous and distinct
        int core = Math.min(3, count);
        for (int i = 3; i < count; i++) {
            if (out[i] != null) continue;
            int anchorIdx = ANCHOR_PATTERN[(i - 3) % ANCHOR_PATTERN.length] % core;
            final float[] anchorLab = getLab(out[anchorIdx]);
            List<BlockColorIndex.Entry> c = filter(pool, e -> !used.contains(e.block) && within(anchorLab, e.lab, 6, 28));
            if (c.isEmpty()) c = filter(pool, e -> !used.contains(e.block) && within(anchorLab, e.lab, 3, 45));
            out[i] = pickOr(c, pool, used, rnd);
            used.add(out[i]);
        }
        return out;
    }

    private static Block pickOr(List<BlockColorIndex.Entry> cand, List<BlockColorIndex.Entry> pool,
                                Set<Block> used, Random rnd) {
        if (!cand.isEmpty()) return cand.get(rnd.nextInt(cand.size())).block;
        List<BlockColorIndex.Entry> unused = filter(pool, e -> !used.contains(e.block));
        if (!unused.isEmpty()) return unused.get(rnd.nextInt(unused.size())).block;
        return pool.get(rnd.nextInt(pool.size())).block; // last resort: allow a repeat
    }

    private static boolean buildable(BlockColorIndex.Entry e) {
        return e.opaqueFraction >= 0.999f && e.fullBlock && !e.blockEntity;
    }
    private static float[] getLab(Block b) {
        float[] l = BlockColorIndex.labOf(b);
        return l != null ? l : new float[] { 70, 0, 0 };
    }
    private static double chroma(float[] lab) { return Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]); }
    private static double hueDeg(float[] lab) { return Math.toDegrees(Math.atan2(lab[2], lab[1])); }
    private static double hueDiff(float[] a, float[] b) {
        double d = Math.abs(hueDeg(a) - hueDeg(b)) % 360;
        return d > 180 ? 360 - d : d;
    }
    private static boolean within(float[] a, float[] b, double lo, double hi) {
        double d = Lab.deltaE(a, b);
        return d >= lo && d <= hi;
    }
    private static List<BlockColorIndex.Entry> filter(List<BlockColorIndex.Entry> in, Predicate<BlockColorIndex.Entry> p) {
        List<BlockColorIndex.Entry> out = new ArrayList<>();
        for (BlockColorIndex.Entry e : in) if (p.test(e)) out.add(e);
        return out;
    }
}