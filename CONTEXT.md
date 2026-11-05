# Morpheus

Compose Multiplatform libraries that draw an animated graph behind content and morph it from one shape to another.

## Language

**Graph**:
What Morpheus draws: a stack of meshes, back to front, animated; the one name for what the library produces, in prose and in the API (`MorpheusGraph…`). Unrelated to charts.
_Avoid_: Network, network graph, background (that is where a graph is drawn, not what it is)

**Spec**:
The full description of one graph: its layers, a seed and an optional fill that its layers can override.
_Avoid_: Config, settings

**Seed**:
The number every random choice of a graph derives from: each cell's home, drift and roll. Layer seeds count from the front layer, so adding a back layer leaves the front one's layout alone.
_Avoid_: Random, noise

**Layer**:
One depth plane inside a spec, as described: its own cell density, shapes, fill chance, edge rule, motion, glow and fill. A layer is the description and draws one mesh. A spec's layers draw back to front.
_Avoid_: Level

**Mesh**:
What a layer draws: its active cells as nodes, joined by edges under its edge rule, over the union of its shapes. One mesh can be the union of several shapes, all over that layer's single cell grid, so overlapping shapes draw as one mesh, not several. Meshes are independent of each other, and a graph is its meshes drawn back to front. A layer is the description; its mesh is the result.
_Avoid_: Layer (for the drawn result)

**Cell** (grid cell):
One square of a layer's fixed, seeded grid, and a potential node. The grid's resolution is the layer's cell density (`density` in code): the number of cells along the longer side of the window. A cell has a fixed home, its rest position, which the seed places and the layer's jitter sets: how far the home sits from the cell's center, in cells. It also has a fixed random roll from 0 to 1. An inactive cell stays a grid cell; once it becomes active it is a node of the layer's mesh. It is active while the density of the layer's shapes at its home, times the layer's fill chance, passes its roll. This is decided every frame, so a morph switches cells on and off.
_Avoid_: Slot

**Node**:
A grid cell that has become active, as its mesh shows it: a dot joined to others by edges. An inactive cell is not a node. It keeps its home in its cell and drifts around it. A cell also has to be present enough to be drawn, and its node needs two visible edges; the edge rule repairs or prunes one that has fewer.
_Avoid_: Vertex, point

**Drift**:
The animated wander of a node around its home, set per layer by its drift amplitude (`driftAmplitude`, in cells) and drift speed (`driftSpeed`). The amplitude stays under one cell so nodes don't clump.
_Avoid_: Jitter (that is where the home sits, fixed), wobble

**Wave**:
A travelling vertical displacement of every home in a layer, as a fraction of the surface's height (`waveAmplitude`), moving at `waveSpeed`. It adds to each node's drift. At 0 the homes stay still.
_Avoid_: Ripple, oscillation

**Edge**:
A line joining two nodes of one mesh. An edge that enters or leaves the layer's edge set fades in or out, so nothing pops.
_Avoid_: Link, connection

**Edge pulse**:
A layer's edges dimming toward a floor and back, over and over (`pulseDepth`: 0 keeps them steady, 1 dims them all the way to the floor).
_Avoid_: Flicker, blink

**Edge rule**:
The rule that decides which pairs of a mesh's nodes get an edge. `Proximity` joins every pair within reach, every frame. `FixedTriangulation` triangulates once over the nodes' homes and keeps the edges while the nodes drift. `LivingDelaunay` triangulates again every frame and fades the edges that flip.
_Avoid_: Edge mode, connection rule

**Shape**:
One density field over the surface: a pill, ribbon, bloom, ring, rect, triangle or uniform field (`ShapePrimitive` in code). A layer holds up to six, and their union is the layer's density.
_Avoid_: Primitive (in prose)

**Union**:
How a layer combines its shapes: at each point, the strongest of them, by weight. Overlapping shapes merge into one denser region, not a sum.
_Avoid_: Compound, merge, sum

**Arc**:
A ring whose sweep is under a full turn, with a round cap at each end. Not a shape of its own: it is a `Ring` with a `sweep` under 360.
_Avoid_: Curve, segment

**Density**:
The number from 0 to 1 that a layer's shapes together give a point of the surface: how crowded with nodes that spot should be. Outside every shape a small floor (`envelopeFloor` in code) leaves a few stray nodes, so a shape has no hard border. Not the layer's cell density, which sets the grid's resolution.
_Avoid_: Weight, intensity

