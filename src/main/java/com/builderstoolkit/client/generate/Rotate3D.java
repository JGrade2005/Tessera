package com.builderstoolkit.client.generate;

import java.util.Locale;

/**
 * Wraps a shape expression so it evaluates in a rotated frame, giving X/Y/Z
 * rotation to every shape. Statements run in order, so reassigning x, y and z
 * up front means the rest of the expression sees the rotated coordinates.
 * Rotation order is Rz * Ry * Rx; no built-in shape uses the names ox/oy/oz.
 *
 * The preamble is written as short as it can be, because Minecraft caps a chat
 * message at 100 characters (256 with GTNH's Hodgepodge). Every gradient pass
 * carries a copy of it, so identity rows, zero terms and unit coefficients are
 * all dropped rather than emitted and multiplied by one.
 */
public final class Rotate3D {

    private static final String[] AXES = { "x", "y", "z" };

    private Rotate3D() {}

    public static String wrap(String expr, double rxDeg, double ryDeg, double rzDeg) {
        return preamble(rxDeg, ryDeg, rzDeg) + expr;
    }

    /** The coordinate reassignments alone, ending in ';', or "" for no rotation. */
    public static String preamble(double rxDeg, double ryDeg, double rzDeg) {
        if (rxDeg == 0 && ryDeg == 0 && rzDeg == 0) return "";
        double[] m = matrix(rxDeg, ryDeg, rzDeg);

        // Rows that come out as the identity change nothing and can be left out.
        boolean[] writes = new boolean[3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                double want = row == col ? 1 : 0;
                if (Math.abs(m[row * 3 + col] - want) > 1e-9) writes[row] = true;
            }
        }

        // A source axis needs saving only if some row reads it after it is overwritten.
        boolean[] saves = new boolean[3];
        for (int row = 0; row < 3; row++) {
            if (!writes[row]) continue;
            for (int col = 0; col < 3; col++) {
                if (!zero(m[row * 3 + col]) && writes[col]) saves[col] = true;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (saves[i]) sb.append('o')
                .append(AXES[i])
                .append('=')
                .append(AXES[i])
                .append(';');
        }
        for (int row = 0; row < 3; row++) {
            if (!writes[row]) continue;
            sb.append(AXES[row])
                .append('=');
            boolean first = true;
            for (int col = 0; col < 3; col++) {
                double c = m[row * 3 + col];
                if (zero(c)) continue;
                String var = (saves[col] ? "o" : "") + AXES[col];
                sb.append(term(c, var, first));
                first = false;
            }
            if (first) sb.append('0'); // every term dropped out: the axis collapses
            sb.append(';');
        }
        return sb.toString();
    }

    /** One signed term, e.g. "-.7071*ox", with the leading '+' omitted on the first. */
    private static String term(double coefficient, String var, boolean first) {
        StringBuilder sb = new StringBuilder();
        boolean negative = coefficient < 0;
        double magnitude = Math.abs(coefficient);
        if (negative) sb.append('-');
        else if (!first) sb.append('+');

        if (Math.abs(magnitude - 1) > 1e-9) sb.append(f(magnitude))
            .append('*');
        sb.append(var);
        return sb.toString();
    }

    private static boolean zero(double v) {
        return Math.abs(v) < 1e-9;
    }

    /** Composed rotation matrix R = Rz * Ry * Rx (row-major, 9 entries). */
    static double[] matrix(double rxDeg, double ryDeg, double rzDeg) {
        double ax = Math.toRadians(rxDeg), ay = Math.toRadians(ryDeg), az = Math.toRadians(rzDeg);
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double cy = Math.cos(ay), sy = Math.sin(ay);
        double cz = Math.cos(az), sz = Math.sin(az);
        return new double[] { cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx, sz * cy, sz * sy * sx + cz * cx,
            sz * sy * cx - cz * sx, -sy, cy * sx, cy * cx };
    }

    /**
     * Three decimals, no leading zero: 0.7071 becomes ".707". Over a coordinate
     * range of +-1 that is a 0.002 error, far below one block.
     */
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
