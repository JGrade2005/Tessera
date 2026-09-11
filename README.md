# BuildersToolkit (Minecraft 1.7.10, Forge)

A **client-side** build helper, ported from the 1.18.2 branch and targeting the
**GregTech: New Horizons** toolchain. Open it from the **G** button on your
survival or creative inventory and use any of five tabs to plan builds.

It registers no blocks, items, packets or world data, and never talks to the
server, so it works on any server and needs no server-side install.

> This is the `1.7.10` branch. The 1.18.2 version lives on the `1.18.2` branch
> and the two diverge permanently — port fixes across with `git cherry-pick`,
> never `git merge`.

## Tabs

| Tab | What it does |
| --- | --- |
| **Gradient** | Drop blocks into waypoint slots; fills the in-between steps with the nearest-coloured blocks. |
| **Generate** | Builds a WorldEdit `//g` command from presets, with a live 3D preview. |
| **Colours** | Pick a colour from a wheel or hex code; ranks the closest blocks. |
| **Palette** | Generates a Base / Wall / Accent building palette. Lock what you like, reshuffle the rest. |
| **Lab** | Parametric shape sandbox driving a `//g` expression, previewed live. |

The Generate and Lab tabs only ever *build a command string* for you to copy or
send in chat — the preview is entirely local. Running the command needs
WorldEdit on the server; everything else works with nothing but this mod.

## How the colour matching works

1. On first open it indexes the average colour of every block **variant** in
   the game and converts each to CIELAB.
2. Waypoints are spaced evenly along the strip and the colours between them are
   interpolated in LAB, which is roughly perceptually uniform.
3. Each in-between slot snaps to the nearest real block by colour.

Indexing enumerates each block's subtypes through its **own creative tab** rather
than a null one - mods that dereference the tab (Chisel, Et Futurum Requiem) throw
on null, which silently collapsed them to metadata 0 and lost every variant.
Metadata up to 32767 is accepted, since some mods use item damage as a variant
index rather than real block metadata.

Indexing is keyed on `(block, metadata)`, not on the block alone — in 1.7.10 all
16 wool colours are one `Block`, so indexing per-block would collapse them into
a single entry and make gradients useless. Registry lookups run on the main
thread; PNG decoding runs on a background thread so opening the GUI does not
stall the client. To stay usable in a pack the size of GTNH, indexing caps at 16
subtypes per block and 6000 entries overall, and caches each decoded texture.

Filter toggles restrict which blocks the tools may pick from. They are **shared by
the Gradient, Colours and Palette tabs**, so a block excluded in one is excluded in
all - switching tabs never quietly changes what you are choosing between. Your
chosen waypoints are always kept regardless.

- **Opaque** — only blocks whose texture has no transparency.
- **Full** — only full-cube blocks (excludes slabs, stairs, fences, carpets).
- **No-TE** — excludes blocks with tile entities (chests, furnaces, signs).
- **Sides** — only blocks whose six faces all use the same texture, so no grass
  blocks with a green top and dirt sides.
- **No-Ore** — excludes ore blocks, identified by the ore dictionary. **On by
  default**: an ore is a base texture with high-contrast speckles, so it reads as
  noise in a wall however close its average colour is.

Colours are read from the **stitched texture atlas** where possible, falling back
to the source PNG. The atlas has an entry for every rendered block, including mods
whose textures are generated at runtime or live outside `textures/blocks`, which is
what the PNG-only path was missing.

## Controls

- **Gradient** — `-` / `+` set strip length, `Dupes` allows repeats in adjacent
  slots. Drag a block from the inventory panel into a waypoint slot;
  right-click a slot to clear it. `Copy IDs` copies the ordered list.
- **Palette** — left-click a slot to lock it, right-click to clear, drag a block
  in to set it. `Reshuffle` re-rolls only the unlocked slots.
- **Colours** — drag on the wheel, use the brightness slider, or type a hex
  code. Click a result to copy its id.
