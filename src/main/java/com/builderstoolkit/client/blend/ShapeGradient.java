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
 * or 256 with GTNH's Hodgepodge, and each pass has to carry the whole shape and
 * its rotation. That is why the output is written so tersely.
 */
public final class ShapeGradient {

    /** Direction the gradient runs across the shape. */
    public enum Axis {

        Y("y"),
        X("x"),
        Z("z"),
        /** Distance from the centre: fades outward, which suits spheres and tori. */
        RADIAL("sqrt(x^2+y^2+z^2)");

        final String expr;

        Axis(String expr) {
            this.expr = expr;
        }
    }

    /**
     * Sine-sum stand-in for noise. The expression language has no noise function,
     * so this mixes three incommensurate frequencies to break up band edges.
     */
    private static final String NOISE = "(sin(x*7)+sin(z*5)+sin(y*9))";

    private ShapeGradient() {}

    /**
     * @param shapeExpr the shape, unrotated, as the Generate tab would emit it
     * @param rotation  the coordinate reassignments from {@link com.builderstoolkit.client.generate.Rotate3D},
     *                  or "" for none
     * @param blockIds  numeric WorldEdit ids, one per gradient step
     * @param dither    0..1, how far band edges are allowed to wander
     * @return one command per gradient step, to run in order. Commands only, with
     *         no comment lines: these get sent to chat, where a leading // would
     *         itself be read as a command.
     */
    public static List<String> build(String shapeExpr, String rotation, List<String> blockIds, Axis axis, double dither,
        boolean hollow) {

        List<String> out = new ArrayList<String>();
        int n = blockIds.size();
        if (n == 0 || shapeExpr == null
            || shapeExpr.trim()
                .isEmpty())
            return out;

        boolean radial = axis == Axis.RADIAL;
        double lo = radial ? 0.0 : -1.0;
        double span = radial ? 1.0 : 2.0;

        // The gradient axis is read before the rotation runs, so a vertical fade
        // stays vertical however the shape is spun - and so a shape that moves x
        // or z itself, as Spiral Stair does, cannot drag the gradient with it.
        StringBuilder head = new StringBuilder("g=").append(axis.expr);
        if (dither > 0) head.append('+')
            .append(f(dither * span / n / 3)) // the /3 that normalises NOISE, folded in
            .append('*')
            .append(NOISE);
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

    /** Three decimals, no leading zero: -0.3333 becomes "-.333". */
    private static String f(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        if (s.startsWith("0.")) s = s.substring(1);
        else if (s.startsWith("-0.")) s = "-" + s.substring(2);
        return s.isEmpty() ? "0" : s;
    }
}
