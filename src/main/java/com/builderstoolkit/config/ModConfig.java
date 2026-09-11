package com.builderstoolkit.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.builderstoolkit.client.gui.Theme;

/**
 * Client-side settings, backed by config/builderstoolkit.cfg and editable in
 * game from Mods - BuildersToolkit - Config.
 */
public final class ModConfig {

    public static final String CAT_APPEARANCE = "appearance";
    public static final String CAT_WORLDEDIT = "worldedit";

    /** Button offsets are relative to the inventory's top-left, so it follows the GUI. */
    public static final int DEFAULT_BUTTON_X = 0;
    public static final int DEFAULT_BUTTON_Y = -22;

    private static Configuration config;

    private static Theme.Palette theme = Theme.Palette.SLATE;
    private static boolean showButton = true;
    private static int buttonX = DEFAULT_BUTTON_X;
    private static int buttonY = DEFAULT_BUTTON_Y;
    private static boolean worldEditTools = true;
    private static boolean generateTab = true;
    private static boolean labTab = true;

    private ModConfig() {}

    public static Configuration config() {
        return config;
    }

    public static Theme.Palette theme() {
        return theme;
    }

    public static boolean showButton() {
        return showButton;
    }

    public static int buttonX() {
        return buttonX;
    }

    public static int buttonY() {
        return buttonY;
    }

    /** Saves a position picked in the drag-to-place editor. */
    public static void setButtonPosition(int x, int y) {
        if (config == null) return;
        config.get(CAT_APPEARANCE, "buttonX", DEFAULT_BUTTON_X)
            .set(x);
        config.get(CAT_APPEARANCE, "buttonY", DEFAULT_BUTTON_Y)
            .set(y);
        config.save();
        load();
    }

    /** The Generate tab builds a WorldEdit //g command, so it follows the WorldEdit switches. */
    public static boolean generateTabEnabled() {
        return worldEditTools && generateTab;
    }

    /** The Lab tab likewise only produces a WorldEdit expression. */
    public static boolean labTabEnabled() {
        return worldEditTools && labTab;
    }

    public static void init(File file) {
        config = new Configuration(file);
        load();
    }

    /** Reads every value, applies the theme, and writes the file back if anything changed. */
    public static void load() {
        if (config == null) return;

        Property themeProp = config.get(
            CAT_APPEARANCE,
            "theme",
            Theme.Palette.SLATE.name(),
            "GUI theme.\n" + "SLATE     - dark slate panels with grouped wells (default)\n"
                + "BLUEPRINT - outlined, hairline rules, cyan accent\n"
                + "VANILLA   - stone grey with beveled buttons, matches vanilla GUIs\n"
                + "CONTRAST  - near black and greyscale with a single amber accent",
            names(Theme.Palette.values()));
        themeProp.setLanguageKey("builderstoolkit.config.theme");
        theme = parseTheme(themeProp.getString());

        showButton = config
            .get(
                CAT_APPEARANCE,
                "showInventoryButton",
                true,
                "Show the G button on the survival inventory.\n"
                    + "Shift-click that button in game to drag it somewhere else.")
            .getBoolean(true);

        buttonX = config
            .get(CAT_APPEARANCE, "buttonX", DEFAULT_BUTTON_X, "Button offset from the inventory's left edge.")
            .getInt(DEFAULT_BUTTON_X);
        buttonY = config
            .get(CAT_APPEARANCE, "buttonY", DEFAULT_BUTTON_Y, "Button offset from the inventory's top edge.")
            .getInt(DEFAULT_BUTTON_Y);

        worldEditTools = config
            .get(
                CAT_WORLDEDIT,
                "worldEditTools",
                true,
                "Master switch for the parts of this mod that build WorldEdit commands.\n"
                    + "Turn this off to hide the Generate and Lab tabs entirely.")
            .getBoolean(true);

        generateTab = config
            .get(CAT_WORLDEDIT, "generateTab", true, "Show the Generate tab (shape presets that build //g).")
            .getBoolean(true);

        labTab = config.get(CAT_WORLDEDIT, "labTab", true, "Show the Lab tab (parametric shape sandbox).")
            .getBoolean(true);

        Theme.apply(theme);

        if (config.hasChanged()) config.save();
    }

    private static Theme.Palette parseTheme(String value) {
        try {
            return Theme.Palette.valueOf(
                value.trim()
                    .toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Theme.Palette.SLATE; // hand-edited to something unknown
        }
    }

    private static String[] names(Theme.Palette[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i].name();
        }
        return out;
    }
}
