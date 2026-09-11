package com.builderstoolkit.config;

import com.builderstoolkit.BuildersToolkit;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Re-reads the config when the player closes the settings screen. */
public final class ConfigEvents {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (BuildersToolkit.MODID.equals(event.modID)) ModConfig.load();
    }
}
