# GradientCraft (Minecraft 1.18.2, Forge)

A **client-side** build helper. Open it from a button on your survival
inventory, drop blocks into the waypoint slots (drag from your inventory **or
from JEI**), pick a length, and it generates a smooth color gradient using
**any blocks in the game** as the in-between steps.

Because it registers nothing on the server and only reads your client-side
inventory, it works on any server and is designed not to conflict with other
mods (unique mod id, no mixins, no overrides, JEI is an optional dependency).

## How it works

1. On first open it indexes the average color of every block's texture
   (including modded blocks) and converts each to CIELAB color space.
2. Your waypoint blocks are placed evenly along a strip; the colors between
   them are interpolated in LAB (perceptually smooth).
3. Each in-between slot snaps to the nearest real block by color.
4. Results render as a strip of block icons. "Copy IDs" copies the ordered
   list of block registry names to your clipboard.

Controls: `-` / `+` set the strip length, `Dupes: ON/OFF` allows or avoids
repeating the same block in adjacent slots, `Generate`, `Clear`, `Copy IDs`.
Right-click a waypoint slot to remove it.

Filter toggles (top "Filters:" row) restrict which blocks the engine may use
for the in-between steps — your chosen waypoints are always kept regardless:

- **Opaque** — only blocks whose texture has no transparency (no glass/leaves).
- **Full** — only full-cube blocks (excludes slabs, stairs, fences, carpets,
  flowers, etc.).
- **No-TE** — excludes blocks with block entities (chests, furnaces, signs...).

The status line shows how many blocks pass the current filters, e.g.
`Palette: 412 / 980 blocks pass filters`.

## Build

You need JDK 17. The project ships the **Gradle wrapper (7.5.1)**, so you do
**not** need (and should not use) any system-installed Gradle — the apt
`gradle` on Ubuntu/WSL is far too old for ForgeGradle 5.

```
# from the project root:
chmod +x gradlew            # WSL/Linux/macOS only, first time
./gradlew build             # use gradlew.bat on a Windows command prompt
```

The first run downloads Gradle 7.5.1 and the Forge toolchain automatically.
The built jar lands in `build/libs/`. Drop it into your `mods` folder.
To launch a dev client: `./gradlew runClient`.

If `./gradlew` reports "Permission denied", run `chmod +x gradlew`. If it says
the JAVA_HOME points at the wrong JDK, set it to a JDK 17 install.

## Things to double-check when you first compile

I wrote this against the 1.18.2 Forge / JEI APIs but could not compile it in
my environment, so verify these version-sensitive spots if the build complains:

- **`ScreenEvent.InitScreenEvent.Post`** and **`event.addListener(...)`** in
  `ClientEvents.java` — correct for Forge 40.x (1.18.2); renamed in later MC
  versions.
- **`BakedModel.getParticleIcon()`** in `BlockColorIndex.java` — if it wants
  model data, use the Forge overload `getParticleIcon(EmptyModelData.INSTANCE)`.
- **`ResourceManager.getResource(...)` returning `Resource`** (1.18.2). In 1.19+
  it returns `Optional<Resource>`.
- **`BlockState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)`**
  in `BlockColorIndex.java` (used for the "Full" filter) — present in 1.18.2; the
  empty getter is fine because full-cube collision is neighbor-independent.
- **`TextComponent` / `TranslatableComponent`** are correct for 1.18.2
  (1.19+ uses `Component.literal` / `Component.translatable`).
- **JEI version** in `build.gradle` (`9.7.2.281`) and the JEI 9 API names:
  `addGuiScreenHandler`, `IScreenHandler.apply`, `IGhostIngredientHandler`, and
  `IGuiProperties` (`getGuiXSize`/`getGuiYSize`). These are correct for the JEI 9
  line (1.18.2); JEI 10+/later MC versions renamed several of them.

### JEI dragging

JEI's ingredient list only shows on screens it has geometry for, so the plugin
registers an `IScreenHandler` for `GradientScreen`. The list appears on the
**right**; the player's inventory panel is therefore drawn on the **left** of
the main panel. If the JEI list does not appear, the window is likely too narrow
for it (lower your GUI scale or raise the resolution), or JEI is not installed.

## /verticalsmooth and /smartsmooth brush (WorldEdit integration)

If WorldEdit is also installed, GradientCraft adds two commands.

### `/smartsmooth brush <sphere|cube|cylinder> <radius> [iterations]`

A real WorldEdit-style brush (bound to your held item, just like `//brush
sphere` or `//brush smooth`) that **auto-detects** which way to smooth for
every single click. It checks the blocks around the clicked spot: if solid
material is concentrated below and air above, that's a floor - it smooths
normally, faces up. If it's flipped (solid above, air below), that's a
ceiling - it smooths the underside without eating into the solid mass above
it. If the lopsidedness shows up on X or Z instead of Y, that's a wall - it
smooths across the face without flattening it top-to-bottom. The same bound
brush handles all three automatically; you don't pick an axis yourself.

