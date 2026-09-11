package com.builderstoolkit.client.gui;

/**
 * Active colour scheme. Fields are mutable rather than constant so switching
 * theme in the config takes effect on the next frame, with no screen rebuild:
 * every widget reads these at draw time.
 */
public final class Theme {

    /** Selectable themes. */
    public enum Palette {
        SLATE,
        BLUEPRINT,
        VANILLA,
        CONTRAST
    }

    /** How widgets are drawn, which differs by more than colour between themes. */
    public enum Style {
        /** Solid body with a dark outer edge. */
        FILLED,
        /** Panel-coloured body with a one-pixel border. */
        OUTLINE,
        /** Vanilla-style raised bevel: light top-left, dark bottom-right. */
        BEVEL
    }

    private Theme() {}

    // Surfaces (need alpha: these are drawRect fills).
    public static int PANEL;
    public static int TITLE_BAR;
    public static int SECTION;
    public static int SECTION_EDGE;
    public static int SLOT;
    public static int SLOT_DARK;
    public static int BORDER;
    public static int LOCKED;

    // Widgets.
    public static int WIDGET;
    public static int WIDGET_HOVER;
    public static int WIDGET_OFF;
    public static int WIDGET_EDGE;
    public static int BEVEL_LIGHT;
    public static int PRIMARY;
    public static int PRIMARY_EDGE;
    public static int PRIMARY_TEXT;

    // Text (RGB; the font renderer does not need alpha).
    public static int TEXT;
    public static int TEXT_DIM;
    public static int TEXT_FAINT;
    public static int TEXT_ERROR;
    public static int TEXT_OK;
    public static int TEXT_ACCENT;

    public static Style STYLE;

    static {
        apply(Palette.SLATE);
    }

    public static void apply(Palette palette) {
        switch (palette) {
            case BLUEPRINT:
                blueprint();
                break;
            case VANILLA:
                vanilla();
                break;
            case CONTRAST:
                contrast();
                break;
            default:
                slate();
                break;
        }
    }

    /** True when sections should be drawn as a filled well rather than by spacing alone. */
    public static boolean hasSectionWells() {
        return (SECTION >>> 24) != 0 || (SECTION_EDGE >>> 24) != 0;
    }

    private static void slate() {
        PANEL = 0xF0101018;
        TITLE_BAR = 0xFF2B2B3A;
        SECTION = 0xFF16161F;
        SECTION_EDGE = 0xFF23232F;
        SLOT = 0xFF3A3A4A;
        SLOT_DARK = 0xFF2A2A38;
        BORDER = 0xFF202030;
        LOCKED = 0xFFFFD060;

        WIDGET = 0xFF34344A;
        WIDGET_HOVER = 0xFF45455F;
        WIDGET_OFF = 0xFF242434;
        WIDGET_EDGE = 0xFF12121C;
        BEVEL_LIGHT = 0xFF4A4A66;
        PRIMARY = 0xFF3F5D46;
        PRIMARY_EDGE = 0xFF12121C;
        PRIMARY_TEXT = 0xDFF5E4;

        TEXT = 0xFFFFFF;
        TEXT_DIM = 0xC8C8D8;
        TEXT_FAINT = 0xA0A0B0;
        TEXT_ERROR = 0xFF7070;
        TEXT_OK = 0x90C090;
        TEXT_ACCENT = 0xFFE08A;

        STYLE = Style.FILLED;
    }

    private static void blueprint() {
        PANEL = 0xF00B1622;
        TITLE_BAR = 0xFF0B1622;
        SECTION = 0x00000000; // no fill; the hairline edge carries the grouping
        SECTION_EDGE = 0xFF16394D;
        SLOT = 0xFF102431;
        SLOT_DARK = 0xFF0D1C27;
        BORDER = 0xFF2E7391;
        LOCKED = 0xFF7FDBFF;

        WIDGET = 0xFF0B1622;
        WIDGET_HOVER = 0xFF12384B;
        WIDGET_OFF = 0xFF0B1622;
        WIDGET_EDGE = 0xFF2E7391;
        BEVEL_LIGHT = 0xFF2E7391;
        PRIMARY = 0xFF0B1622;
        PRIMARY_EDGE = 0xFF7FDBFF;
        PRIMARY_TEXT = 0x7FDBFF;

        TEXT = 0xB8D8E6;
        TEXT_DIM = 0x7FA8BD;
        TEXT_FAINT = 0x557D92;
        TEXT_ERROR = 0xFF8080;
        TEXT_OK = 0x7FDBFF;
        TEXT_ACCENT = 0x7FDBFF;

        STYLE = Style.OUTLINE;
    }

    private static void vanilla() {
        PANEL = 0xFFC6C6C6;
        TITLE_BAR = 0xFF8B8B8B;
        SECTION = 0xFFB0B0B0;
        SECTION_EDGE = 0xFF8B8B8B;
        SLOT = 0xFF8B8B8B;
        SLOT_DARK = 0xFF8B8B8B;
        BORDER = 0xFF373737;
        LOCKED = 0xFF3F3F9E;

        WIDGET = 0xFF9A9A9A;
        WIDGET_HOVER = 0xFFB4B4B4;
        WIDGET_OFF = 0xFF7A7A7A;
        WIDGET_EDGE = 0xFF545454;
        BEVEL_LIGHT = 0xFFDCDCDC;
        PRIMARY = 0xFF6E8F6E;
        PRIMARY_EDGE = 0xFF3C5A3C;
        PRIMARY_TEXT = 0xFFFFFF;

        // Dark text: this is the one light-ground theme.
        TEXT = 0x2B2B2B;
        TEXT_DIM = 0x404040;
        TEXT_FAINT = 0x565656;
        TEXT_ERROR = 0xA00000;
        TEXT_OK = 0x1F5F1F;
        TEXT_ACCENT = 0x3F3F9E;

        STYLE = Style.BEVEL;
    }

    private static void contrast() {
        PANEL = 0xF00A0A0C;
        TITLE_BAR = 0xFF0A0A0C;
        SECTION = 0x00000000; // grouped by spacing only
        SECTION_EDGE = 0x00000000;
        SLOT = 0xFF1A1A1F;
        SLOT_DARK = 0xFF141418;
        BORDER = 0xFF33333C;
        LOCKED = 0xFFFFB224;

        WIDGET = 0xFF1A1A1F;
        WIDGET_HOVER = 0xFF2A2A32;
        WIDGET_OFF = 0xFF141418;
        WIDGET_EDGE = 0xFF33333C;
        BEVEL_LIGHT = 0xFF33333C;
        PRIMARY = 0xFFFFB224;
        PRIMARY_EDGE = 0xFFFFB224;
        PRIMARY_TEXT = 0x0A0A0C;

        TEXT = 0xF2F2F5;
        TEXT_DIM = 0xA8A8B2;
        TEXT_FAINT = 0x6A6A76;
        TEXT_ERROR = 0xFF6B6B;
        TEXT_OK = 0xFFB224;
        TEXT_ACCENT = 0xFFB224;

        STYLE = Style.FILLED;
    }
}
