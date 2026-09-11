package com.builderstoolkit.client.blend;

/**
 * Seeded 2D value noise with fractal octaves. Coherent, unlike a plain random
 * call, which is what turns scattered speckle into the organic blobs the noise
 * blend modes need.
 *
 * Value noise rather than Perlin: it needs no gradient table, is a few lines,
 * and at the scales a GUI preview uses the difference is not visible.
 */
public final class NoiseField {

    private final int seed;

    public NoiseField(long seed) {
        this.seed = (int) (seed ^ (seed >>> 32));
    }

    /** Hashed lattice value in [0,1). */
    private double lattice(int x, int y) {
        int n = x * 374761393 + y * 668265263 + seed * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        n = n ^ (n >>> 16);
        return (n & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    /** Smoothstep, so cells blend without visible lattice creases. */
    private static double fade(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Single-octave noise in [0,1]. */
    public double value(double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = fade(x - x0);
        double fy = fade(y - y0);

        double top = lerp(lattice(x0, y0), lattice(x0 + 1, y0), fx);
        double bottom = lerp(lattice(x0, y0 + 1), lattice(x0 + 1, y0 + 1), fx);
        return lerp(top, bottom, fy);
    }

    /**
     * Fractal sum. More octaves add finer detail on top of the large shapes,
     * which is what separates a rough coastline from a smooth blob.
     *
     * @return a value in [0,1]
     */
    public double fbm(double x, double y, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        double frequency = 1;

        for (int i = 0; i < Math.max(1, octaves); i++) {
            sum += value(x * frequency, y * frequency) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return total == 0 ? 0 : sum / total;
    }
}