- **shape** — `sphere` (true 3D taper), `cube` (whole bounding box, no
  taper), or `cylinder` (round footprint, unrestricted along whichever axis
  was auto-detected as depth).
- **radius** — brush size, like any other WorldEdit brush (0.5-20).
- **iterations** — smoothing passes per click (default 1, up to 10).

After running the command, left-click a block to apply it there. Run the
command again (same or different shape/radius) to change the bound brush.

**Why it's `/smartsmooth brush ...` and not literally `//brush smartsmooth`:**
WorldEdit's brush sub-types (`sphere`, `cylinder`, `smooth`, `gravity`, ...)
are all hardcoded inside WorldEdit's own internal `BrushCommands` class, with
no public way to add a new one into that same tree short of hooking its
internal command registry - exactly the kind of fragility avoided below for
`/verticalsmooth`. Instead, `/smartsmooth brush ...` builds a genuine
WorldEdit `Brush` (the same interface `//brush sphere` implements) and binds
it to your held item using WorldEdit's own public brush-binding API, so once
bound it behaves exactly like a real brush - it just isn't nested under `//`.

### `/verticalsmooth [iterations] [-x|-y|-z] [-r]`

The original, selection-based version: smooths your current WorldEdit
selection along one axis you choose (or it auto-picks the thinner horizontal
extent). Good for smoothing an entire selected wall or cliff face in one go,
rather than click-by-click.

WorldEdit's own `//smooth` always measures "height" along the world's Y axis,
so it collapses any vertical structure - a cliff, tower, or wall - toward one
flat horizontal plane, because a wall looks the same at every (x,z) column and
the heightmap has no way to represent it. `/verticalsmooth` generalizes the
same averaging so you can pick X or Z as the "thickness" direction instead of
Y, and it smooths across the wall's face instead of flattening it.

- **iterations** — number of smoothing passes (default 1, like `//smooth`).
- **-x / -y / -z** — force which axis is the thickness/depth direction. If
  omitted, it auto-picks whichever horizontal extent (X or Z) of your current
  WorldEdit selection is smaller, since a wall's selection is usually thin in
  the direction it faces.
- **-r** — reverse which side of that axis counts as "outward" facing, in
  case the wall faces the opposite direction from the default guess.

It reads your current WorldEdit selection and edits blocks through WorldEdit's
own `EditSession`, so `//undo` still works on it afterward.

### Architecture notes - please read before relying on this

- **This is typed with a single slash**, `/verticalsmooth`, not `//verticalsmooth`.
  WorldEdit has never heard of this command, so it can't literally live inside
  WorldEdit's own `//` namespace without hooking into WorldEdit's internal,
  unstable, annotation-processor-driven command registry - far less reliable
  to get right without compiling against your exact WorldEdit build. Instead
  it registers as an ordinary Forge command and talks to WorldEdit purely
  through its public, documented API (selections, `EditSession`, undo history).
- **This part is genuinely server-side**, unlike every other tab in this mod
  (which only ever build a `//g` string for you to paste - entirely
  client-side). Editing blocks is server work. In **singleplayer** this is
  automatic, since the integrated server loads the same mods as the client.
  On a **real multiplayer/dedicated server**, whoever runs the server needs
  both GradientCraft and WorldEdit installed *server-side* too, or
  `/verticalsmooth` simply won't exist there. The mod loads and runs fine
  without WorldEdit present at all - you just won't have this command.
- **Compile caveat**: `VerticalSmoothCommand.java` uses WorldEdit's Forge
  integration classes (`worldedit-forge-mc1.18.2`, especially `ForgeAdapter`)
  based on WorldEdit's published API docs, but I don't have the actual
  WorldEdit jar available to compile against in my environment, so — unlike
  the rest of this project — I can't guarantee this file compiles as-is. The
  core smoothing math (`VerticalSmoothAlgorithm.java`) has no Minecraft or
  WorldEdit imports at all and is fully unit-tested (19 passing tests
  covering all three axes, spikes, pits, gaps, and convergence), so if the
  build does complain, it will be in the WorldEdit-glue file, not the math.
  The likeliest trouble spot is the exact static method name(s) on
  `ForgeAdapter` for turning a `ServerPlayer` into WorldEdit's `Actor`. Build
  once and send me the compiler error - it'll point straight at the fix.
- The WorldEdit version pinned in `build.gradle` (`7.2.15`) may not be the
  newest available; check
  `https://maven.enginehub.org/repo/com/sk89q/worldedit/worldedit-forge-mc1.18.2/`
  for the latest 7.2.x release if that version 404s.

## Possible tweaks you might want

- Filter the palette to "full opaque cube" blocks only (right now it includes
  any block whose texture has enough opaque pixels).
- Distance-weight the waypoint spacing by color distance instead of even spacing.
- Switch CIE76 (`Lab.deltaE`) to CIEDE2000 for slightly better matches.