**Preset**:
A ready-made spec shipped in `MorpheusGraphPresets`.
_Avoid_: Theme, template

**Fill**:
The colour source of a graph or of one layer: solid, linear, radial or a custom brush. A layer's own fill wins over its spec's.
_Avoid_: Palette, gradient (a gradient is one kind of fill), fill chance (unrelated to colour)

**Brush**:
What a fill paints a layer's nodes and edges with. A custom fill (`MorpheusGraphFill.Custom`) is asked for a brush once per layer, every frame, with the layer's index and the time, so it can tint by depth or animate.
_Avoid_: Shader (one kind of brush), paint

**Fill chance**:
A layer's ceiling on how many cells may activate: a multiplier from 0 to 1 on the density at each home (`fillChance` in code). At 1 a cell activates wherever its roll is under the density; below 1, fewer do. Comets use it too, as the share of free-running cells they light. Not a Fill.

**Glow**:
The soft light drawn around a hot node, set per layer by its glow radius and glow strength. `Halo` is the code name only.
_Avoid_: Halo (in prose)

**Hot node**:
A node that is larger, fully opaque and glows. A layer makes a share of its nodes hot. Nodes heat up and cool down gradually, and a comet heats the nodes it passes.

**Surface**:
The area a graph is drawn in: the size of the composable its modifier is on. Shapes are laid out over it, as fractions of its width, height or longer side.
_Avoid_: Canvas, background (that is what a graph is drawn behind)

**Window**:
The reference size a layer's grid is built from, normally the app window, or the surface itself when none is given. Its longer side is divided into cells, so the surface can resize without changing the cell size: a bigger surface adds cells and a smaller one fades the nodes outside it.
_Avoid_: Viewport

## Morphing

**Morph**:
A graph changing from one spec to another over time.
_Avoid_: Transition, tween

**Morph family**:
Layers that share a seed, cell density, jitter, edge rule and strict triangles, so their grids are identical and a morph between them keeps every node in place and only fades presence and fill. A morph runs per layer pair: the pair's meshes morph in place if the layers are of one family, and dissolve otherwise.
_Avoid_: Family (alone)

**Follow**:
A morph state that tracks a fractional position across several specs (`Follow` in code), such as a pager's offset. Between two whole positions it shows a morph part way. It moves at a limited pace, so a fast change of position leaves the graph trailing behind.
_Avoid_: Scrub, sync

**Retarget**:
A new target given in the middle of a morph. The new morph starts from the screen as it is, not from either end, with the interrupted morph nested as its start, so nothing jumps.
_Avoid_: Interrupt, restart

**Presence**:
How much of a node shows, from 0 to 1. Outside a morph it rises from 0 to 1 as the density at the node's home passes its roll, over a short band, so a node fades instead of popping. In a morph each node takes its presence at the two ends and switches between them at its own point in the morph.
_Avoid_: Opacity, visibility

**Dissolve**:
What a morph does to a layer pair of different families. Their grids differ, so no cell is shared: the old mesh's nodes fade out and the new mesh's fade in along one sweep across the surface.
_Avoid_: Crossfade

**Comet**:
A bright head that crosses a graph during a morph, carrying light from the nodes only the old shape shows to the nodes only the new shape shows. It lights the cells it passes that neither end shows, each once. Comets do the most work when the two shapes share no nodes, because nothing else links the old shape to the new one. A spark is a comet's flash at a morph's ends.
_Avoid_: Particle

## Samples

**Sample**:
One entry of the samples' registry: a composable showing one capability, with the specs and morphs the gallery can tune. Samples are never published.
_Avoid_: Demo, example

**Gallery**:
The app that runs every sample, on desktop and in the browser, where a viewer tunes a sample and copies its specs as Kotlin.
_Avoid_: Demo app, catalog

**Section**:
One group of samples in the gallery's list: Showcase, Shapes, Meshes, Fills or Morphs, in that order (`SampleSection` in code). Each sample belongs to exactly one.
_Avoid_: Category

**Showcase**:
The section of samples that earn their place by how they look rather than by one capability.

**Summary**:
One sentence about a sample, shown under its title in the gallery's list.
_Avoid_: Subtitle

**Description**:
Two to four sentences about a sample, shown under its title when it is open: what you see, then how the library does it, in this glossary's terms.

**Capture**:
A sample rendered headlessly to a media file, by its id, at the defaults of its specs.
_Avoid_: Recording, screenshot