- **Generate / Lab** — drag inside the preview box to rotate. Pasting a full
  `//g ...` command into the expression box parses it back into the controls.
- **Scrolling** — tall panels open at the top and scroll with the wheel, or by
  dragging the scrollbar on the right edge.

Copied ids carry the metadata suffix where it matters (e.g. `minecraft:wool:14`),
which is the form WorldEdit and `/setblock` accept.

## Blend: 2D walls and noise

Scroll down inside the Gradient tab for the **Blend** section. The strip above it
is the palette; Blend turns it into the wall you would actually build.

| Mode | What it does |
| --- | --- |
| `BANDS` | Hard horizontal bands, one gradient step per layer. |
| `DITHER` | Bands softened by per-cell white noise. The classic scattered blend. |
| `NOISE` | Bands softened by coherent noise, giving organic interlocking edges. |
| `MAP` | No gradient axis at all - the index comes purely from 2D noise, like a biome map. |

- **randomness** - how far, in gradient steps, noise can pull a cell. 100% is +/-3 steps.
- **scale** - noise feature size in blocks. Larger means bigger shapes.
- **octaves** - fractal detail on top of the large shapes. 1 is smooth, 5 is ragged.
- **wall H** - how many layers tall the wall is, which is also the number of commands.
- **Reroll seed** - a new random arrangement at the same settings.

The preview draws each cell using the block's real side texture, which is the face
a wall actually shows. The full wall is sampled down to the preview grid, so what
you see is the whole gradient at the size it reads from a distance. Textures come
straight off the stitched atlas as one quad per cell — rendering each cell as an
item instead would be thousands of model draws a frame.

### Exporting to WorldEdit

**Copy WorldEdit script** puts a command list on the clipboard:

```
//set 73%minecraft:stone,27%minecraft:cobblestone
//shift 1 down
//set 65%minecraft:stone,35%minecraft:cobblestone
```

Select the **top layer** of the wall, one block thick, then run the lines in order.
Percentages always total exactly 100 (largest-remainder rounding), so WorldEdit
never leaves gaps.

Scripts use **numeric block ids** (`35:14`), not registry names. WorldEdit 6 splits
a pattern entry on its first colon to separate block from data, so a modded name is
unusable: `BiomesOPlenty:newBopDirt:5` parses as block `BiomesOPlenty`, data
`newBopDirt`, and fails. Numeric ids carry no colon of their own. **Copy IDs** still
gives registry names, which is the form `/setblock` wants.

One honest limitation: WorldEdit patterns say *how much* of each block to use, not
*where* to put it. That reproduces `BANDS`, `DITHER` and `NOISE` faithfully, since
those only vary along the build axis. `MAP` varies across the layer, which a
pattern cannot place, so it exports as a single `//set` with the overall mix and
loses its shape. Use `MAP` for planning and place it by hand, or pick `NOISE`
instead if you need the export to match.

### Gradients across a generated shape

The Generate tab can lay the gradient across the shape properly, not just scatter
it. **Copy gradient script** emits one `//g` per gradient step, each filling a band
of space intersected with the shape:

```
//g 1 g=y;return (x^2+y^2+z^2<.8^2)&&(g<-.667)
//g 4 g=y;return (x^2+y^2+z^2<.8^2)&&(g>=-.667)&&(g<-.333)
```

`g` is assigned before any rotation runs, so a vertical fade stays vertical however
the shape is spun — and a preset that moves `x` or `z` itself, as Spiral Stair does,
cannot drag the gradient along with it.

**Run gradient** sends them for you, one every four ticks. That is not a
convenience: Minecraft chat takes a single line, so a multi-command script cannot
be pasted, and firing them all at once trips server spam limits. Click it again to
cancel. **Copy grad** still copies the script for use with a macro mod.

Each pass fills one band, so together they fill the shape exactly once.

#### Duplicates

