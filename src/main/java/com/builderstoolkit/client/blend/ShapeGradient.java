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
    private static final String NOISE = "(sin(x*7.1)*0.5+sin(z*5.3)*0.3+sin(y*9.7)*0.2)";

    private ShapeGradient() {}

    /**
     * @param shapeExpr the shape, as the Generate tab would emit it
     * @param blockIds  numeric WorldEdit ids, one per gradient step
     * @param dither    0..1, how far band edges are allowed to wander
     * @return one command per gradient step, to run in order. Commands only, with
     *         no comment lines: these get sent to chat, where a leading // would
     *         itself be read as a command.
     */
    public static List<String> build(String shapeExpr, List<String> blockIds, Axis axis, double dither,
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
        String shape = asAssignment(shapeExpr, "sh");

        for (int i = 0; i < n; i++) {
            double from = lo + span * i / n;
            double to = lo + span * (i + 1) / n;

            StringBuilder expr = new StringBuilder(shape);
            expr.append(";g=")
                .append(axis.expr);
            if (dither > 0) {
                expr.append(";g=g+")
                    .append(f(dither * span / n))
                    .append('*')
                    .append(NOISE);
            }
            expr.append(";return sh");
            // Open-ended at both extremes, so dithered values never fall through a gap.
            if (i > 0) expr.append("&&g>=")
                .append(f(from));
            if (i < n - 1) expr.append("&&g<")
                .append(f(to));

            out.add("//g " + (hollow ? "-h " : "") + blockIds.get(i) + " " + expr);
        }
        return out;
    }

    /**
     * Rewrites a shape expression so its result lands in {@code var}, letting the
     * band condition be ANDed onto it. Assignments to {@code data} are dropped:
     * they would fight the metadata of the block each pass places.
     */
    static String asAssignment(String expr, String var) {
        String[] parts = expr.split(";");
        List<String> kept = new ArrayList<String>();
        for (String part : parts) {
            String stmt = part.trim();
            if (stmt.isEmpty()) continue;
            if (isAssignmentTo(stmt, "data")) continue;
            kept.add(stmt);
        }
        if (kept.isEmpty()) return var + "=(1)";

        int last = kept.size() - 1;
        String tail = kept.get(last);
        if (tail.startsWith("return ")) tail = tail.substring(7)
            .trim();
        kept.set(last, var + "=(" + tail + ")");

        StringBuilder sb = new StringBuilder();
        for (String stmt : kept) {
            if (sb.length() > 0) sb.append(';');
            sb.append(stmt);
        }
        return sb.toString();
    }

    private static boolean isAssignmentTo(String stmt, String name) {
        if (!stmt.startsWith(name)) return false;
        String rest = stmt.substring(name.length())
            .trim();
        return rest.startsWith("=") && !rest.startsWith("==");
    }

    private static String f(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s.isEmpty() ? "0" : s;
    }
}
