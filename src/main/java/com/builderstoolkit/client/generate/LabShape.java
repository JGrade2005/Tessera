package com.builderstoolkit.client.generate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the Lab tab sliders and toggles into a WorldEdit expression: a
 * superquadric base (sphere to cube via the exponent, stretched per axis) with
 * optional twist, taper, ripple, wobble, hollow shell, invert and rainbow data.
 * Rotation is applied separately by {@link Rotate3D}.
 *
 * Sliders (s): 0 size, 1 exponent, 2-4 stretch XYZ, 5 taper, 6 twist,
 * 7 rippleFreq, 8 rippleAmp, 9 wobbleFreq, 10 wobbleAmp, 11 thickness
 * Toggles (c): 0 twist, 1 ripple, 2 wobble, 3 hollow, 4 invert, 5 rainbow
 */
public final class LabShape {

    private LabShape() {}

    public static String build(double[] s, boolean[] c) {
        double size = s[0], e = s[1], sx = s[2], sy = s[3], sz = s[4], taper = s[5], tw = s[6], rf = s[7], ra = s[8],
            wf = s[9], wa = s[10], thick = s[11];
        boolean twist = c[0], ripple = c[1], wobble = c[2], hollow = c[3], invert = c[4], rainbow = c[5];

        List<String> st = new ArrayList<>();

        if (rainbow) st.add("data=(32+15/2/pi*atan2(x,y))%16");

        // twist the cross-section with height
        if (twist) {
            st.add("xt=(x*cos(" + f(tw) + "*y)-z*sin(" + f(tw) + "*y))");
            st.add("zt=(x*sin(" + f(tw) + "*y)+z*cos(" + f(tw) + "*y))");
        } else {
            st.add("xt=x");
            st.add("zt=z");
        }
        st.add("yt=y");

        // taper narrows x,z toward the top
        st.add("tf=1-" + f(taper) + "*(yt+1)/2");

        String dx = "(" + f(size * sx) + "*tf)";
        String dy = "(" + f(size * sy) + ")";
        String dz = "(" + f(size * sz) + "*tf)";
        String exp = f(e);
        st.add("F=abs(xt/" + dx + ")^" + exp + "+abs(yt/" + dy + ")^" + exp + "+abs(zt/" + dz + ")^" + exp);

        StringBuilder surf = new StringBuilder("1");
        if (ripple) surf.append("+")
            .append(f(ra))
            .append("*sin(")
            .append(f(rf))
            .append("*atan2(xt,zt))");
        if (wobble) surf.append("+")
            .append(f(wa))
            .append("*sin(")
            .append(f(wf))
            .append("*xt)*cos(")
            .append(f(wf))
            .append("*zt)");
        st.add("S=" + surf);

        String cond;
        if (hollow) cond = "abs(F-S)<" + f(thick);
        else if (invert) cond = "F>S";
        else cond = "F<S";
        st.add("return " + cond);

        return String.join(";", st);
    }

    private static String f(double v) {
        double a = Math.abs(v);
        String s;
        if (a != 0 && a < 0.001) s = String.format(Locale.ROOT, "%.6f", v);
        else if (a < 0.1) s = String.format(Locale.ROOT, "%.5f", v);
        else s = String.format(Locale.ROOT, "%.4f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }
}
