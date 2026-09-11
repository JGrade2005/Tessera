package com.gradientcraft.client;

/**
 * sRGB <-> CIELAB conversion (D65 reference white) plus a perceptual distance.
 *
 * We interpolate gradients in LAB space because it is roughly perceptually
 * uniform, so a straight line through it produces a transition that looks
 * smooth to the eye rather than smooth in raw RGB numbers.
 */
public final class Lab {

    private Lab() {}

    /** Convert 0-255 sRGB components to a {L, a, b} float array. */
    public static float[] rgbToLab(int r, int g, int b) {
        double rl = pivotSrgb(r / 255.0);
        double gl = pivotSrgb(g / 255.0);
        double bl = pivotSrgb(b / 255.0);

        // linear sRGB -> XYZ (D65)
        double x = rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375;
        double y = rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750;
        double z = rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041;

        // normalize by D65 white point
        double fx = pivotXyz(x / 0.95047);
        double fy = pivotXyz(y / 1.00000);
        double fz = pivotXyz(z / 1.08883);

        float l = (float) (116.0 * fy - 16.0);
        float a = (float) (500.0 * (fx - fy));
        float bb = (float) (200.0 * (fy - fz));
        return new float[] { l, a, bb };
    }

    private static double pivotSrgb(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double pivotXyz(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t) + (16.0 / 116.0);
    }

    /** CIE76 distance. Good enough for nearest-block matching and very fast. */
    public static double deltaE(float[] p, float[] q) {
        double dl = p[0] - q[0];
        double da = p[1] - q[1];
        double db = p[2] - q[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /** Linear interpolation between two LAB colors. */
    public static float[] lerp(float[] a, float[] b, double t) {
        return new float[] {
            (float) (a[0] + (b[0] - a[0]) * t),
            (float) (a[1] + (b[1] - a[1]) * t),
            (float) (a[2] + (b[2] - a[2]) * t),
        };
    }
}
