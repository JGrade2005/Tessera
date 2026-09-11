package com.builderstoolkit.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import com.builderstoolkit.BuildersToolkit;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;

/** The settings screen reached from Mods - BuildersToolkit - Config. */
public class ConfigGui extends GuiConfig {

    public ConfigGui(GuiScreen parent) {
        super(parent, categories(), BuildersToolkit.MODID, false, false, "BuildersToolkit");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> categories() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        list.add(
            new ConfigElement(
                ModConfig.config()
                    .getCategory(ModConfig.CAT_APPEARANCE)));
        list.add(
            new ConfigElement(
                ModConfig.config()
                    .getCategory(ModConfig.CAT_WORLDEDIT)));
        return list;
    }
}