With **Dupes on**, a long strip often collapses onto a few blocks: neighbouring
steps are only a few deltaE apart, and one flat block can be the best fit for
several of them at once. Turning Dupes **off** reserves every block the strip has
already used, plus all the waypoints, so each slot gets one of its own. The Result
header says how many distinct blocks the strip actually holds.

#### Blend settings and the shape gradient

The Blend controls on the Gradient tab drive **both** the wall preview and the
Generate tab's shape gradient, so what the preview shows is what a `//g` build
gets. `wall H` is the exception — it is the wall's layer count, which a 3D shape
has no equivalent for.

The match is in character, not cell for cell. WorldEdit's expression language has
no noise function and no per-block randomness, so the noise is a sum of sines:
`scale` sets their frequency, `octaves` adds finer terms on top, `Reroll seed`
picks a different frequency mix, and `randomness` scales how far a block can move
from its band — the same ±3 steps at 100% that the preview uses. `DITHER` is the
same noise at a much higher frequency, which is the nearest an expression gets to
per-cell scatter. `MAP` drops the gradient axis entirely and slices on the noise
alone.

Only the first two octaves are emitted. A third costs about twenty characters in
every pass, and the limit below is what that spends.

#### Bands follow the shape, not the selection

`//g` normalises coordinates to the **selection**, but a shape rarely fills it: a
sphere of radius 0.8 only reaches `y = +-0.8`. Bands spread over the full `-1..1`
would leave the first and last gradient blocks with nowhere to go, so a strip of 11
would build out of 9. The Generate tab measures how far the shape actually reaches
along the gradient axis - the 3D preview already samples exactly that - and lays the
bands across that range instead. Every block of the gradient gets used.

#### The chat character limit

Minecraft truncates a chat message at **100 characters** — silently, so an
over-long `//g` arrives as a valid-looking prefix and WorldEdit builds something
wrong out of it. GTNH ships Hodgepodge, whose `longerSentMessages` raises that to
**256**; `chatCharLimit` in this mod's config must match whatever your server
allows. Passes longer than the limit are refused rather than sent half-written,
and the Generate tab shows the longest pass against the limit as you work.

Every emitted character is therefore rationed: rotation drops identity rows, zero
terms and unit coefficients, numbers lose their leading zero, the noise seed rides
in the frequencies rather than in phase terms, and the shape is inlined instead of
being assigned to a variable first. A torus at full three-axis rotation went from
308 characters a pass to 198 that way.

What fits, across all 38 presets and all four blend modes:

| | unrotated | one axis | all three |
|---|---|---|---|
| 1 octave | all fit | all fit | ~13% overflow |
| 2 octaves | all fit | ~2% overflow | ~30% overflow |

The overflows are the same handful of long presets — Spiral Stair, Mega Fancy
Cube, Boulder, Lollipop Tree. Rotating on one axis rather than three is what buys
the most room.

- **Axis** - `Y`, `X`, `Z`, or `RADIAL`, which fades outward from the centre and
  suits spheres and tori. Ignored in `MAP` mode, which slices on noise instead.
- **dither** - lets band edges wander so the transitions interlock instead of
  showing hard rings. The expression language has no noise function, so this is a
  sum of three incommensurate sines.

Assignments to `data` in the shape expression are dropped from gradient passes,
since they would fight the metadata of the block each pass places.

**Use as pattern** is the older behaviour: it sets the `//g` pattern to a weighted
mix. WorldEdit places a weighted pattern at random, so that speckles the whole
shape rather than fading across it. Use it when you want a blended texture, not a
gradient.

### Limiting the gradient length

The `Len` control in the **Strip** section caps how many blocks the gradient runs
through, from 2 to 256. Stone to obsidian at `Len 5` gives five steps total,
including both ends.

## Settings

In game: **Mods - BuildersToolkit - Config**, or edit `config/builderstoolkit.cfg`. Everything is client-side and applies on the next frame; no restart.

