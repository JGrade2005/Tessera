package com.gradientcraft.client.generate;

import java.util.Locale;

/**
 * Wraps any shape expression so it is evaluated in a rotated coordinate frame,
 * giving X/Y/Z rotation to every shape. It captures the original coordinates
 * and reassigns x, y, z to the rotated values before the original expression
 * runs (the engine evaluates statements in order), so the rest of the
 * expression - including multi-statement / data= / return forms - sees the
 * rotated coordinates.
 *
 * Rotation order is Rz * Ry * Rx. None of the built-in shapes use the temp
 * variable names ox/oy/oz, so there is no collision.
 */
public final class Rotate3D {

    private Rotate3D() {}

    public static String wrap(String expr, double rxDeg, double ryDeg, double rzDeg) {
        if (rxDeg == 0 && ryDeg == 0 && rzDeg == 0) return expr;
        double[] m = matrix(rxDeg, ryDeg, rzDeg);
        String pre = "ox=x;oy=y;oz=z;"
                + "x=((" + f(m[0]) + ")*ox+(" + f(m[1]) + ")*oy+(" + f(m[2]) + ")*oz);"
                + "y=((" + f(m[3]) + ")*ox+(" + f(m[4]) + ")*oy+(" + f(m[5]) + ")*oz);"
                + "z=((" + f(m[6]) + ")*ox+(" + f(m[7]) + ")*oy+(" + f(m[8]) + ")*oz);";
        return pre + expr;
    }

    /** Composed rotation matrix R = Rz * Ry * Rx (row-major, 9 entries). */
    static double[] matrix(double rxDeg, double ryDeg, double rzDeg) {
        double ax = Math.toRadians(rxDeg), ay = Math.toRadians(ryDeg), az = Math.toRadians(rzDeg);
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double cy = Math.cos(ay), sy = Math.sin(ay);
        double cz = Math.cos(az), sz = Math.sin(az);
        return new double[] {
            cz * cy,                 cz * sy * sx - sz * cx,  cz * sy * cx + sz * sx,
            sz * cy,                 sz * sy * sx + cz * cx,  sz * sy * cx - cz * sx,
            -sy,                     cy * sx,                 cy * cx
        };
    }

    private static String f(double v) {
        double a = Math.abs(v);
        String s = (a != 0 && a < 0.001) ? String.format(Locale.ROOT, "%.6f", v)
                 : String.format(Locale.ROOT, "%.5f", v);
        if (s.contains(".")) { s = s.replaceAll("0+$", ""); s = s.replaceAll("\\.$", ""); }
        return s;
    }
}
