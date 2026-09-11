package com.gradientcraft.worldedit;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Entry point for the {@code /verticalsmooth} integration.
 *
 * This listens on the FORGE event bus (not CLIENT) because registering a
 * command and editing blocks both happen on whichever side owns the command
 * dispatcher - the (integrated or dedicated) server. It only ever touches a
 * WorldEdit class after confirming WorldEdit is actually loaded, so
 * GradientCraft continues to load and run fine on installs without
 * WorldEdit - exactly like the optional JEI integration elsewhere in this
 * mod.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldEditIntegration {

    private WorldEditIntegration() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!ModList.get().isLoaded("worldedit")) return;
        try {
            VerticalSmoothCommand.register(event.getDispatcher());
        } catch (Throwable t) {
            System.err.println("[GradientCraft] Failed to register /verticalsmooth against the "
                    + "installed WorldEdit version - the rest of GradientCraft is unaffected.");
            t.printStackTrace();
        }
        try {
            SmartSmoothCommand.register(event.getDispatcher());
        } catch (Throwable t) {
            System.err.println("[GradientCraft] Failed to register /smartsmooth against the "
                    + "installed WorldEdit version - the rest of GradientCraft is unaffected.");
            t.printStackTrace();
        }
    }
}
