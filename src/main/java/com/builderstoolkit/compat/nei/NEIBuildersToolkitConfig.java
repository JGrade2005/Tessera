package com.builderstoolkit.compat.nei;

import com.builderstoolkit.Tags;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * NEI plugin entry point. NEI discovers this by scanning for classes named
 * NEI*Config that implement IConfigureNEI, so the name is load-bearing.
 */
public class NEIBuildersToolkitConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        API.registerNEIGuiHandler(new ToolkitNEIHandler());
    }

    @Override
    public String getName() {
        return "BuildersToolkit";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
