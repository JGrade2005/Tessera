package com.gradientcraft.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

/**
 * The brush behind {@code /smartsmooth brush &lt;shape&gt; &lt;radius&gt; [iterations]}.
 *
 * Unlike {@link VerticalSmoothCommand} (which smooths one axis you choose,
 * over your whole WorldEdit selection), this brush auto-detects the right
 * axis and direction for the specific spot you click, using
 * {@link VerticalSmoothAlgorithm#pickAxis}: it checks, in the region around
 * the click, whether solid-vs-air is lopsided along X, Y, or Z, and which
 * side is solid. A floor picks Y facing up (ordinary //smooth behavior). A
 * ceiling picks Y facing down, so it smooths the underside without eating
 * into the solid mass above it. A wall picks X or Z instead of Y, so it
 * smooths across the wall's face without flattening it top-to-bottom.
 *
 * Because {@link VerticalSmoothAlgorithm#applyColumnChanges} only ever
 * touches cells strictly between a column's old and new surface position -
 * never anything beyond the region that was actually scanned - a flat solid
 * mass behind a rough surface (e.g. the bulk of a ceiling above its bumpy
 * underside) is left alone by construction, not just by convention.
 *
 * COMPILE CAVEAT: same as {@link VerticalSmoothCommand} - this uses
 * WorldEdit's public {@code Brush} interface and {@code EditSession} based on
 * documented API shapes, but I don't have the real WorldEdit jar to compile
 * against here. If the build complains about this file specifically, the
 * most likely spot is the exact {@code Brush.build(...)} method signature for
 * your WorldEdit 7.2.10 build.
 */
public class SmartSmoothBrush implements Brush {

    public enum Shape { SPHERE, CUBE, CYLINDER }

    private final Shape shape;
    private final int iterations;

    public SmartSmoothBrush(Shape shape, int iterations) {
        this.shape = shape;
        this.iterations = Math.max(1, iterations);
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size)
            throws MaxChangedBlocksException {
        int r = Math.max(1, (int) Math.ceil(size));
        int cx = position.getX(), cy = position.getY(), cz = position.getZ();
        int minX = cx - r, maxX = cx + r;
        // Clamp to 1.18.2's build height range so a brush near the world
        // floor/ceiling doesn't scan out of bounds.
        int minY = Math.max(0, cy - r), maxY = Math.min(255, cy + r);
        int minZ = cz - r, maxZ = cz + r;

        VerticalSmoothAlgorithm.SolidityGrid grid = (x, y, z) -> {
            BlockState bs = editSession.getBlock(BlockVector3.at(x, y, z));
            return !bs.getBlockType().getMaterial().isAir();
        };

        VerticalSmoothAlgorithm.AxisChoice choice =
                VerticalSmoothAlgorithm.pickAxis(grid, minX, maxX, minY, maxY, minZ, maxZ);

        int dMin, dMax, uMin, uMax, vMin, vMax, centerU, centerV, centerD;
        switch (choice.axis) {
            case 0 -> { // depth = X
                dMin = minX; dMax = maxX; uMin = minY; uMax = maxY; vMin = minZ; vMax = maxZ;
                centerU = cy; centerV = cz; centerD = cx;
            }
            case 2 -> { // depth = Z
                dMin = minZ; dMax = maxZ; uMin = minX; uMax = maxX; vMin = minY; vMax = maxY;
                centerU = cx; centerV = cy; centerD = cz;
            }
            default -> { // depth = Y
                dMin = minY; dMax = maxY; uMin = minX; uMax = maxX; vMin = minZ; vMax = maxZ;
                centerU = cx; centerV = cz; centerD = cy;
            }
        }

        VerticalSmoothAlgorithm.ColumnMask columnMask;
        VerticalSmoothAlgorithm.CellMask cellMask;
        switch (shape) {
            case CUBE -> {
                columnMask = VerticalSmoothAlgorithm.allColumns();
                cellMask = VerticalSmoothAlgorithm.allCells();
            }
            case SPHERE -> {
                columnMask = VerticalSmoothAlgorithm.circleColumns(centerU, centerV, size);
                cellMask = VerticalSmoothAlgorithm.sphereCells(centerU, centerV, centerD, size);
            }
            default -> { // CYLINDER: round footprint, unrestricted along the depth axis
                columnMask = VerticalSmoothAlgorithm.circleColumns(centerU, centerV, size);
                cellMask = VerticalSmoothAlgorithm.allCells();
            }
        }

        try {
            VerticalSmoothAlgorithm.applyColumnChanges(
                    WorldEditBlockEditor.of(editSession), grid, choice.axis,
                    dMin, dMax, uMin, uMax, vMin, vMax, choice.scanFromMax, iterations,
                    columnMask, cellMask);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MaxChangedBlocksException mce) throw mce;
            throw e;
        }
    }
}
