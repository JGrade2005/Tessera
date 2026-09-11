package com.builderstoolkit.client.blend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies a gradient across a generated shape, properly, rather than scattering
 * blocks by percentage.
 *
 * A weighted WorldEdit pattern says how much of each block to use but not where
 * to put it, so it can only ever speckle. This instead emits one {@code //g} per
 * gradient step, each placing a single block inside a band of space intersected
 * with the shape. Running them in order fills the shape once, with the blocks
 * laid out along the chosen axis.
 *
 * Every character counts: Minecraft truncates a chat message at 100 characters,
 * or 256 with GTNH's Hodgepodge, and each pass has to carry the whole shape, its
 * rotation and its noise. That is why the output is written so tersely.
 */
public final class ShapeGradient {

    /** Direction the gradient runs across the shape. */
    public enum Axis {

        Y("y", 1),
        X("x", 0),
        Z("z", 2),
        /** Distance from the centre: fades outward, which suits spheres and tori. */
        RADIAL("sqrt(x^2+y^2+z^2)", 3);

        final String expr;
        /** Index into ShapePreview's measured extents. */
        public final int index;

        Axis(String expr, int index) {
            this.expr = expr;
            this.index = index;
        }
    }

    /** Matches BlendEngine: how far, in gradient steps, a randomness of 100% can pull. */
    private static final double SPREAD = 3.0;

    /**
     * Emitting more than this many octaves costs more characters than the detail is
     * worth: each one has to be repeated in every pass, alongside the whole shape.
     */
    private static final int MAX_OCTAVES = 2;

    /**
     * Per-axis frequency ratios, chosen to be mutually incommensurate so the sines
     * do not line up into a visible lattice. The seed picks a row, which is how a
     * reroll changes the pattern without costing a single character.
     */
    private static final double[][] AXIS_MIX = { { 1.00, 0.77, 1.31 }, { 1.19, 0.91, 0.63 }, { 0.83, 1.27, 1.07 },
        { 1.41, 0.67, 0.97 }, { 0.71, 1.13, 1.37 }, { 1.07, 1.33, 0.79 }, { 1.29, 0.87, 1.11 }, { 0.93, 1.21, 0.73 }, };

    /**
     * Turns a feature size in blocks into a frequency over the normalised -1..1
     * coordinates //g works in. The selection's real size is unknown here, so this
     * is a feel-right mapping rather than an exact one.
     */
    private static final double SCALE_TO_FREQUENCY = 60.0;

    /** DITHER is per-cell scatter in the preview; the nearest an expression gets is a very fine grain. */
    private static final double DITHER_SHARPNESS = 4.0;

    private ShapeGradient() {}

    /**
     * @param shapeExpr the shape, unrotated, as the Generate tab would emit it
     * @param rotation  the coordinate reassignments from
     *                  {@link com.builderstoolkit.client.generate.Rotate3D}, or "" for none
     * @param blockIds  numeric WorldEdit ids, one per gradient step
     * @param blend     the Blend controls, so the build matches what the preview shows
     * @param range     how far the shape actually reaches along the axis, {min, max},
     *                  or null to spread the bands over the whole selection
     * @return one command per gradient step, to run in order. Commands only, with
     *         no comment lines: these get sent to chat, where a leading // would
     *         itself be read as a command.
     */
    public static List<String> build(String shapeExpr, String rotation, List<String> blockIds, Axis axis,
        BlendSettings blend, double[] range, boolean hollow) {

        List<String> out = new ArrayList<String>();
        int n = blockIds.size();
        if (n == 0 || shapeExpr == null
            || shapeExpr.trim()
                .isEmpty())
            return out;

        String noise = blend.flat() ? "" : noise(blend);
        boolean map = !noise.isEmpty() && blend.mode == BlendEngine.Mode.MAP;

        // MAP drops the axis entirely and slices on the noise itself, so it always
        // spans -1..1 however the axis control is set.
        double lo;
        double span;
        if (map) {
            lo = -1.0;
            span = 2.0;
        } else if (range != null && range[1] > range[0]) {
            // Bands across what the shape occupies, not across the selection. A sphere
            // only reaches y = +-0.8, so bands laid over the full -1..1 would leave the
            // first and last blocks of the gradient with nowhere to go.
            lo = range[0];
            span = range[1] - range[0];
        } else {
            lo = axis == Axis.RADIAL ? 0.0 : -1.0;
            span = axis == Axis.RADIAL ? 1.0 : 2.0;
        }

        // The gradient axis is read before the rotation runs, so a vertical fade
        // stays vertical however the shape is spun - and so a shape that moves x
        // or z itself, as Spiral Stair does, cannot drag the gradient with it.
        StringBuilder head = new StringBuilder("g=");
        if (map) {
            head.append(f(1.0 / amplitude(blend.octaves)))
                .append("*(")
                .append(noise)
                .append(')');
        } else {
            head.append(axis.expr);
            if (!noise.isEmpty()) {
                double amp = blend.randomness * SPREAD * span / n / amplitude(blend.octaves);
                head.append('+')
                    .append(f(amp))
                    .append("*(")
                    .append(noise)
                    .append(')');
            }
        }
        head.append(';')
            .append(rotation == null ? "" : rotation);

        String[] body = split(shapeExpr);
        String prefix = head + body[0] + "return (" + body[1] + ")";

        for (int i = 0; i < n; i++) {
            double from = lo + span * i / n;
            double to = lo + span * (i + 1) / n;

            StringBuilder expr = new StringBuilder(prefix);
            // Open-ended at both extremes, so dithered values never fall through a gap.
            if (i > 0) expr.append("&&(g>=")
                .append(f(from))
                .append(')');
            if (i < n - 1) expr.append("&&(g<")
                .append(f(to))
                .append(')');

            out.add("//g " + (hollow ? "-h " : "") + blockIds.get(i) + " " + expr);
        }
        return out;
    }

    /**
     * Sine-sum stand-in for the preview's value noise. The expression language has
     * no noise function and no per-block randomness, so this matches the preview in
     * character - feature size and softness - rather than cell for cell.
     *
     * The first octave varies each axis on its own; later octaves add one mixed
     * term apiece, because three more sines per octave would not fit in a chat
     * message alongside the shape.
     */
    private static String noise(BlendSettings blend) {
        long seed = Math.abs(blend.seed);
        double[] mix = AXIS_MIX[(int) (seed % AXIS_MIX.length)];
        double base = SCALE_TO_FREQUENCY / Math.max(1.0, blend.scale);
        if (blend.mode == BlendEngine.Mode.DITHER) base *= DITHER_SHARPNESS;
        // The seed also nudges the frequencies, so a reroll changes the pattern
        // beyond the eight mixes. A phase term per sine would read better but costs
        // six characters each, which is most of an octave.
        base *= 1.0 + (seed / AXIS_MIX.length % 23) / 100.0;

        StringBuilder sb = new StringBuilder("sin(x*").append(freq(base * mix[0]))
            .append(")+sin(z*")
            .append(freq(base * mix[1]))
            .append(")+sin(y*")
            .append(freq(base * mix[2]))
            .append(')');

        int octaves = Math.min(MAX_OCTAVES, Math.max(1, blend.octaves));
        for (int k = 1; k < octaves; k++) {
            double f = base * (1 << k);
            sb.append("+sin(x*")
                .append(freq(f * mix[1]))
                .append("+z*")
                .append(freq(f * mix[2]))
                .append(")/")
                .append(1 << k);
        }
        return sb.toString();
    }

    /** Largest value {@link #noise} can reach, so the result can be scaled back to -1..1. */
    private static double amplitude(int octaves) {
        double total = 3.0; // the first octave is three sines
        for (int k = 1; k < Math.min(MAX_OCTAVES, Math.max(1, octaves)); k++) {
            total += 1.0 / (1 << k);
        }
        return total;
    }

    /** Length of the longest command in a script, for checking against the chat cap. */
    public static int longest(List<String> commands) {
        int max = 0;
        for (String c : commands) {
            max = Math.max(max, c.length());
        }
        return max;
    }

    /**
     * Splits a shape expression into {leading statements, final value}.
     *
     * Assignments to {@code data} are dropped: they would fight the metadata of
     * the block each pass places.
     */
    static String[] split(String expr) {
        String[] parts = expr.split(";");
        List<String> kept = new ArrayList<String>();
        for (String part : parts) {
            String stmt = part.trim();
            if (stmt.isEmpty()) continue;
            if (isAssignmentTo(stmt, "data")) continue;
            kept.add(stmt);
        }
        if (kept.isEmpty()) return new String[] { "", "1" };

        int last = kept.size() - 1;
        String tail = kept.get(last);
        if (tail.startsWith("return ")) tail = tail.substring(7)
            .trim();

        StringBuilder lead = new StringBuilder();
        for (int i = 0; i < last; i++) {
            lead.append(kept.get(i))
                .append(';');
        }
        return new String[] { lead.toString(), tail };
    }

    private static boolean isAssignmentTo(String stmt, String name) {
        if (!stmt.startsWith(name)) return false;
        String rest = stmt.substring(name.length())
            .trim();
        return rest.startsWith("=") && !rest.startsWith("==");
    }

    /**
     * A frequency to about three significant figures. These are arbitrary numbers
     * picked to look irregular, so trailing digits buy nothing but length.
     */
    private static String freq(double v) {
        double a = Math.abs(v);
        return trim(String.format(Locale.ROOT, a >= 10 ? "%.1f" : (a >= 1 ? "%.2f" : "%.3f"), v));
    }

    /** Three decimals, no leading zero: -0.3333 becomes "-.333". */
    private static String f(double v) {
        return trim(String.format(Locale.ROOT, "%.3f", v));
    }

    private static String trim(String s) {
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        if (s.startsWith("0.")) s = s.substring(1);
        else if (s.startsWith("-0.")) s = "-" + s.substring(2);
        return s.isEmpty() ? "0" : s;
    }
}
