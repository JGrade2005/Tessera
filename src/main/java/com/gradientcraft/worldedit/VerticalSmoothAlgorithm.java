package com.gradientcraft.worldedit;

/**
 * The math behind {@code //verticalsmooth}.
 *
 * WorldEdit's own {@code //smooth} always measures "height" along the world's
 * Y axis: for every (x,z) column it finds the top of the solid ground and
 * averages that height with its neighbors. That is exactly why it flattens
 * vertical faces - a cliff or wall has the same (x,z) footprint at every
 * height, so the heightmap has no information about it and the smoothing
 * collapses it toward one flat plane.
 *
 * This class does the same averaging, but generalizes which world axis plays
 * the role of "height" (here called the depth axis). Pick X or Z as the depth
 * axis (i.e. the direction the wall is thin in / faces toward) and the
 * "heightmap" is built over the remaining two axes (one of which is now Y),
 * so the smoothing runs along the wall's face instead of collapsing it.
 *
 * This class is pure math: it has no dependency on Minecraft or WorldEdit
 * types, so it can be unit tested directly. It operates on a caller-supplied
 * 2D grid of surface positions ("depth map") plus a solidity lookup used to
 * build that grid from a 3D volume.
 */
public final class VerticalSmoothAlgorithm {

    private VerticalSmoothAlgorithm() {}

    /** Sentinel depth value meaning "no solid block found along this line". */
    public static final int NO_SURFACE = Integer.MIN_VALUE;

    /** Solidity lookup over an axis-aligned integer volume. */
    public interface SolidityGrid {
        boolean isSolid(int x, int y, int z);
    }

    /**
     * Builds a depth map: for every (u, v) pair, scans along the depth axis
     * and records the index of the outer-most solid cell.
     *
     * @param grid       solidity lookup
     * @param depthAxis  0 = X, 1 = Y, 2 = Z; the axis the "thickness" runs along
     * @param dMin, dMax inclusive bounds along the depth axis
     * @param uMin, uMax inclusive bounds along the first remaining axis
     * @param vMin, vMax inclusive bounds along the second remaining axis
     * @param scanFromMax true = scan from dMax down to dMin (surface faces +axis,
     *                    matching WorldEdit's own "scan down from above" convention);
     *                    false = scan from dMin up to dMax (surface faces -axis)
     * @return depth[u-uMin][v-vMin] = absolute depth-axis coordinate of the
     *         outer solid cell, or {@link #NO_SURFACE} if the whole line is empty
     */
    public static int[][] buildDepthMap(SolidityGrid grid, int depthAxis,
                                         int dMin, int dMax, int uMin, int uMax, int vMin, int vMax,
                                         boolean scanFromMax) {
        int uSize = uMax - uMin + 1, vSize = vMax - vMin + 1;
        int[][] depth = new int[uSize][vSize];
        for (int ui = 0; ui < uSize; ui++) {
            for (int vi = 0; vi < vSize; vi++) {
                int u = uMin + ui, v = vMin + vi;
                int found = NO_SURFACE;
                if (scanFromMax) {
                    for (int d = dMax; d >= dMin; d--) {
                        if (isSolidAt(grid, depthAxis, d, u, v)) { found = d; break; }
                    }
                } else {
                    for (int d = dMin; d <= dMax; d++) {
                        if (isSolidAt(grid, depthAxis, d, u, v)) { found = d; break; }
                    }
                }
                depth[ui][vi] = found;
            }
        }
        return depth;
    }

    private static boolean isSolidAt(SolidityGrid grid, int depthAxis, int d, int u, int v) {
        switch (depthAxis) {
            case 0: return grid.isSolid(d, u, v); // depth=X, u=Y, v=Z
            case 2: return grid.isSolid(u, v, d); // depth=Z, u=X, v=Y
            default: return grid.isSolid(u, d, v); // depth=Y, u=X, v=Z (same as normal //smooth)
        }
    }

    /** Maps a (u,v,d) triple back to (x,y,z) for the given depth axis. */
    public static int[] toXYZ(int depthAxis, int d, int u, int v) {
        switch (depthAxis) {
            case 0: return new int[] { d, u, v };
            case 2: return new int[] { u, v, d };
            default: return new int[] { u, d, v };
        }
    }

    /** Result of auto-detecting which way a local surface faces. */
    public static final class AxisChoice {
        public final int axis;         // 0=X, 1=Y, 2=Z
        public final boolean scanFromMax;
        public final double confidence; // 0..1, how lopsided the winning axis was
        AxisChoice(int axis, boolean scanFromMax, double confidence) {
            this.axis = axis; this.scanFromMax = scanFromMax; this.confidence = confidence;
        }
    }