**appearance** holds the theme and the inventory button.

`showInventoryButton` toggles the **G** button on the survival inventory.
`buttonX` / `buttonY` are its offset from the inventory's top-left corner, so it
follows the GUI when other mods move it. You rarely need to type those: hit **Move G** in the top-right of any toolkit
screen (or shift-click the G button itself) to open a drag-to-place editor showing
a stand-in inventory at its real size. Drag the button where you want, then Save.

**theme** picks one of four looks:

| Theme | Look |
| --- | --- |
| `SLATE` | Dark slate panels with grouped wells (default) |
| `BLUEPRINT` | Outlined widgets, hairline rules, cyan accent |
| `VANILLA` | Stone grey with beveled buttons, matches vanilla GUIs |
| `CONTRAST` | Near black and greyscale with a single amber accent |

Themes differ by more than colour: each also selects a widget style (filled, outlined
or beveled), so slots and buttons are drawn differently, not just recoloured.

**worldedit** controls the tabs that exist only to build WorldEdit commands:

| Option | Effect |
| --- | --- |
| `worldEditTools` | Master switch. Off hides both Generate and Lab. |
| `generateTab` | Show the Generate tab (shape presets). |
| `labTab` | Show the Lab tab (parametric sandbox). |

Hidden tabs are removed from the tab strip, which then redistributes to fill the panel
width, so there are no gaps.

## Build

Requires **JDK 17**. The project uses the GTNH Gradle convention plugin, the
same buildscript GTNH mods use, so it produces a jar that drops straight into a
GTNH instance.

```bash
./gradlew build
```

The jar lands in `build/libs/`. To launch a dev client: `./gradlew runClient`.

The first run downloads Gradle, Forge 10.13.4.1614 and the MCP mappings, then
decompiles Minecraft. Expect it to take a while.

### Java version

`enableModernJavaSyntax = jabel` in `gradle.properties` compiles with a **Java
17 toolchain** but emits **Java 8 bytecode**, which 1.7.10 requires. GTNH itself
runs on Java 17+ via lwjgl3ify, so the jar loads fine there.

The source deliberately sticks to Java 8 *syntax* and the Java 8 standard
library. Jabel only backports syntax, not APIs — `String.isBlank()`, `List.of()`
and friends would compile and then fail at runtime on a Java 8 stdlib. Keeping
the syntax conservative also means the build works whether or not Jabel is
enabled.

### Formatting

The GTNH convention runs Spotless. If the build fails on formatting:

```bash
./gradlew spotlessApply
```

## Not ported from 1.18.2

- **WorldEdit `//smooth` integration** (`/verticalsmooth`, `/smartsmooth`) was
  removed — it is being split into a separate mod. The code is still in the
  `1.18.2` branch history if you want to lift it.
- **JEI integration** was replaced with NEI, which is what 1.7.10 and GTNH use.
  The screens extend `GuiContainer` over an empty container so NEI shows its item
  panel, and an `INEIGuiHandler` accepts blocks dragged out of that panel into the
  waypoint and palette slots. NEI is a compile-time-only dependency, so the mod
  still runs without it.

  `ToolkitNEIHandler` implements every `INEIGuiHandler` method, not just the drag
  hook. Recent NEI builds give them defaults, but older ones declare them all
  abstract, and inheriting a default that isn't there throws `AbstractMethodError`
  the moment any inventory opens.

  The container is inert by design: `handleMouseClick` and `onGuiClosed` are both
  overridden to do nothing, because `GuiContainer` would otherwise send
  window-click packets against the real player inventory and drop whatever is on
  the cursor when the screen closes.

## Possible tweaks

- Switch CIE76 (`Lab.deltaE`) to CIEDE2000 for slightly better matches.
- Weight waypoint spacing by colour distance instead of spacing evenly.
- Add NEI ghost-ingredient support to the Gradient and Palette tabs.
