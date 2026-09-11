package com.builderstoolkit.client.blend;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a blend grid as WorldEdit commands.
 *
 * WorldEdit patterns can express "62% stone, 38% cobblestone" but not where in
 * the region each block lands, so a gradient becomes one weighted {@code //set}
 * per layer with a {@code //shift} between. That reproduces BANDS, DITHER and
 * NOISE faithfully, because those only vary along the build axis.
 *
 * MAP varies across the layer instead, which a weighted pattern cannot capture,
 * so it collapses to a single {@code //set} carrying the overall mix.
 */
public final class WorldEditScript {

    private WorldEditScript() {}

    /**
     * @param ids block id per gradient step, in order
     * @return the command lines, to paste or save
     */
    public static List<String> build(int[] grid, int width, int height, List<String> ids, BlendEngine.Mode mode) {
        List<String> out = new ArrayList<String>();
        int steps = ids.size();
        if (grid.length == 0 || steps == 0) return out;

        if (mode == BlendEngine.Mode.MAP) {
            out.add("// MAP varies across the layer, which a WorldEdit pattern cannot place.");
            out.add("// This sets the whole selection to the same overall mix.");
            out.add("//set " + pattern(BlendEngine.weights(grid, steps), ids));
            return out;
        }

        out.add("// Select the TOP layer of the wall (one block thick), then run these in order.");
        for (int y = 0; y < height; y++) {
            out.add("//set " + pattern(BlendEngine.rowWeights(grid, width, y, steps), ids));
            if (y < height - 1) out.add("//shift 1 down");
        }
        return out;
    }

    /**
     * Builds a weighted pattern whose percentages total exactly 100, using the
     * largest-remainder method so rounding never leaves WorldEdit short.
     */
    public static String pattern(double[] weights, List<String> ids) {
        int n = Math.min(weights.length, ids.size());

        int[] percent = new int[n];
        double[] remainder = new double[n];
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            double exact = weights[i] * 100.0;
            percent[i] = (int) Math.floor(exact);
            remainder[i] = exact - percent[i];
            assigned += percent[i];
        }

        // Hand the leftover points to the largest remainders first.
        for (int k = assigned; k < 100; k++) {
            int best = -1;
            double bestRem = -1;
            for (int i = 0; i < n; i++) {
                if (weights[i] > 0 && remainder[i] > bestRem) {
                    bestRem = remainder[i];
                    best = i;
                }
            }
            if (best < 0) break;
            percent[best]++;
            remainder[best] = -1;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (percent[i] <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(percent[i])
                .append('%')
                .append(ids.get(i));
        }
        // Everything rounded away: fall back to the most common step.
        if (sb.length() == 0) sb.append(ids.get(dominant(weights, n)));
        return sb.toString();
    }

    private static int dominant(double[] weights, int n) {
        int best = 0;
        for (int i = 1; i < n; i++) {
            if (weights[i] > weights[best]) best = i;
        }
        return best;
    }
}
