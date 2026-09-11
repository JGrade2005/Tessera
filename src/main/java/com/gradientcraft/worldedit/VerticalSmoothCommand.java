package com.gradientcraft.worldedit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /verticalsmooth [iterations] [-x|-y|-z] [-r]}
 *
 * A generalized version of WorldEdit's {@code //smooth} that can smooth a
 * VERTICAL face (a cliff, tower, or wall) instead of only a horizontal one.
 *
 * {@code //smooth} always measures height along Y, so it collapses any
 * vertical structure toward one flat plane. This command lets you pick which
 * axis is the "thickness" direction instead - X or Z for a wall that runs
 * along the other horizontal axis and up/down through Y - so the smoothing
 * runs across the wall's face without flattening its verticality.
 *
 * Usage:
 *   /verticalsmooth              - 1 pass, auto-picks the thinner horizontal
 *                                  axis of your selection as the depth axis
 *   /verticalsmooth 3            - 3 smoothing passes
 *   /verticalsmooth 2 -x         - force X as the depth axis (wall runs along Z/Y)
 *   /verticalsmooth 2 -z -r      - force Z as the depth axis, reversed facing
 *
 * IMPORTANT ARCHITECTURE NOTE (read this before shipping):
 * This is a genuinely new command that WorldEdit itself has never heard of,
 * so it cannot be typed with WorldEdit's own "//" prefix the way //smooth can -
 * it is registered as an ordinary Forge/Brigadier command using a single
 * slash. It reads your current WorldEdit selection and edits blocks through
 * WorldEdit's own EditSession, so //undo still works on it and it respects
 * your current WorldEdit world - it just isn't literally inside WorldEdit's
 * "//" namespace. Hooking directly into WorldEdit's internal Piston command
 * registry is possible but far less stable to get right without compiling
 * against the exact WorldEdit jar, so this command takes the safer path.
 *
 * SECOND IMPORTANT NOTE: unlike the rest of GradientCraft (which only ever
 * builds a //g string for you to paste, entirely client-side), this command
 * performs the edit itself, which is inherently server-side work (WorldEdit
 * edits blocks through the server / integrated server). In singleplayer that
 * is automatic - the integrated server loads the same mods as the client.
 * On a real multiplayer/dedicated server, whoever runs the server needs
 * GradientCraft (or at least this command) and WorldEdit both installed
 * server-side for /verticalsmooth to exist there at all.
 *
 * COMPILE CAVEAT: this file uses the WorldEdit Forge integration classes
 * (worldedit-forge-mc1.18.2, notably {@code ForgeAdapter}) based on
 * documented WorldEdit API concepts (EditSession, LocalSession, Region,
 * BlockVector3, BlockState) cross-checked against WorldEdit's own published
 * javadoc/API docs. I do not have the actual WorldEdit jar available to
 * compile against in this environment, so - unlike the rest of this project,
 * which is compiled and/or brace-checked - I cannot guarantee this file
 * compiles as-is against your exact WorldEdit build. The likeliest trouble
 * spots if it doesn't are: the exact static method names on ForgeAdapter
 * (adapting a ServerPlayer/ServerLevel to WorldEdit's Actor/World), and
 * whether BlockState needs converting via ForgeAdapter as well when reading
 * back from the world. Please build once and send me any compiler errors -
 * they will point at exactly the right spot to fix.
 */
public final class VerticalSmoothCommand {

    private VerticalSmoothCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("verticalsmooth")
            .requires(src -> src.hasPermission(2) || src.getEntity() instanceof ServerPlayer)
            .executes(ctx -> run(ctx, 1, null, false))
            .then(Commands.argument("iterations", IntegerArgumentType.integer(1, 20))
                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "iterations"), null, false))
                .then(Commands.literal("-x").executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "iterations"), 0, false))
                    .then(Commands.literal("-r").executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "iterations"), 0, true))))
                .then(Commands.literal("-y").executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "iterations"), 1, false))
                    .then(Commands.literal("-r").executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "iterations"), 1, true))))
                .then(Commands.literal("-z").executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "iterations"), 2, false))
                    .then(Commands.literal("-r").executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "iterations"), 2, true))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int iterations, Integer forcedAxis, boolean reverse) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer mcPlayer)) {
            src.sendFailure(new TextComponent("Only a player can run /verticalsmooth."));
            return 0;
        }

        try {
            Actor actor = ForgeAdapter.adaptPlayer(mcPlayer);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
            World weWorld = session.getSelectionWorld();
            if (weWorld == null) {
                src.sendFailure(new TextComponent("Please make a WorldEdit selection first."));
                return 0;
            }

            Region region;
            try {
                region = session.getSelection(weWorld);
            } catch (IncompleteRegionException e) {
                src.sendFailure(new TextComponent("Please make a WorldEdit selection first."));
                return 0;
            }

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            int xSize = max.getX() - min.getX() + 1;
            int zSize = max.getZ() - min.getZ() + 1;

            // Auto-pick the depth axis: whichever horizontal extent is smaller,
            // since a vertical wall's selection is usually thin in the direction
            // it faces. Explicit -x/-y/-z always wins.
            int axis = forcedAxis != null ? forcedAxis : (xSize <= zSize ? 0 : 2);

            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld).actor(actor).build()) {

                int dMin, dMax, uMin, uMax, vMin, vMax;
                switch (axis) {
                    case 0 -> { dMin = min.getX(); dMax = max.getX(); uMin = min.getY(); uMax = max.getY(); vMin = min.getZ(); vMax = max.getZ(); }
                    case 2 -> { dMin = min.getZ(); dMax = max.getZ(); uMin = min.getX(); uMax = max.getX(); vMin = min.getY(); vMax = max.getY(); }
                    default -> { dMin = min.getY(); dMax = max.getY(); uMin = min.getX(); uMax = max.getX(); vMin = min.getZ(); vMax = max.getZ(); }
                }

                VerticalSmoothAlgorithm.SolidityGrid grid = (x, y, z) -> {
                    BlockState bs = editSession.getBlock(BlockVector3.at(x, y, z));
                    return !bs.getBlockType().getMaterial().isAir();
                };

                boolean scanFromMax = !reverse; // default: surface faces the +axis direction
                int changed = VerticalSmoothAlgorithm.applyColumnChanges(
                        WorldEditBlockEditor.of(editSession), grid, axis,
                        dMin, dMax, uMin, uMax, vMin, vMax, scanFromMax, iterations,
                        VerticalSmoothAlgorithm.allColumns());

                session.remember(editSession);
                actor.printInfo(com.sk89q.worldedit.util.formatting.text.TextComponent.of(
                        "verticalsmooth: smoothed " + changed + " columns along the "
                                + (axis == 0 ? "X" : axis == 2 ? "Z" : "Y") + " axis."));
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(new TextComponent("verticalsmooth failed: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