    /**
     * Auto-detects which axis is the "depth" direction and which way the
     * surface faces, by checking - for each axis - how lopsided solid-vs-air
     * is between the low half and the high half of the region.
     *
     * A normal floor: solid concentrated in the low-Y half, air in the high-Y
     * half -> picks axis=Y, scanFromMax=true (matches WorldEdit's own //smooth).
     * A ceiling: solid concentrated in the high-Y half instead -> axis=Y,
     * scanFromMax=false (scan upward from the open room to find the
     * ceiling's underside, instead of digging into the solid mass above it).
     * A wall: the lopsidedness shows up on X or Z instead, with Y roughly
     * balanced (solid runs the same at every height) -> that axis wins.
     *
     * The axis with the single largest lopsidedness wins, since that is the
     * one behaving like a genuine one-sided surface rather than a direction
     * that just runs along the surface.
     */
    public static AxisChoice pickAxis(SolidityGrid grid,
                                       int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        double impX = axisImbalance(grid, 0, minX, maxX, minY, maxY, minZ, maxZ);
        double impY = axisImbalance(grid, 1, minX, maxX, minY, maxY, minZ, maxZ);
        double impZ = axisImbalance(grid, 2, minX, maxX, minY, maxY, minZ, maxZ);
        double aX = Math.abs(impX), aY = Math.abs(impY), aZ = Math.abs(impZ);
        if (aX >= aY && aX >= aZ) return new AxisChoice(0, impX > 0, aX);
        if (aZ >= aY) return new AxisChoice(2, impZ > 0, aZ);
        return new AxisChoice(1, impY > 0, aY);
    }

