package com.builderstoolkit.client.blend;

import java.util.Random;

/**
 * Turns a linear gradient into a 2D field of gradient indices: the wall you
 * would actually build, rather than a strip of one block per step.
 *
 * Pure maths - no Minecraft types - so the modes can be tested directly.
 */
public final class BlendEngine {

    /** How the gradient index is chosen for each cell. */
    public enum Mode {
        /** Hard horizontal bands, one gradient step per row. */
        BANDS,
        /** Bands softened by per-cell white noise: the classic dithered blend. */
        DITHER,
        /** Bands softened by coherent noise, giving organic interlocking edges. */
        NOISE,
        /** No gradient axis at all - the index comes purely from 2D noise, like a biome map. */
        MAP
    }

    /** How far, in gradient steps, a randomness of 100% can pull a cell. */
    private static final double SPREAD = 3.0;

    private BlendEngine() {}

    /**
     * Builds a {@code width * height} grid of indices into a gradient of
     * {@code steps} blocks. Row 0 is the first gradient block.
     *
     * <p>
     * Runs in O(width * height * octaves); a GUI preview is a few thousand
     * cells, so it is cheap enough to rebuild whenever a control moves.
     *
     * @param randomness 0..1, how strongly noise perturbs the band position
     * @param scale      noise frequency in cells; larger means bigger shapes
     * @param octaves    fractal detail levels for the coherent modes
     * @return a flat array of length {@code width * height}, row-major
     */
    public static int[] build(int width, int height, int steps, Mode mode, double randomness, double scale, int octaves,
        long seed) {

        int[] grid = new int[Math.max(0, width) * Math.max(0, height)];
        if (grid.length == 0 || steps <= 0) return grid;
        if (steps == 1) return grid; // every cell is index 0

        NoiseField noise = new NoiseField(seed);
        Random white = new Random(seed);
        double freq = 1.0 / Math.max(1.0, scale);

        for (int y = 0; y < height; y++) {
            double t = height == 1 ? 0.0 : (double) y / (height - 1);
            double band = t * (steps - 1);

            for (int x = 0; x < width; x++) {
                double index;

                switch (mode) {
                    case DITHER:
                        index = band + (white.nextDouble() - 0.5) * 2.0 * randomness * SPREAD;
                        break;
                    case NOISE:
                        index = band + (noise.fbm(x * freq, y * freq, octaves) - 0.5) * 2.0 * randomness * SPREAD;
                        break;
                    case MAP:
                        // Pure 2D noise: the gradient supplies the palette, not a direction.
                        index = noise.fbm(x * freq, y * freq, octaves) * steps - 0.5;
                        break;
                    default:
                        index = band;
                        break;
                }

                grid[y * width + x] = clamp((int) Math.round(index), 0, steps - 1);
            }
        }
        return grid;
    }

    /** Share of each gradient index across the whole grid, as fractions summing to 1. */
    public static double[] weights(int[] grid, int steps) {
        double[] out = new double[Math.max(0, steps)];
        if (grid.length == 0 || steps <= 0) return out;
        for (int v : grid) {
            if (v >= 0 && v < steps) out[v]++;
        }
        for (int i = 0; i < steps; i++) {
            out[i] /= grid.length;
        }
        return out;
    }

    /** Share of each gradient index within one row. */
    public static double[] rowWeights(int[] grid, int width, int row, int steps) {
        double[] out = new double[Math.max(0, steps)];
        if (width <= 0 || steps <= 0) return out;
        int start = row * width;
        if (start < 0 || start + width > grid.length) return out;
        for (int x = 0; x < width; x++) {
            int v = grid[start + x];
            if (v >= 0 && v < steps) out[v]++;
        }
        for (int i = 0; i < steps; i++) {
            out[i] /= width;
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
