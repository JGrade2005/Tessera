package com.builderstoolkit.client;

import java.util.ArrayDeque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Sends a list of commands one at a time, a few ticks apart.
 *
 * A gradient is one command per step, which is useless as clipboard text:
 * Minecraft chat takes a single line, so a multi-line script cannot be pasted.
 * Spacing the sends also keeps servers from treating the burst as chat spam.
 */
public final class CommandQueue {

    /** Ticks between sends. 4 is about five commands a second. */
    private static final int INTERVAL = 4;

    private static final ArrayDeque<String> PENDING = new ArrayDeque<String>();
    private static int cooldown;
    private static int total;

    /** Queues commands, replacing anything still waiting. */
    public static void run(List<String> commands) {
        PENDING.clear();
        for (String c : commands) {
            String line = c.trim();
            if (line.isEmpty()) continue;
            PENDING.add(line);
        }
        total = PENDING.size();
        cooldown = 0;
        if (total > 0) say(EnumChatFormatting.GRAY + "Running " + total + " commands...");
    }

    public static void cancel() {
        if (PENDING.isEmpty()) return;
        PENDING.clear();
        say(EnumChatFormatting.RED + "Cancelled.");
    }

    public static boolean isRunning() {
        return !PENDING.isEmpty();
    }

    public static int remaining() {
        return PENDING.size();
    }

    public static int total() {
        return total;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            PENDING.clear();
            return;
        }
        if (--cooldown > 0) return;
        cooldown = INTERVAL;

        mc.thePlayer.sendChatMessage(PENDING.poll());
        if (PENDING.isEmpty()) say(EnumChatFormatting.GRAY + "Done.");
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(message));
    }
}
