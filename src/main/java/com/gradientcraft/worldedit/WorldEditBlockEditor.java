package com.gradientcraft.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

/**
 * Adapts a WorldEdit {@link EditSession} to {@link VerticalSmoothAlgorithm.Editor},
 * so the tested {@code applyColumnChanges} logic in the algorithm class can
 * write real blocks. Shared by both {@link VerticalSmoothCommand} (selection-based)
 * and {@link SmartSmoothBrush} (click-based), so there is exactly one place
 * that talks to WorldEdit's actual block-editing API.
 */
final class WorldEditBlockEditor implements VerticalSmoothAlgorithm.Editor<BlockState> {

    private final EditSession editSession;

    private WorldEditBlockEditor(EditSession editSession) {
        this.editSession = editSession;
    }

    static WorldEditBlockEditor of(EditSession editSession) {
        return new WorldEditBlockEditor(editSession);
    }

    @Override
    public BlockState getBlock(int x, int y, int z) {
        return editSession.getBlock(BlockVector3.at(x, y, z));
    }

    @Override
    public void setBlock(int x, int y, int z, BlockState block) {
        try {
            editSession.setBlock(BlockVector3.at(x, y, z), block);
        } catch (com.sk89q.worldedit.MaxChangedBlocksException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BlockState air() {
        return BlockTypes.AIR.getDefaultState();
    }
}
