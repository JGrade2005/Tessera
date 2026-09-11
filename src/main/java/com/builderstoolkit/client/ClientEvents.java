package com.builderstoolkit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;

import com.builderstoolkit.client.gui.ButtonPositionScreen;
import com.builderstoolkit.client.gui.GradientScreen;
import com.builderstoolkit.config.ModConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Adds the "G" button to the survival inventory. Click opens the toolkit;
 * shift-click opens the drag-to-place editor for the button itself.
 */
public final class ClientEvents {

    private static final int BUTTON_ID = 7710;
    private static final int INV_W = 176;
    private static final int INV_H = 166;
    private static final int BTN = 20;
    /** Creative uses a different panel size, so the fallback centring differs. */
    private static final int CREATIVE_W = 195;

    @SubscribeEvent
    @SuppressWarnings("unchecked") // 1.7.10 exposes buttonList as a raw List
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isInventory(event.gui) || !ModConfig.showButton()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        GuiContainer gui = (GuiContainer) event.gui;
        int guiLeft = fieldOf(gui, fallbackLeft(gui, mc), "guiLeft", "field_147003_i");
        int guiTop = fieldOf(gui, (gui.height - INV_H) / 2, "guiTop", "field_147009_r");

        event.buttonList
            .add(new GuiButton(BUTTON_ID, guiLeft + ModConfig.buttonX(), guiTop + ModConfig.buttonY(), BTN, BTN, "G"));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!isInventory(event.gui) || event.button.id != BUTTON_ID) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (GuiScreen.isShiftKeyDown()) {
            mc.displayGuiScreen(new ButtonPositionScreen(event.gui));
        } else {
            mc.displayGuiScreen(new GradientScreen());
        }
    }

    /** Survival and creative inventories both get the button. */
    private static boolean isInventory(GuiScreen gui) {
        return gui instanceof GuiInventory || gui instanceof GuiContainerCreative;
    }

    /**
     * Reads a protected GuiContainer layout field. Other mods (NEI in particular)
     * move the inventory, so recomputing the centre would put the button in the
     * wrong place. Falls back to the vanilla maths if the lookup fails.
     */
    private static int fieldOf(GuiContainer gui, int fallback, String... names) {
        try {
            Integer v = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, names);
            if (v != null) return v.intValue();
        } catch (Throwable ignored) {
            // obfuscated name mismatch; fall through
        }
        return fallback;
    }

    /** Vanilla GuiInventory shifts right while potion effects are listed. */
    private static int fallbackLeft(GuiContainer gui, Minecraft mc) {
        if (gui instanceof GuiContainerCreative) return (gui.width - CREATIVE_W) / 2;
        return mc.thePlayer.getActivePotionEffects()
            .isEmpty() ? (gui.width - INV_W) / 2 : 160 + (gui.width - INV_W - 160) / 2;
    }
}
