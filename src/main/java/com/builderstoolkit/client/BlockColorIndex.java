package com.builderstoolkit.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.FMLLog;

/**
 * Average-colour index over every block variant in the game.
 *
 * Colour comes from the block's source PNG where one exists, and from the
 * stitched atlas otherwise, so blocks whose textures are generated at runtime or
 * filed outside textures/blocks still get indexed. The block's render tint is
 * applied on top, which is what separates dyed variants that share one greyscale
 * texture.
 *
 * 1.7.10 keys textures on (block, metadata) rather than on distinct block
 * objects, so the index is per-variant. Indexing by block alone would collapse
 * all 16 wool colours into one entry and make the gradients useless.
 *
 * Registry, icon and GL lookups run on the main thread; PNG decoding runs on one
 * background thread so opening the GUI does not stall the client.
 */
public final class BlockColorIndex {

    public enum State {
        NOT_STARTED,
        INDEXING,
        READY,
        FAILED
    }

    /** Per-block subtype cap. GregTech-style blocks expose thousands; a sample is enough. */
    private static final int MAX_SUBTYPES = 32;
    /** Overall cap, so a kitchen-sink pack cannot spend minutes indexing. */
    private static final int MAX_ENTRIES = 12000;
    /** Largest atlas worth copying out of the GPU, in pixels: 4096x4096. */
    private static final int MAX_ATLAS_PIXELS = 16 * 1024 * 1024;

    /** One indexed block variant. */
    public static final class Entry {

        public final Block block;
        public final int meta;
        public final ItemStack stack;
        public final float[] lab;
        /** Averaged texture colour, packed 0xRRGGBB. Used to draw large previews cheaply. */
        public final int rgb;
        public final float opaqueFraction;
        public final boolean fullBlock;
        public final boolean tileEntity;
        /** True when every face uses the same texture, so it reads as one solid colour. */
        public final boolean sameOnAllSides;

        Entry(Block block, int meta, ItemStack stack, float[] lab, int rgb, float opaqueFraction, boolean fullBlock,
            boolean tileEntity, boolean sameOnAllSides) {
            this.block = block;
            this.meta = meta;
            this.stack = stack;
            this.lab = lab;
            this.rgb = rgb;
            this.opaqueFraction = opaqueFraction;
            this.fullBlock = fullBlock;
            this.tileEntity = tileEntity;
            this.sameOnAllSides = sameOnAllSides;
        }

        public String displayName() {
            try {
                return stack.getDisplayName();
            } catch (Throwable t) {
                return id();
            }
        }

        /** Registry name, with the metadata suffix WorldEdit and /setblock accept. */
        public String id() {
            String name = String.valueOf(Block.blockRegistry.getNameForObject(block));
            return meta == 0 ? name : name + ":" + meta;
        }

        /**
         * Numeric id for WorldEdit, e.g. "35:14".
         *
         * WorldEdit 6 splits a pattern entry on its FIRST colon to separate block
         * from data, so a modded registry name is unusable: "BiomesOPlenty:newBopDirt:5"
         * parses as block "BiomesOPlenty", data "newBopDirt". Numeric ids have no
         * colon of their own and always resolve.
         */
        public String worldEditId() {
            int blockId = Block.getIdFromBlock(block);
            return meta == 0 ? String.valueOf(blockId) : blockId + ":" + meta;
        }
    }

    /** Everything the background thread needs about one variant. */
    private static final class Job {

        Block block;
        int meta;
        ItemStack stack;
        ResourceLocation png;
        /** Sprite rectangle in the atlas: x, y, width, height. Null when unknown. */
        int[] rect;
        int tint;
        boolean fullBlock;
        boolean tileEntity;
        boolean sameOnAllSides;
    }

    private static volatile State state = State.NOT_STARTED;
    private static volatile int progress = 0;
    private static volatile int total = 0;

    private static final List<Entry> PALETTE = new ArrayList<Entry>();
    private static final Map<Long, Entry> BY_KEY = new HashMap<Long, Entry>();

    /** Atlas copy, ARGB, level 0. Held only while indexing runs. */
    private static int[] atlas;
    private static int atlasWidth;

    private BlockColorIndex() {}

    public static State state() {
        return state;
    }

    public static int progress() {
        return progress;
    }

    public static int total() {
        return total;
    }

    public static boolean isReady() {
        return state == State.READY;
    }

    public static List<Entry> palette() {
        return PALETTE;
    }

