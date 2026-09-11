package com.gradientcraft.worldedit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.world.item.ItemType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /smartsmooth brush <sphere|cube|cylinder> <radius> [iterations]}
 *
 * Binds {@link SmartSmoothBrush} to the item currently in your hand, exactly
 * like WorldEdit's own {@code //brush sphere}, {@code //brush smooth}, etc.
 * do - left-click a block afterward to apply it there. It is invoked through
 * our own command rather than nested under WorldEdit's own {@code //brush},
 * since that command's sub-types (sphere, smooth, gravity, ...) are all
 * hardcoded inside WorldEdit's own internal BrushCommands class with no
 * public extension point for adding a new one - only unstable internal hooks,
 * which is exactly the fragility {@link VerticalSmoothCommand} already
 * avoided for the same reason.
 *
 * shape:
 *   sphere    - true 3D sphere footprint, tapering off with depth too
 *   cube      - the entire bounding cube around the click, no taper
 *   cylinder  - round footprint (like sphere), but unrestricted along the
 *               depth axis WorldEdit auto-detected for this click
 * radius: brush size, like any other WorldEdit brush.
 * iterations: smoothing passes per click (default 1).
 *
 * Per-click intelligence: the axis and facing direction are NOT fixed by
 * this command - {@link SmartSmoothBrush} re-detects them fresh for every
 * click via {@link VerticalSmoothAlgorithm#pickAxis}, so the same bound
 * brush correctly smooths a floor, a ceiling, or a wall depending on what's
 * actually around the block you click.
 *
 * COMPILE CAVEAT: same status as the rest of this package - I don't have the
 * real WorldEdit jar to compile against here. The riskiest guess in this file
 * is {@code LocalSession.getBrushTool(ItemType)} - WorldEdit's own
 * BrushCommands use this pattern to fetch-or-create the BrushTool bound to an
 * item, but I can't confirm the exact method name against your 7.2.10 build.
 * If the build complains here, tell me the exact error and I'll adjust it -
 * the underlying brush logic (SmartSmoothBrush, and everything in
 * VerticalSmoothAlgorithm) is unaffected either way.
 */
public final class SmartSmoothCommand {

    private SmartSmoothCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("smartsmooth")
            .requires(src -> src.hasPermission(2) || src.getEntity() instanceof ServerPlayer)
            .then(Commands.literal("brush")
                .then(shapeBranch("sphere", SmartSmoothBrush.Shape.SPHERE))
                .then(shapeBranch("cube", SmartSmoothBrush.Shape.CUBE))
                .then(shapeBranch("cylinder", SmartSmoothBrush.Shape.CYLINDER))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> shapeBranch(
            String literal, SmartSmoothBrush.Shape shape) {
        return Commands.literal(literal)
            .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 20))
                .executes(ctx -> bind(ctx, shape, DoubleArgumentType.getDouble(ctx, "radius"), 1))
                .then(Commands.argument("iterations", IntegerArgumentType.integer(1, 10))
                    .executes(ctx -> bind(ctx, shape,
                            DoubleArgumentType.getDouble(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "iterations")))));
    }

    private static int bind(CommandContext<CommandSourceStack> ctx, SmartSmoothBrush.Shape shape,
                             double radius, int iterations) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer mcPlayer)) {
            src.sendFailure(new TextComponent("Only a player can use /smartsmooth brush."));
            return 0;
        }
        try {
            Actor actor = ForgeAdapter.adaptPlayer(mcPlayer);
            Player player = (Player) actor;
            ItemType heldItem = player.getItemInHand(HandSide.MAIN_HAND).getType();

            BrushTool tool = WorldEdit.getInstance().getSessionManager().get(actor).getBrushTool(heldItem);
            tool.setSize(radius);
            tool.setBrush(new SmartSmoothBrush(shape, iterations), "gradientcraft.brush.smartsmooth");

            actor.printInfo(com.sk89q.worldedit.util.formatting.text.TextComponent.of(
                    "smartsmooth brush (" + shape.name().toLowerCase() + ", radius " + radius
                            + ", " + iterations + " iteration" + (iterations == 1 ? "" : "s")
                            + ") bound to your held item - left-click a block to smooth around it."));
            return 1;
        } catch (Exception e) {
            src.sendFailure(new TextComponent("smartsmooth brush failed: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
