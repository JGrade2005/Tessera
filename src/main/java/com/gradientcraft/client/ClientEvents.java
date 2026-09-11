package com.gradientcraft.client;

import com.gradientcraft.GradientCraft;
import com.gradientcraft.client.gui.GradientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds a small "G" button to the survival inventory that opens the gradient tool.
 * Registered on the Forge event bus, client side only.
 */
@Mod.EventBusSubscriber(modid = GradientCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {

    // Vanilla survival inventory GUI is 176px wide. We anchor relative to leftPos
    // so this stays correct even when the recipe book pushes the GUI sideways.
    private static final int VANILLA_INV_WIDTH = 176;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onInventoryInit(ScreenEvent.InitScreenEvent.Post event) {
        if (event.getScreen() instanceof InventoryScreen inv) {
            int btnW = 20, btnH = 20;
            // Place the button just ABOVE the top-right corner of the inventory,
            // outside the panel. Active potion effects render down the right
            // EDGE of the inventory (starting at leftPos + imageWidth + 2), so a
            // button there gets covered; sitting above the top edge avoids them.
            int x = inv.getGuiLeft() + VANILLA_INV_WIDTH - btnW;
            int y = inv.getGuiTop() - btnH - 2;
            Button btn = new Button(x, y, btnW, btnH, new TextComponent("G"),
                    b -> Minecraft.getInstance().setScreen(new GradientScreen()));
            event.addListener(btn);
        }
    }
}