    /** A copy safe to iterate while the background thread is still adding entries. */
    public static List<Entry> snapshot() {
        synchronized (PALETTE) {
            return new ArrayList<Entry>(PALETTE);
        }
    }

    /** The indexed entry for a block variant, or null if it was not indexed. */
    public static Entry find(Block block, int meta) {
        if (block == null) return null;
        synchronized (PALETTE) {
            return BY_KEY.get(Long.valueOf(key(block, meta)));
        }
    }

    /** The indexed entry backing an item stack, or null if it is not a known block. */
    public static Entry find(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Block b = Block.getBlockFromItem(stack.getItem());
        if (b == null || b == Blocks.air) return null;
        return find(b, stack.getItemDamage());
    }

    private static long key(Block block, int meta) {
        return ((long) Block.getIdFromBlock(block) << 16) | (meta & 0xFFFF);
    }

    /** Starts indexing if it has not run yet. Safe to call repeatedly, on the main thread. */
    public static synchronized void ensureStarted() {
        if (state != State.NOT_STARTED) return;
        state = State.INDEXING;

        readAtlas();
        final List<Job> jobs = collectJobs();
        total = jobs.size();

        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    decodeAll(jobs);
                    state = State.READY;
                } catch (Throwable th) {
                    th.printStackTrace();
                    state = State.FAILED;
                } finally {
                    atlas = null;
                }
            }
        }, "BuildersToolkit-ColorIndex");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Main thread: resolve every block variant to a texture plus the cheap
     * properties the GUI filters on.
     *
     * Modded blocks throw from all of these, so each call is guarded on its own.
     * A shared try/catch here is a trap: one mod's broken getIcon would discard a
     * variant that every other lookup had resolved fine.
     */
    private static List<Job> collectJobs() {
        List<Job> jobs = new ArrayList<Job>();
        List<ItemStack> subs = new ArrayList<ItemStack>();

        for (Object o : Block.blockRegistry) {
            if (jobs.size() >= MAX_ENTRIES) break;
            Block block = (Block) o;
            Item item = Item.getItemFromBlock(block);
            if (item == null) continue; // no inventory form

            subs.clear();
            try {
                // Some mods dereference the tab, so hand them a real one.
                CreativeTabs tab = block.getCreativeTabToDisplayOn();
                block.getSubBlocks(item, tab != null ? tab : CreativeTabs.tabBlock, subs);
            } catch (Throwable ignored) {
                subs.clear();
            }
            if (subs.isEmpty()) {
                try {
                    block.getSubBlocks(item, (CreativeTabs) null, subs);
                } catch (Throwable ignored) {
                    subs.clear();
                }
            }
            if (subs.isEmpty()) subs.add(new ItemStack(item, 1, 0));

            boolean fullBlock;
            try {
                fullBlock = block.renderAsNormalBlock();
            } catch (Throwable ignored) {
                fullBlock = false;
            }

            int taken = 0;
            for (int i = 0; i < subs.size() && taken < MAX_SUBTYPES; i++) {
                ItemStack stack = subs.get(i);
                if (stack == null) continue;
                int meta = stack.getItemDamage();
                if (meta < 0 || meta > 0x7FFF) continue;

                IIcon icon;
                try {
                    icon = block.getIcon(2, meta);
                } catch (Throwable t) {
                    skip(block, meta, "getIcon threw " + t.getClass().getSimpleName());
                    continue;
                }

                Job job = new Job();
                job.block = block;
                job.meta = meta;
                job.fullBlock = fullBlock;
                job.png = texturePath(icon);
                job.rect = spriteRect(icon);
                job.tint = 0xFFFFFF;

                try {
                    job.stack = stack.copy();
                } catch (Throwable t) {
                    skip(block, meta, "stack copy threw " + t.getClass().getSimpleName());
                    continue;
                }
                try {
                    job.tileEntity = block.hasTileEntity(meta);
                } catch (Throwable ignored) {}
                try {
                    job.tint = block.getRenderColor(meta);
                } catch (Throwable ignored) {}
                job.sameOnAllSides = sameOnAllSides(block, meta);

                if (job.png == null && job.rect == null) {
                    skip(block, meta, icon == null ? "no icon" : "unusable icon name");
                    continue;
                }
                jobs.add(job);
                taken++;
            }
        }
        return jobs;
    }

    /** Background thread: average each texture and record the colour. */
    private static void decodeAll(List<Job> jobs) {
        // Variants share textures constantly, so cache hits and misses alike.
        Map<ResourceLocation, float[]> cache = new HashMap<ResourceLocation, float[]>();

        for (Job job : jobs) {
            float[] avg = null;
            if (job.png != null) {
                if (cache.containsKey(job.png)) {
                    avg = cache.get(job.png);
                } else {
                    avg = averageColor(job.png); // {L, a, b, opaqueFraction, rgb} or null
                    cache.put(job.png, avg);
                }
            }
            if (avg == null && job.rect != null) avg = sampleAtlas(job.rect);

            progress++;
            if (avg == null) {
                skip(job.block, job.meta, job.png == null ? "not in atlas" : "no texture at " + job.png);
                continue;
            }
            avg = applyTint(avg, job.tint);

            float[] lab = new float[] { avg[0], avg[1], avg[2] };
            Entry e = new Entry(
                job.block,
                job.meta,
                job.stack,
                lab,
                (int) avg[4],
                avg[3],
                job.fullBlock,
                job.tileEntity,
                job.sameOnAllSides);
            synchronized (PALETTE) {
                if (BY_KEY.put(Long.valueOf(key(job.block, job.meta)), e) == null) PALETTE.add(e);
            }
        }
        logSummary(jobs.size());
    }

    /** True when all six faces resolve to the same icon. */
    private static boolean sameOnAllSides(Block block, int meta) {
        try {
            IIcon first = block.getIcon(0, meta);
            if (first == null) return false;
            String name = first.getIconName();
            for (int side = 1; side < 6; side++) {
                IIcon icon = block.getIcon(side, meta);
                if (icon == null || !name.equals(icon.getIconName())) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---- atlas ----

    /**
     * Copies the stitched block atlas out of the GPU, once.
     *
     * Reading sprite pixels straight off the sprite does not work in 1.7.10:
     * TextureMap calls clearFramesTextureData() on every sprite that is not
     * animated, immediately after uploading it. The GPU copy is the only one left.
     * Needs the GL context, so main thread only.
     */
    private static void readAtlas() {
        try {
            ITextureObject tex = Minecraft.getMinecraft()
                .getTextureManager()
                .getTexture(TextureMap.locationBlocksTexture);
            if (tex == null) return;

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0) return;
            if ((long) w * h > MAX_ATLAS_PIXELS) {
                FMLLog.info("[BuildersToolkit] atlas is %dx%d, too large to sample; source PNGs only", w, h);
                return;
            }

            // BGRA + 8_8_8_8_REV is what TextureUtil uploads with, so it reads back as ARGB.
            IntBuffer buf = GLAllocation.createDirectIntBuffer(w * h);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
            int[] pixels = new int[w * h];
            buf.get(pixels);
            atlas = pixels;
            atlasWidth = w;
        } catch (Throwable t) {
            atlas = null;
            FMLLog.info("[BuildersToolkit] could not read the block atlas: %s", t);
        }
    }

    /** Where a sprite sits in the atlas: x, y, width, height. Null if it is not a sprite. */
    private static int[] spriteRect(IIcon icon) {
        if (!(icon instanceof TextureAtlasSprite)) return null;
        try {
            TextureAtlasSprite s = (TextureAtlasSprite) icon;
            return new int[] { s.getOriginX(), s.getOriginY(), s.getIconWidth(), s.getIconHeight() };
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Averages one sprite's rectangle out of the atlas copy.
     *
     * @return {L, a, b, opaqueFraction, rgb}, or null if nothing there is opaque
     */
    private static float[] sampleAtlas(int[] rect) {
        int[] pixels = atlas;
        if (pixels == null) return null;
        int ox = rect[0], oy = rect[1], w = rect[2], h = rect[3];
        if (w <= 0 || h <= 0 || ox < 0 || oy < 0) return null;
        if (ox + w > atlasWidth || (long) (oy + h) * atlasWidth > pixels.length) return null;

        long rs = 0, gs = 0, bs = 0, opaque = 0;
        for (int y = 0; y < h; y++) {
            int row = (oy + y) * atlasWidth + ox;
            for (int x = 0; x < w; x++) {
                int argb = pixels[row + x];
                if (((argb >>> 24) & 0xFF) < 128) continue;
                rs += (argb >> 16) & 0xFF;
                gs += (argb >> 8) & 0xFF;
                bs += argb & 0xFF;
                opaque++;
            }
        }
        if (opaque == 0) return null;
        int r = (int) (rs / opaque);
        int g = (int) (gs / opaque);
        int b = (int) (bs / opaque);
        float[] lab = Lab.rgbToLab(r, g, b);
        return new float[] { lab[0], lab[1], lab[2], (float) opaque / (float) (w * h), (r << 16) | (g << 8) | b };
    }

    /**
     * Applies the block's render tint. Dyed blocks in several mods share one
     * greyscale texture and differ only by this multiplier, so without it every
     * colour of them averages to the same grey.
     */
    private static float[] applyTint(float[] avg, int tint) {
        if ((tint & 0xFFFFFF) == 0xFFFFFF) return avg;
        int rgb = (int) avg[4];
        int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        float[] lab = Lab.rgbToLab(r, g, b);
        return new float[] { lab[0], lab[1], lab[2], avg[3], (r << 16) | (g << 8) | b };
    }

    // ---- source PNGs ----

    /** "stone" or "modid:foo" to assets/&lt;domain&gt;/textures/blocks/&lt;path&gt;.png */
    private static ResourceLocation texturePath(IIcon icon) {
        if (icon == null) return null;
        String name = icon.getIconName();
        if (name == null || name.length() == 0 || name.contains("missingno")) return null;
        int colon = name.indexOf(':');
        String domain = colon < 0 ? "minecraft" : name.substring(0, colon);
        String path = colon < 0 ? name : name.substring(colon + 1);
        return new ResourceLocation(domain, "textures/blocks/" + path + ".png");
    }

    /**
     * Average the opaque pixels of a texture.
     *
     * @return {L, a, b, opaqueFraction, rgb}, or null if the texture is unusable.
     *         The packed rgb rides in a float because it never exceeds 2^24, so
     *         it survives the conversion exactly.
     */
    private static float[] averageColor(ResourceLocation png) {
        InputStream is = null;
        try {
            IResource res = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(png);
            is = res.getInputStream();
            BufferedImage img = ImageIO.read(is);
            if (img == null) return null;

            int w = img.getWidth();
            // Animated textures are a vertical strip of square frames; use the first only.
            int h = (img.getHeight() > w && img.getHeight() % w == 0) ? w : img.getHeight();
            if (w <= 0 || h <= 0) return null;

            long rs = 0, gs = 0, bs = 0, opaque = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    if (((argb >>> 24) & 0xFF) < 128) continue; // skip near-transparent pixels
                    rs += (argb >> 16) & 0xFF;
                    gs += (argb >> 8) & 0xFF;
                    bs += argb & 0xFF;
                    opaque++;
                }
            }
            if (opaque == 0) return null;

            int r = (int) (rs / opaque);
            int g = (int) (gs / opaque);
            int b = (int) (bs / opaque);
            float[] lab = Lab.rgbToLab(r, g, b);
            int rgb = (r << 16) | (g << 8) | b;
            return new float[] { lab[0], lab[1], lab[2], (float) opaque / (float) (w * h), rgb };
        } catch (Throwable t) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable ignored) {}
            }
        }
    }

    // ---- diagnostics ----

    /**
     * Why variants were dropped. Guessing at a missing block from a screenshot is
     * slower than reading one line of the log.
     */
    private static final Map<String, Integer> SKIP_COUNTS = new LinkedHashMap<String, Integer>();
    /** One example per block, so 16 dropped metas do not become 16 log lines. */
    private static final Map<String, String> SKIP_BLOCKS = new LinkedHashMap<String, String>();

    private static void skip(Block block, int meta, String reason) {
        Integer n = SKIP_COUNTS.get(reason);
        SKIP_COUNTS.put(reason, Integer.valueOf(n == null ? 1 : n.intValue() + 1));

        String name = String.valueOf(Block.blockRegistry.getNameForObject(block));
        if (SKIP_BLOCKS.size() < 400 && !SKIP_BLOCKS.containsKey(name)) {
            SKIP_BLOCKS.put(name, "meta " + meta + ": " + reason);
        }
    }

    private static void logSummary(int variants) {
        FMLLog.info(
            "[BuildersToolkit] indexed %d of %d block variants (atlas %s)",
            Integer.valueOf(PALETTE.size()),
            Integer.valueOf(variants),
            atlas == null ? "unavailable" : "available");
        for (Map.Entry<String, Integer> e : SKIP_COUNTS.entrySet()) {
            FMLLog.info("[BuildersToolkit]   dropped %d - %s", e.getValue(), e.getKey());
        }
        for (Map.Entry<String, String> e : SKIP_BLOCKS.entrySet()) {
            FMLLog.info("[BuildersToolkit]   %s %s", e.getKey(), e.getValue());
        }
    }
}
