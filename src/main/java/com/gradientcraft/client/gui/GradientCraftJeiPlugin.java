package com.gradientcraft.client.gui;

import com.gradientcraft.GradientCraft;
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
 * JEI compatibility for every screen that implements {@link GhostSlots}
 * (the Gradient and Palette tabs). All JEI types are confined to this package
 * so the mod loads normally when JEI is absent.
 *
 * Built against JEI 9.7.x for 1.18.2.
 */
@JeiPlugin
public class GradientCraftJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(GradientCraft.MODID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration reg) {
        reg.addGuiScreenHandler(GradientScreen.class, new ScreenHandler<>(GradientScreen.class));
        reg.addGhostIngredientHandler(GradientScreen.class, new GhostHandler<>());
        reg.addGuiScreenHandler(PaletteScreen.class, new ScreenHandler<>(PaletteScreen.class));
        reg.addGhostIngredientHandler(PaletteScreen.class, new GhostHandler<>());
    }

    /** Reports GUI geometry so JEI shows its ingredient list beside the screen. */
    private static class ScreenHandler<T extends Screen> implements IScreenHandler<T> {
        private final Class<? extends Screen> cls;
        ScreenHandler(Class<? extends Screen> cls) { this.cls = cls; }
        @Override
        public IGuiProperties apply(T screen) {
            if (!(screen instanceof GhostSlots gs)) return null;
            return new Props(gs, cls);
        }
    }

    private static class Props implements IGuiProperties {
        private final GhostSlots s;
        private final Class<? extends Screen> cls;
        Props(GhostSlots s, Class<? extends Screen> cls) { this.s = s; this.cls = cls; }
        @Override public Class<? extends Screen> getScreenClass() { return cls; }
        @Override public int getGuiLeft()   { return s.guiLeft(); }
        @Override public int getGuiTop()    { return s.guiTop(); }
        @Override public int getGuiXSize()  { return s.guiWidth(); }
        @Override public int getGuiYSize()  { return s.guiHeight(); }
        @Override public int getScreenWidth()  { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
        @Override public int getScreenHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    }

    /** Turns each ghost slot into a drop target for dragged JEI ingredients. */
    private static class GhostHandler<T extends Screen> implements IGhostIngredientHandler<T> {
        @Override
        public <I> List<Target<I>> getTargets(T gui, I ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            if (!(gui instanceof GhostSlots gs)) return targets;
            if (!(ingredient instanceof ItemStack)) return targets;
            List<Rect2i> areas = gs.getWaypointAreas();
            for (int i = 0; i < areas.size(); i++) {
                final int slot = i;
                final Rect2i area = areas.get(i);
                targets.add(new Target<I>() {
                    @Override public Rect2i getArea() { return area; }
                    @Override public void accept(I dropped) {
                        if (dropped instanceof ItemStack stack) gs.setWaypointFromStack(slot, stack);
                    }
                });
            }
            return targets;
        }
        @Override public void onComplete() {}
    }
}