    /**
     * Signed lopsidedness of solid-vs-air between the low and high halves of
     * the region along one axis: (solid fraction of low half) - (solid
     * fraction of high half). Positive means solid concentrated low (surface
     * faces the + direction, i.e. scan from max down); negative means solid
     * concentrated high (surface faces the - direction, i.e. scan from min up).
     */
    private static double axisImbalance(SolidityGrid grid, int axis,
                                         int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        int dMin, dMax;
        switch (axis) {
            case 0 -> { dMin = minX; dMax = maxX; }
            case 2 -> { dMin = minZ; dMax = maxZ; }
            default -> { dMin = minY; dMax = maxY; }
        }
        int mid = (dMin + dMax) / 2;
        long solidLow = 0, totalLow = 0, solidHigh = 0, totalHigh = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int d = axis == 0 ? x : axis == 2 ? z : y;
                    boolean solid = grid.isSolid(x, y, z);
                    if (d <= mid) { totalLow++; if (solid) solidLow++; }
                    else { totalHigh++; if (solid) solidHigh++; }
                }
            }
        }
        double fracLow = totalLow == 0 ? 0 : (double) solidLow / totalLow;
        double fracHigh = totalHigh == 0 ? 0 : (double) solidHigh / totalHigh;
        return fracLow - fracHigh;
    }

    /** Restricts which (u,v) columns get touched - used to carve out brush shapes. */
    public interface ColumnMask {
        boolean include(int u, int v);
    }

    /** A column mask that includes everything (used for CUBE-shaped brushes). */
    public static ColumnMask allColumns() { return (u, v) -> true; }

    /** A circular column mask around (centerU, centerV) with the given radius (CYLINDER-shaped brushes). */
    public static ColumnMask circleColumns(double centerU, double centerV, double radius) {
        double r2 = radius * radius;
        return (u, v) -> {
            double du = u - centerU, dv = v - centerV;
            return du * du + dv * dv <= r2;
        };
    }

    /** Restricts individual (u,v,d) cells - used for a true SPHERE brush shape, unlike ColumnMask which only restricts (u,v). */
    public interface CellMask {
        boolean include(int u, int v, int d);
    }

    /** A cell mask that includes every cell (used when a shape doesn't need per-cell restriction). */
    public static CellMask allCells() { return (u, v, d) -> true; }

    /** A true 3D sphere mask around (centerU, centerV, centerD) - for SPHERE-shaped brushes. */
    public static CellMask sphereCells(double centerU, double centerV, double centerD, double radius) {
        double r2 = radius * radius;
        return (u, v, d) -> {
            double du = u - centerU, dv = v - centerV, dd = d - centerD;
            return du * du + dv * dv + dd * dd <= r2;
        };
    }

    /**
     * Generic read/write access to whatever block-type representation the
     * caller uses (a real {@code BlockState} in the WorldEdit glue, or a
     * plain boxed value in tests). Kept generic so the apply logic below is
     * fully unit-testable without any WorldEdit or Minecraft types.
     */
    public interface Editor<B> {
        B getBlock(int x, int y, int z);
        void setBlock(int x, int y, int z, B block);
        B air();
    }

    /**
     * Builds the depth map, smooths it, and writes the resulting changes back
     * through {@code editor} - shared by both the selection-based command and
     * the brush, so there is exactly one tested code path for "how a depth
     * change becomes actual block edits".
     *
     * Only columns where {@code mask.include(u, v)} is true are written,
     * which is how brush shapes (sphere/cube/cylinder) carve out their
     * footprint. A column is only ever grown using the block that was
     * already at its own surface (no foreign material is introduced), and
     * only cells strictly between the old and new surface position are
     * touched - never anything beyond the originally-scanned depth range -
     * which is what keeps a solid mass behind a rough surface (e.g. the bulk
     * of a ceiling above its bumpy underside) untouched.
     *
     * @return number of columns that were changed
     */
    public static <B> int applyColumnChanges(Editor<B> editor, SolidityGrid grid, int axis,
                                              int dMin, int dMax, int uMin, int uMax, int vMin, int vMax,
                                              boolean scanFromMax, int iterations, ColumnMask mask) {
        return applyColumnChanges(editor, grid, axis, dMin, dMax, uMin, uMax, vMin, vMax,
                scanFromMax, iterations, mask, allCells());
    }

    /** Same as the 12-arg overload, but also restricts individual cells via {@code cellMask} (for true-sphere brushes). */
    public static <B> int applyColumnChanges(Editor<B> editor, SolidityGrid grid, int axis,
                                              int dMin, int dMax, int uMin, int uMax, int vMin, int vMax,
                                              boolean scanFromMax, int iterations, ColumnMask mask, CellMask cellMask) {
        int[][] before = buildDepthMap(grid, axis, dMin, dMax, uMin, uMax, vMin, vMax, scanFromMax);
        int[][] after = smooth(before, iterations);
        int changed = 0;
        int uSize = uMax - uMin + 1, vSize = vMax - vMin + 1;
        for (int ui = 0; ui < uSize; ui++) {
            for (int vi = 0; vi < vSize; vi++) {
                int u = uMin + ui, v = vMin + vi;
                if (!mask.include(u, v)) continue;
                int oldD = before[ui][vi], newD = after[ui][vi];
                if (oldD == NO_SURFACE || newD == NO_SURFACE || oldD == newD) continue;

                int[] surfaceXyz = toXYZ(axis, oldD, u, v);
                B surfaceBlock = editor.getBlock(surfaceXyz[0], surfaceXyz[1], surfaceXyz[2]);

                int lo = Math.min(oldD, newD), hi = Math.max(oldD, newD);
                boolean growing = newD > oldD ? scanFromMax : !scanFromMax;
                // When growing, oldD is already correctly solid - only fill beyond it.
                // When shrinking, newD is already correctly solid - only clear beyond it.
                int skip = growing ? oldD : newD;
                boolean touchedAny = false;
                for (int d = lo; d <= hi; d++) {
                    if (d == skip) continue;
                    if (!cellMask.include(u, v, d)) continue;
                    int[] xyz = toXYZ(axis, d, u, v);
                    editor.setBlock(xyz[0], xyz[1], xyz[2], growing ? surfaceBlock : editor.air());
                    touchedAny = true;
                }
                if (touchedAny) changed++;
            }
        }
        return changed;
    }

    /**
     * One WorldEdit-style smoothing pass: each cell becomes the weighted
     * average of itself and its 8 neighbors using the kernel
     * <pre>1 2 1
     * 2 4 2
     * 1 2 1</pre>
     * (the same kernel WorldEdit's own smooth uses), skipping any neighbor
     * marked {@link #NO_SURFACE} and renormalizing by the weight actually used.
     * Cells that are themselves {@link #NO_SURFACE} are left untouched.
     */
    public static double[][] smoothPass(double[][] depth) {
        int uSize = depth.length, vSize = uSize == 0 ? 0 : depth[0].length;
        double[][] out = new double[uSize][vSize];
        int[] dx = { -1, 0, 1, -1, 0, 1, -1, 0, 1 };
        int[] dy = { -1, -1, -1, 0, 0, 0, 1, 1, 1 };
        double[] w = { 1, 2, 1, 2, 4, 2, 1, 2, 1 };
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                if (depth[u][v] == NO_SURFACE) { out[u][v] = NO_SURFACE; continue; }
                double sum = 0, weight = 0;
                for (int k = 0; k < 9; k++) {
                    int nu = u + dx[k], nv = v + dy[k];
                    if (nu < 0 || nu >= uSize || nv < 0 || nv >= vSize) continue;
                    double val = depth[nu][nv];
                    if (val == NO_SURFACE) continue;
                    sum += val * w[k];
                    weight += w[k];
                }
                out[u][v] = weight > 0 ? sum / weight : depth[u][v];
            }
        }
        return out;
    }

    /** Runs {@code iterations} smoothing passes and rounds to the nearest integer depth. */
    public static int[][] smooth(int[][] depth, int iterations) {
        int uSize = depth.length, vSize = uSize == 0 ? 0 : depth[0].length;
        double[][] cur = new double[uSize][vSize];
        for (int u = 0; u < uSize; u++)
            for (int v = 0; v < vSize; v++)
                cur[u][v] = depth[u][v];
        for (int i = 0; i < Math.max(1, iterations); i++) cur = smoothPass(cur);
        int[][] result = new int[uSize][vSize];
        for (int u = 0; u < uSize; u++)
            for (int v = 0; v < vSize; v++)
                result[u][v] = cur[u][v] == NO_SURFACE ? NO_SURFACE : (int) Math.round(cur[u][v]);
        return result;
    }
}
