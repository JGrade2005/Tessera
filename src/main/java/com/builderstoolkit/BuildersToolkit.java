package com.builderstoolkit;

import net.minecraftforge.common.MinecraftForge;

import com.builderstoolkit.client.ClientEvents;
import com.builderstoolkit.client.CommandQueue;
import com.builderstoolkit.config.ConfigEvents;
import com.builderstoolkit.config.ModConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Client-side build helper. Registers no blocks, items, packets or world data,
 * so it is safe on any server and needs no server-side install.
 *
 * 1.7.10 has no {@code clientSideOnly} flag, so {@code acceptableRemoteVersions = "*"}
 * is what stops FML rejecting servers that do not have this mod installed.
 */
@Mod(
    modid = BuildersToolkit.MODID,
    name = "BuildersToolkit",
    version = Tags.VERSION,
    acceptableRemoteVersions = "*",
    guiFactory = "com.builderstoolkit.config.ConfigGuiFactory")
public class BuildersToolkit {

    public static final String MODID = "builderstoolkit";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.init(event.getSuggestedConfigurationFile());

        // Everything below touches client-only classes. Without a clientSideOnly
        // flag in 1.7.10, this guard is what keeps the jar harmless on a server.
        if (!event.getSide()
            .isClient()) return;

        MinecraftForge.EVENT_BUS.register(new ClientEvents());
        // ConfigChangedEvent is posted on the FML bus, not the Forge one.
        FMLCommonHandler.instance()
            .bus()
            .register(new ConfigEvents());
        FMLCommonHandler.instance()
            .bus()
            .register(new CommandQueue());
    }
}
