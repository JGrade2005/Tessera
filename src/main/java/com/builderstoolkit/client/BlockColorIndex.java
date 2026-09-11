package com.builderstoolkit.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.FMLLog;

/**
 * Average-colour index over every block variant in the game.
 *
 * A variant's colour is the mean over all six faces, and alongside it we keep
 * the spread of its pixels around that mean. Low spread means a flat, even
 * texture, which reads as a clean gradient step; high spread means a busy or
 * two-tone face that shows as noise however well its average matches.
 *
 * Pixels come from the block's source PNG where one exists, and from the
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
        /** Render tint to draw the texture with, packed 0xRRGGBB. */
        public final int tint;
        /**
         * RMS spread of the block's pixels around its own average, 0-255. Zero is
         * a single flat colour; a mossy or ore texture lands well above 40.
         */
        public final float deviation;
        public final float opaqueFraction;
        public final boolean fullBlock;
        public final boolean tileEntity;
        /** True when every face uses the same texture, so it reads as one solid colour. */
        public final boolean sameOnAllSides;
        public final boolean ore;

        Entry(Block block, int meta, ItemStack stack, float[] lab, int rgb, int tint, float deviation,
            float opaqueFraction, boolean fullBlock, boolean tileEntity, boolean sameOnAllSides, boolean ore) {
            this.block = block;
            this.meta = meta;
            this.stack = stack;
            this.lab = lab;
            this.rgb = rgb;
            this.tint = tint;
            this.deviation = deviation;
            this.opaqueFraction = opaqueFraction;
            this.fullBlock = fullBlock;
            this.tileEntity = tileEntity;
            this.sameOnAllSides = sameOnAllSides;
            this.ore = ore;
        }

        public String displayName() {
            try {
                return stack.getDisplayName();
            } catch (Throwable t) {
                return id();
            }
        }

        /** The side texture, which is the face a wall shows. Null if it cannot be resolved. */
        public IIcon sideIcon() {
            try {
                return block.getIcon(2, meta);
            } catch (Throwable ignored) {
                return null;
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

    /** One distinct face texture of a variant, plus how many of the six faces use it. */
    private static final class Face {

        ResourceLocation png;
        /** Sprite rectangle in the atlas: x, y, width, height. Null when unknown. */
        int[] rect;
        String iconName;
        int count;
    }

    /** Everything the background thread needs about one variant. */
    private static final class Job {

        Block block;
        int meta;
        ItemStack stack;
        List<Face> faces;
        int tint;
        boolean fullBlock;
        boolean tileEntity;
        boolean sameOnAllSides;
        boolean ore;
    }

    /**
     * Running pixel statistics. Sums and sums of squares let one pass over the
     * pixels yield both the mean and the spread, and let faces be combined
     * without re-reading anything.
     */
    private static final class Sample {

        long opaque;
        long total;
        long sr, sg, sb;
        long qr, qg, qb;

        void add(int argb) {
            total++;
            if (((argb >>> 24) & 0xFF) < 128) return; // near-transparent pixels are not the block's colour
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            opaque++;
            sr += r;
            sg += g;
            sb += b;
            qr += (long) r * r;
            qg += (long) g * g;
            qb += (long) b * b;
        }

        void addScaled(Sample s, int weight) {
            opaque += s.opaque * weight;
            total += s.total * weight;
            sr += s.sr * weight;
            sg += s.sg * weight;
            sb += s.sb * weight;
            qr += s.qr * weight;
            qg += s.qg * weight;
            qb += s.qb * weight;
        }

        boolean usable() {
            return opaque > 0;
        }

        int mean(long sum) {
            return (int) (sum / opaque);
        }

        int rgb() {
            return (mean(sr) << 16) | (mean(sg) << 8) | mean(sb);
        }

        float opaqueFraction() {
            return total == 0 ? 0f : (float) opaque / (float) total;
        }

        /** RMS distance of the pixels from the mean, over all three channels at once. */
        float deviation() {
            double var = variance(sr, qr) + variance(sg, qg) + variance(sb, qb);
            return (float) Math.sqrt(Math.max(0.0, var));
        }

        private double variance(long sum, long sumSq) {
            double mean = (double) sum / opaque;
            return (double) sumSq / opaque - mean * mean;
        }
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
     * Main thread: resolve every block variant to its face textures plus the
     * cheap properties the GUI filters on.
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

                List<Face> faces = faces(block, meta);
                if (faces.isEmpty()) {
                    skip(block, meta, "no usable face texture");
                    continue;
                }

                Job job = new Job();
                job.block = block;
                job.meta = meta;
                job.faces = faces;
                job.fullBlock = fullBlock;
                job.sameOnAllSides = faces.size() == 1;
                job.tint = 0xFFFFFF;

                try {
                    job.stack = stack.copy();
                } catch (Throwable t) {
                    skip(
                        block,
                        meta,
                        "stack copy threw " + t.getClass()
                            .getSimpleName());
                    continue;
                }
                try {
                    job.tileEntity = block.hasTileEntity(meta);
                } catch (Throwable ignored) {}
                try {
                    job.tint = block.getRenderColor(meta);
                } catch (Throwable ignored) {}
                job.ore = isOre(block, job.stack);

                jobs.add(job);
                taken++;
            }
        }
        return jobs;
    }

    /** The distinct textures across all six faces, each with the number of faces using it. */
    private static List<Face> faces(Block block, int meta) {
        List<Face> out = new ArrayList<Face>(6);
        for (int side = 0; side < 6; side++) {
            IIcon icon;
            try {
                icon = block.getIcon(side, meta);
            } catch (Throwable ignored) {
                continue;
            }
            if (icon == null) continue;

            String name;
            try {
                name = icon.getIconName();
            } catch (Throwable ignored) {
                continue;
            }
            if (name == null) continue;

            Face existing = null;
            for (Face f : out) {
                if (name.equals(f.iconName)) {
                    existing = f;
                    break;
                }
            }
            if (existing != null) {
                existing.count++;
                continue;
            }

            Face face = new Face();
            face.iconName = name;
            face.count = 1;
            face.png = texturePath(name);
            face.rect = spriteRect(icon);
            if (face.png != null || face.rect != null) out.add(face);
        }
        return out;
    }

    /**
     * Ore blocks make poor gradient steps: they are a base texture with high
     * contrast speckles, so they read as noise however close their average is.
     * The ore dictionary is the reliable signal; the name check is for blocks
     * that never registered.
     */
    private static boolean isOre(Block block, ItemStack stack) {
        try {
            for (int id : OreDictionary.getOreIDs(stack)) {
                String name = OreDictionary.getOreName(id);
                if (name != null && name.toLowerCase(Locale.ROOT)
                    .startsWith("ore")) return true;
            }
        } catch (Throwable ignored) {}

        String reg = String.valueOf(Block.blockRegistry.getNameForObject(block))
            .toLowerCase(Locale.ROOT);
        int colon = reg.indexOf(':');
        String path = colon < 0 ? reg : reg.substring(colon + 1);
        return path.startsWith("ore") || path.endsWith("ore") || path.contains("_ore");
    }

    /** Background thread: average each variant's faces and record the colour. */
    private static void decodeAll(List<Job> jobs) {
        // Variants share textures constantly, so cache hits and misses alike.
        Map<String, Sample> cache = new HashMap<String, Sample>();

        for (Job job : jobs) {
            Sample combined = new Sample();
            for (Face face : job.faces) {
                Sample s = sampleFace(face, cache);
                if (s != null) combined.addScaled(s, face.count);
            }
            progress++;

            if (!combined.usable()) {
                Face first = job.faces.get(0);
                skip(job.block, job.meta, first.png == null ? "not in atlas" : "no texture at " + first.png);
                continue;
            }

            int rgb = tinted(combined.rgb(), job.tint);
            float[] lab = Lab.rgbToLab((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            Entry e = new Entry(
                job.block,
                job.meta,
                job.stack,
                lab,
                rgb,
                job.tint & 0xFFFFFF,
                combined.deviation(),
                combined.opaqueFraction(),
                job.fullBlock,
                job.tileEntity,
                job.sameOnAllSides,
                job.ore);
            synchronized (PALETTE) {
                if (BY_KEY.put(Long.valueOf(key(job.block, job.meta)), e) == null) PALETTE.add(e);
            }
        }
        logSummary(jobs.size());
    }

    /** Pixel statistics for one face, from its PNG if there is one and the atlas if not. */
    private static Sample sampleFace(Face face, Map<String, Sample> cache) {
        if (cache.containsKey(face.iconName)) return cache.get(face.iconName);

        Sample s = face.png == null ? null : samplePng(face.png);
        if (s == null && face.rect != null) s = sampleAtlas(face.rect);
        cache.put(face.iconName, s);
        return s;
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

    /** Reads one sprite's rectangle out of the atlas copy. */
    private static Sample sampleAtlas(int[] rect) {
        int[] pixels = atlas;
        if (pixels == null) return null;
        int ox = rect[0], oy = rect[1], w = rect[2], h = rect[3];
        if (w <= 0 || h <= 0 || ox < 0 || oy < 0) return null;
        if (ox + w > atlasWidth || (long) (oy + h) * atlasWidth > pixels.length) return null;

        Sample s = new Sample();
        for (int y = 0; y < h; y++) {
            int row = (oy + y) * atlasWidth + ox;
            for (int x = 0; x < w; x++) {
                s.add(pixels[row + x]);
            }
        }
        return s.usable() ? s : null;
    }

    /**
     * Applies the block's render tint. Dyed blocks in several mods share one
     * greyscale texture and differ only by this multiplier, so without it every
     * colour of them averages to the same grey.
     */
    private static int tinted(int rgb, int tint) {
        if ((tint & 0xFFFFFF) == 0xFFFFFF) return rgb;
        int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return (r << 16) | (g << 8) | b;
    }

    // ---- source PNGs ----

    /** "stone" or "modid:foo" to assets/&lt;domain&gt;/textures/blocks/&lt;path&gt;.png */
    private static ResourceLocation texturePath(String name) {
        if (name == null || name.length() == 0 || name.contains("missingno")) return null;
        int colon = name.indexOf(':');
        String domain = colon < 0 ? "minecraft" : name.substring(0, colon);
        String path = colon < 0 ? name : name.substring(colon + 1);
        return new ResourceLocation(domain, "textures/blocks/" + path + ".png");
    }

    /** Pixel statistics for one texture file, or null if it cannot be read. */
    private static Sample samplePng(ResourceLocation png) {
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

            Sample s = new Sample();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    s.add(img.getRGB(x, y));
                }
            }
            return s.usable() ? s : null;
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
