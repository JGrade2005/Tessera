package com.gradientcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and caches an average-color index for (almost) every block in the game,
 * including modded blocks, by reading each block's particle texture PNG and
 * averaging its opaque pixels into a CIELAB color.
 *
 * Each entry also records a few properties used by the in-GUI filter toggles:
 *  - opaqueFraction: fraction of texture pixels that are fully opaque
 *  - fullBlock: whether the block's collision shape is a full cube
 *  - blockEntity: whether the block has a block entity (chest, furnace, etc.)
 *
 * Cheap model/shape lookups happen on the main thread (where they are safe),
 * then the heavier PNG reading/averaging runs on a background thread so the
 * game does not hitch. Results are cached for the whole session.
 */
public final class BlockColorIndex {

    public enum State { NOT_STARTED, INDEXING, READY, FAILED }

    /** One usable palette entry. */
    public static final class Entry {
        public final Block block;
        public final ItemStack stack;
        public final float[] lab;
        public final float opaqueFraction;
        public final boolean fullBlock;
        public final boolean blockEntity;

        Entry(Block block, ItemStack stack, float[] lab,
              float opaqueFraction, boolean fullBlock, boolean blockEntity) {
            this.block = block;
            this.stack = stack;
            this.lab = lab;
            this.opaqueFraction = opaqueFraction;
            this.fullBlock = fullBlock;
            this.blockEntity = blockEntity;
        }
    }

    private static volatile State state = State.NOT_STARTED;
    private static volatile int progress = 0;
    private static volatile int total = 0;

    private static final List<Entry> PALETTE = new ArrayList<>();
    private static final Map<Block, float[]> BY_BLOCK = new HashMap<>();

    private BlockColorIndex() {}

    public static State state() { return state; }
    public static int progress() { return progress; }
    public static int total() { return total; }
    public static boolean isReady() { return state == State.READY; }
    public static List<Entry> palette() { return PALETTE; }

    public static float[] labOf(Block b) { return BY_BLOCK.get(b); }

    /** Kick off indexing if it has not run yet. Safe to call repeatedly. Call on the main thread. */
    public static synchronized void ensureStarted() {
        if (state != State.NOT_STARTED) return;
        state = State.INDEXING;

        // --- Phase 1 (main thread): resolve texture + cheap properties ---
        final List<Object[]> jobs = new ArrayList<>();
        // {Block, ItemStack, ResourceLocation png, Boolean fullBlock, Boolean blockEntity}
        Minecraft mc = Minecraft.getInstance();
        for (Block block : Registry.BLOCK) {
            Item item = block.asItem();
            if (item == Items.AIR) continue; // no inventory representation
            try {
                BlockState st = block.defaultBlockState();

                BakedModel model = mc.getBlockRenderer().getBlockModel(st);
                TextureAtlasSprite sprite = model.getParticleIcon();
                ResourceLocation name = sprite.getName();
                if (name.getPath().contains("missingno")) continue;
                ResourceLocation png = new ResourceLocation(
                        name.getNamespace(), "textures/" + name.getPath() + ".png");

                boolean fullBlock = false;
                try {
                    fullBlock = st.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                } catch (Exception ignored) { /* some blocks need a real world; treat as not-full */ }

                boolean blockEntity = block instanceof EntityBlock;

                jobs.add(new Object[] { block, new ItemStack(item), png, fullBlock, blockEntity });
            } catch (Exception ignored) {
                // a few blocks have no sensible model; just skip them
            }
        }
        total = jobs.size();

        // --- Phase 2 (background thread): read PNGs and average ---
        Thread t = new Thread(() -> {
            try {
                for (Object[] job : jobs) {
                    Block block = (Block) job[0];
                    ItemStack stack = (ItemStack) job[1];
                    ResourceLocation png = (ResourceLocation) job[2];
                    boolean fullBlock = (Boolean) job[3];
                    boolean blockEntity = (Boolean) job[4];

                    float[] result = averageColor(png); // {L, a, b, opaqueFraction} or null
                    if (result != null) {
                        float[] lab = new float[] { result[0], result[1], result[2] };
                        Entry e = new Entry(block, stack, lab, result[3], fullBlock, blockEntity);
                        synchronized (PALETTE) {
                            PALETTE.add(e);
                            BY_BLOCK.put(block, lab);
                        }
                    }
                    progress++;
                }
                state = State.READY;
            } catch (Throwable th) {
                th.printStackTrace();
                state = State.FAILED;
            }
        }, "GradientCraft-ColorIndex");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Average the opaque pixels of a texture into a LAB color.
     * @return {L, a, b, opaqueFraction} or null if the texture is unusable.
     */
    private static float[] averageColor(ResourceLocation png) {
        try {
            Resource res = Minecraft.getInstance().getResourceManager().getResource(png);
            try (InputStream is = res.getInputStream();
                 NativeImage img = NativeImage.read(is)) {
                long rs = 0, gs = 0, bs = 0, opaque = 0;
                int w = img.getWidth();
                int h = img.getHeight();
                int totalPixels = w * h;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = img.getPixelRGBA(x, y); // NativeImage packs as 0xAABBGGRR
                        int a = (argb >> 24) & 0xFF;
                        if (a < 128) continue; // ignore mostly-transparent pixels for the average
                        int bch = (argb >> 16) & 0xFF;
                        int gch = (argb >> 8) & 0xFF;
                        int rch = argb & 0xFF;
                        rs += rch; gs += gch; bs += bch; opaque++;
                    }
                }
                if (opaque == 0) return null;
                int r = (int) (rs / opaque);
                int g = (int) (gs / opaque);
                int b = (int) (bs / opaque);
                float[] lab = Lab.rgbToLab(r, g, b);
                float fraction = totalPixels == 0 ? 0f : (float) opaque / (float) totalPixels;
                return new float[] { lab[0], lab[1], lab[2], fraction };
            }
        } catch (Exception e) {
            return null;
        }
    }
}
