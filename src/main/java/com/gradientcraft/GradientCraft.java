package com.gradientcraft;

import net.minecraftforge.fml.common.Mod;

/**
 * GradientCraft — a purely client-side build helper that suggests block
 * gradients between any blocks you choose.
 *
 * It registers no blocks, items, network packets, or world data, so it is
 * safe to use on multiplayer servers (the server never needs the mod).
 */
@Mod(GradientCraft.MODID)
public class GradientCraft {
    public static final String MODID = "gradientcraft";

    public GradientCraft() {
        // Nothing to register on the mod bus. All behavior is wired through
        // @Mod.EventBusSubscriber handlers gated to Dist.CLIENT.
    }
}
