package com.gradientcraft.compat.jei;

import com.gradientcraft.GradientCraft;
import com.gradientcraft.client.gui.GradientScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI compatibility. All JEI types are confined to this package so the mod
 * loads normally when JEI is absent (JEI only classloads @JeiPlugin types
 * when JEI itself is present).
 *
 * Two registrations are needed:
 *  1) addGuiScreenHandler  -> tells JEI the geometry of our (non-container)
 *     screen so it actually DRAWS its ingredient list beside it. Without this
 *     JEI hides its list on custom Screens, so there is nothing to drag.
 *  2) addGhostIngredientHandler -> defines the waypoint slots as drop targets
 *     for ingredients dragged out of that list.
 *
 * NOTE: built against JEI 9.7.x for 1.18.2. The IGuiProperties getter names
 * (getGuiXSize/getGuiYSize) and IScreenHandler.apply signature are correct for
 * the JEI 9 line; later JEI versions changed these.
 */
@JeiPlugin
public class GradientCraftJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(GradientCraft.MODID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(GradientScreen.class, new ScreenHandler());
        registration.addGhostIngredientHandler(GradientScreen.class, new GhostHandler());
    }

    /** Reports where our GUI sits so JEI can place its ingredient list to the right. */
    private static class ScreenHandler implements IScreenHandler<GradientScreen> {
        @Override
        public IGuiProperties apply(GradientScreen screen) {
            return new Props(screen);
        }
    }

    private static class Props implements IGuiProperties {
        private final GradientScreen s;
        Props(GradientScreen s) { this.s = s; }

        @Override public Class<? extends Screen> getScreenClass() { return GradientScreen.class; }
        @Override public int getGuiLeft()   { return s.guiLeft(); }
        @Override public int getGuiTop()    { return s.guiTop(); }
        @Override public int getGuiXSize()  { return s.guiWidth(); }
        @Override public int getGuiYSize()  { return s.guiHeight(); }
        @Override public int getScreenWidth() {
            return Minecraft.getInstance().getWindow().getGuiScaledWidth();
        }
        @Override public int getScreenHeight() {
            return Minecraft.getInstance().getWindow().getGuiScaledHeight();
        }
    }

    /** Makes each waypoint slot a valid drop target for dragged JEI ingredients. */
    private static class GhostHandler implements IGhostIngredientHandler<GradientScreen> {
        @Override
        public <I> List<Target<I>> getTargets(GradientScreen screen, I ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            if (!(ingredient instanceof ItemStack)) return targets;

            List<Rect2i> areas = screen.getWaypointAreas();
            for (int i = 0; i < areas.size(); i++) {
                final int slot = i;
                final Rect2i area = areas.get(i);
                targets.add(new Target<I>() {
                    @Override
                    public Rect2i getArea() {
                        return area;
                    }

                    @Override
                    public void accept(I dropped) {
                        if (dropped instanceof ItemStack stack) {
                            screen.setWaypointFromStack(slot, stack);
                        }
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {}
    }
}
