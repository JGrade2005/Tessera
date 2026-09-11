package com.builderstoolkit.client.blend;

/**
 * The Blend controls, as one value, so every consumer reads the same numbers.
 *
 * The wall preview, the flat-wall script and the shape gradient all used to take
 * these separately, and the shape gradient simply was not given them - which is
 * why it produced hard bands no matter what the preview showed.
 */
public final class BlendSettings {

    public final BlendEngine.Mode mode;
    /** 0..1, how strongly noise perturbs the band position. */
    public final double randomness;
    /** Noise feature size, in blocks. */
    public final double scale;
    public final int octaves;
    public final long seed;

    public BlendSettings(BlendEngine.Mode mode, double randomness, double scale, int octaves, long seed) {
        this.mode = mode;
        this.randomness = randomness;
        this.scale = scale;
        this.octaves = octaves;
        this.seed = seed;
    }

    /** True when the result is hard bands: no noise to add. */
    public boolean flat() {
        return mode == BlendEngine.Mode.BANDS || randomness <= 0;
    }
}
