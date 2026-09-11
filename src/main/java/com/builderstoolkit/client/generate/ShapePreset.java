package com.builderstoolkit.client.generate;

import java.util.Locale;
import java.util.function.Function;

/**
 * Built-in shapes for the generator. Each preset exposes a few named parameters
 * (with min/max/default) and a builder that turns the current parameter values
 * into a WorldEdit expression string. Adjusting a parameter rebuilds the
 * expression, which re-runs the preview.
 */
public final class ShapePreset {

    public static final class Param {

        public final String label;
        public final double min, max, def;
        public final boolean integer;

        public Param(String label, double min, double max, double def, boolean integer) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.def = def;
            this.integer = integer;
        }
    }

    public final String name;
    public final Param[] params;
    public final String defaultPattern;
    public final boolean defaultHollow;
    private final Function<double[], String> builder;

    private ShapePreset(String name, String pattern, boolean hollow, Function<double[], String> builder,
        Param... params) {
        this.name = name;
        this.defaultPattern = pattern;
        this.defaultHollow = hollow;
        this.builder = builder;
        this.params = params;
    }

    public String build(double[] p) {
        return builder.apply(p);
    }

    public double[] defaults() {
        double[] d = new double[params.length];
        for (int i = 0; i < params.length; i++) d[i] = params[i].def;
        return d;
    }

    /** Format a double compactly and locale-independently, keeping small values precise. */
    private static String f(double v) {
        double a = Math.abs(v);
        String s;
        if (a != 0 && a < 0.001) s = String.format(Locale.ROOT, "%.6f", v);
        else if (a < 0.1) s = String.format(Locale.ROOT, "%.5f", v);
        else s = String.format(Locale.ROOT, "%.3f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }

    /**
     * Torus of major radius R and minor radius rr, tilted rotDeg about X. The
     * rotation is baked into the coordinates so the result stays a valid,
     * copy-pasteable //g expression.
     */
    private static String torusExpr(double R, double rr, double rotDeg, String dataPrefix) {
        double th = Math.toRadians(rotDeg);
        double c = Math.cos(th), s = Math.sin(th);
        String yr = "(y*(" + f(c) + ")-z*(" + f(s) + "))";
        String zr = "(y*(" + f(s) + ")+z*(" + f(c) + "))";
        return dataPrefix + "(" + f(R) + "-sqrt(x^2+" + yr + "^2))^2+" + zr + "^2 < " + f(rr) + "^2";
    }

    // The custom preset is identified by index == PRESETS.length - 1.
    public static final boolean isCustom(int index) {
        return index == PRESETS.length - 1;
    }

    public static final ShapePreset[] PRESETS = new ShapePreset[] {
        new ShapePreset(
            "Sphere",
            "stone",
            false,
            p -> "x^2+y^2+z^2 < " + f(p[0]) + "^2",
            new Param("radius", 0.1, 1.0, 0.8, false)),

        new ShapePreset(
            "Torus",
            "stone",
            false,
            p -> torusExpr(p[0], p[1], 0, ""),
            new Param("major R", 0.2, 0.9, 0.75, false),
            new Param("minor r", 0.05, 0.4, 0.25, false)),

        new ShapePreset(
            "Rainbow Torus",
            "wool",
            false,
            p -> torusExpr(p[0], p[1], 0, "data=(32+15/2/pi*atan2(x,y))%16; "),
            new Param("major R", 0.2, 0.9, 0.75, false),
            new Param("minor r", 0.05, 0.4, 0.25, false)),

        new ShapePreset(
            "Heart",
            "wool:14",
            false,
            p -> "(z/2)^2+x^2+(5*y/4-sqrt(abs(x)))^2 < " + f(p[0]),
            new Param("size", 0.3, 1.0, 0.6, false)),

        new ShapePreset(
            "Egg",
            "wool",
            false,
            p -> "y^2/9+x^2/6*(1/(1-0.4*y))+z^2/6*(1/(1-0.4*y)) < " + f(p[0]),
            new Param("size", 0.04, 0.15, 0.08, false)),

        new ShapePreset(
            "Sine Wave",
            "glass",
            true,
            p -> "sin(x*" + f(p[0]) + ")*" + f(p[1]) + " < y",
            new Param("frequency", 1, 12, 5, false),
            new Param("amplitude", 0.1, 1.0, 0.5, false)),

        new ShapePreset(
            "Radial Wave",
            "glass",
            true,
            p -> "cos(sqrt(x^2+z^2)*" + f(p[0]) + ")/2 < y",
            new Param("frequency", 1, 12, 5, false)),

        new ShapePreset(
            "Spiral Stair",
            "stone",
            false,
            p -> "turns=" + f(p[0])
                + ";phase=pi/44;A0="
                + f(p[2])
                + ";A1=0.0;r="
                + f(p[1])
                + ";t=y;th=2*pi*turns*t+phase;A=A0+(A1-A0)*t;"
                + "x+=sin(th)*A;z+=cos(th)*A;return x*x+z*z<r*r",
            new Param("turns", 1, 8, 4, true),
            new Param("thickness", 0.06, 0.3, 0.18, false),
            new Param("radius", 0.1, 0.6, 0.4, false)),

        new ShapePreset(
            "Double Helix",
            "stone",
            false,
            p -> "(abs(cos(atan2(x,z)+y*" + f(p[0]) + "*pi))) > max(abs(x),abs(z))",
            new Param("turns", 1, 6, 1, true)),

        new ShapePreset(
            "Flower",
            "stone",
            false,
            p -> "x^2+z^2 < (0.6+0.2*sin(" + f(p[0]) + "*atan2(x,z)))^2",
            new Param("petals", 3, 12, 6, true)),

        new ShapePreset(
            "Hyperboloid",
            "stone",
            false,
            p -> "-(z^2/12)+(y^2/4)-(x^2/12) > -" + f(p[0]),
            new Param("waist", 0.01, 0.2, 0.03, false)),

        new ShapePreset(
            "Backrooms Grid",
            "stone",
            false,
            p -> "abs(sin(" + f(p[0]) + "*y)*cos(3*x)) < 0.2",
            new Param("scale", 2, 10, 5, false)),

        // ---- WesterosCraft advanced shapes (normalized coordinates) ----

        new ShapePreset(
            "Hollow 4D Cube",
            "stone",
            false,
            p -> "((x^4+y^4+z^4-x*x-y*y-z*z)^2-0.2)^2 < " + f(0.001 * p[0]),
            new Param("thickness", 0.3, 3.0, 1.0, false)),

        new ShapePreset(
            "Tower Roof",
            "stone",
            false,
            p -> "((x*x+y*y*y*5+z*z-2.3)*(x*x+z*z-0.5))^2 < " + f(0.02 * p[0]),
            new Param("thickness", 0.3, 3.0, 1.0, false)),

        new ShapePreset("Bridge Arch", "stone", false, p -> "(y> -(x^2)+0.85)*(z*z<0.95)*(y<0.95)+(z*z>0.85)*(y>0.9)"),

        new ShapePreset(
            "Fancy Bridge",
            "stone",
            false,
            p -> "(((x^4-0.6+y)^2<0.05*x^2)+((x^2/5-y+0.75)^2<0.02))*(z*z*9<8)*(y*9<8)+(z*z*7>6)*(y*9>8)"),

        new ShapePreset(
            "Spike Star",
            "stone",
            false,
            p -> "((x^2+y^2+(z/9)^2-0.01)*((x/9)^2+y^2+z^2-0.01)*(x^2+(y/9)^2+z^2-0.01))^2 < 9^-6"),

        new ShapePreset(
            "Vase",
            "stone",
            false,
            p -> "(((x*x-y^5+z*z-1))^2<0.005)+((x^2+z^2+(y+0.99)^2*1500-0.4)^2 <0.3)"),

        new ShapePreset(
            "Tornado",
            "stone",
            false,
            p -> "(((atan2(x,z)+(y-1)*" + f(
                p[0]) + "+1/(x*x+z*z))*(atan2(x,z)+6.2832+(y-1)*" + f(p[0]) + "+1/(x*x+z*z)))^2<" + f(2.0 * p[1]) + ")",
            new Param("twist", 5, 40, 20, true),
            new Param("thickness", 0.3, 3.0, 1.0, false)),

        new ShapePreset(
            "Hollow Spiral",
            "stone",
            false,
            p -> "(((x-0.4*sin(y*" + f(p[0]) + "))^2+(z-0.4*cos(y*" + f(p[0]) + "))^2-0.04))^2 < " + f(0.00007 * p[1]),
            new Param("turns", 2, 10, 5, true),
            new Param("thickness", 0.3, 3.0, 1.0, false)),

        new ShapePreset(
            "Pumpkin",
            "stone",
            false,
            p -> "(((x^2+1.5*y^2+z^2-(0.7+0.05*cos(" + f(p[0]) + "*atan2(x,z)))))^2 < " + f(0.01 * p[1]) + ")",
            new Param("ridges", 6, 24, 16, true),
            new Param("thickness", 0.3, 3.0, 1.0, false)),

        new ShapePreset(
            "Mega Fancy Cube",
            "stained_glass",
            false,
            p -> "data=((y*y+x*x+z*z+0.2)*32%4+12); ((x^4+y^4+z^4-0.6+cos(" + f(p[0])
                + "*atan2(x,z))/32+cos("
                + f(p[0])
                + "*atan2(y,z))/32+cos("
                + f(p[0])
                + "*atan2(x,y))/32))^2 < "
                + f(0.002 * p[1]),
            new Param("ripples", 8, 48, 32, true),
            new Param("thickness", 0.3, 3.0, 1.0, false)),

        new ShapePreset(
            "Multicolor Torus",
            "log",
            false,
            p -> torusExpr(p[0], p[1], 0, "data=((y*y+x*x+z*z+0.2)*2%4+12); "),
            new Param("major R", 0.2, 0.9, 0.75, false),
            new Param("minor r", 0.05, 0.4, 0.25, false)),

        // ---- Natural / organic shapes ----
        // Built from summed sine/cosine waves (pseudo-noise), distorted spheres,
        // and stacked conditions, since the expression language has no real noise.

        new ShapePreset(
            "Mountain Peak",
            "stone",
            false,
            p -> "y < " + f(p[0])
                + " - 1.6*sqrt(x^2+z^2) + "
                + f(p[1])
                + "*sin(x*7) + "
                + f(p[1])
                + "*cos(z*8) + "
                + f(p[1] * 0.6)
                + "*sin(x*16+z*14)",
            new Param("height", 0.3, 1.2, 0.8, false),
            new Param("roughness", 0.0, 0.4, 0.2, false)),

        new ShapePreset(
            "Mountain Range",
            "stone",
            false,
            p -> "y < -0.1 + " + f(p[0])
                + "*sin(x*"
                + f(p[1])
                + ") + "
                + f(p[0] * 0.9)
                + "*cos(z*"
                + f(p[1] * 0.8)
                + ") + "
                + f(p[0] * 0.5)
                + "*sin(x*6+z*5) + 0.12*cos(x*11)",
            new Param("height", 0.1, 0.7, 0.45, false),
            new Param("ridges", 1.5, 5.0, 2.5, false)),

        new ShapePreset(
            "Rolling Hills",
            "grass",
            false,
            p -> "y < -0.35 + " + f(
                p[0]) + "*sin(x*3)*cos(z*3) + " + f(p[0] * 0.7) + "*sin(x*5+1) + " + f(p[0] * 0.5) + "*cos(z*4)",
            new Param("height", 0.15, 0.6, 0.3, false)),

        new ShapePreset(
            "Canyon",
            "sandstone",
            false,
            p -> "y < 0.3 && y < -0.6 + " + f(p[1]) + "*abs(x - " + f(p[0]) + "*sin(z*2.5))",
            new Param("winding", 0.0, 0.5, 0.35, false),
            new Param("steepness", 1.5, 4.0, 2.5, false)),

        new ShapePreset(
            "Mesa / Butte",
            "hardened_clay",
            false,
            p -> "x^2+z^2 < (" + f(p[0]) + " + " + f(p[1]) + "*sin(9*atan2(x,z)) - 0.12*y)^2 && y < 0.6",
            new Param("radius", 0.4, 0.8, 0.65, false),
            new Param("erosion", 0.0, 0.12, 0.04, false)),

        new ShapePreset(
            "Boulder",
            "stone",
            false,
            p -> "x^2+y^2+z^2 < (" + f(p[0])
                + " + "
                + f(p[1])
                + "*sin(4*atan2(x,z)) + "
                + f(p[1] * 0.7)
                + "*cos(5*y) + "
                + f(p[1] * 0.4)
                + "*sin(7*atan2(y,sqrt(x^2+z^2))))^2",
            new Param("size", 0.5, 0.85, 0.72, false),
            new Param("lumpiness", 0.0, 0.25, 0.14, false)),

        new ShapePreset(
            "Rock Spire",
            "stone",
            false,
            p -> "x^2+z^2 < (0.35 - " + f(p[0]) + "*y + 0.05*sin(y*12))^2",
            new Param("taper", 0.1, 0.3, 0.18, false)),

        new ShapePreset(
            "Cliff Overhang",
            "stone",
            false,
            p -> "y<0.7 && x < 0.1 + 0.3*sin(z*3) + " + f(p[0]) + "*max(0,y) && abs(z)<0.7",
            new Param("overhang", 0.2, 0.7, 0.45, false)),

        new ShapePreset(
            "Waterfall",
            "water",
            false,
            p -> "(abs(x)<" + f(p[0]) + " && abs(z)<0.5 && y<0.9) || (y<-0.55 && x^2+z^2<0.45)",
            new Param("width", 0.06, 0.2, 0.1, false)),

        new ShapePreset("Cascade", "water", false, p -> {
            double sh = 0.6 / p[0];
            return "y < -0.7 + floor((z+1)*" + f(p[0]) + ")*" + f(sh) + " && abs(x)<0.5";
        }, new Param("steps", 2, 6, 4, true)),

        new ShapePreset(
            "Crater",
            "stone",
            false,
            p -> "y < -0.2 + " + f(p[0]) + "*(x^2+z^2) && y < 0.25 && sqrt(x^2+z^2)<0.95",
            new Param("depth", 1.5, 3.0, 2.2, false)),

        new ShapePreset(
            "Sand Dunes",
            "sand",
            false,
            p -> "y < -0.4 + " + f(p[0]) + "*sin(x*2+z*1.5) + " + f(p[0] * 0.6) + "*cos(z*3) + 0.1*sin(x*7)",
            new Param("height", 0.2, 0.5, 0.35, false)),

        new ShapePreset(
            "Pine Tree",
            "leaves:1",
            false,
            p -> "sqrt(x^2+z^2) < 0.55*(0.5 - y/2) + " + f(p[0]) + "*sin(y*16)",
            new Param("bushiness", 0.03, 0.1, 0.05, false)),

        new ShapePreset(
            "Lollipop Tree",
            "leaves",
            false,
            p -> "(x^2+z^2 < 0.03 && y < 0.3) || (x^2+(y-0.55)^2+z^2 < (" + f(p[0])
                + " + 0.12*sin(5*atan2(x,z))*cos(3*y))^2)",
            new Param("canopy", 0.35, 0.55, 0.42, false)),

        // Custom must remain LAST (see isCustom). Builder returns a placeholder;
        // the screen substitutes the user's raw expression for this preset.
        new ShapePreset("Custom", "stone", false, p -> "x^2+y^2+z^2 < 0.8^2"), };
}
