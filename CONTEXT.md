# Morpheus

Compose Multiplatform libraries that draw an animated graph behind content and morph it from one shape to another.

## Language

**Graph**:
The animated field of nodes joined by edges that Morpheus draws; the one name for what the library produces, in prose and in the API (`MorpheusGraph…`). Unrelated to charts.
_Avoid_: Network, network graph, background (that is where a graph is drawn, not what it is), mesh (only the triangulated edges of a graph, as in an edge rule)

**Spec**:
The full description of one graph: its layers, a seed and an optional fill that its layers can override.
_Avoid_: Config, settings

**Seed**:
The number every random choice of a graph derives from: each cell's home, drift and roll. Layer seeds count from the front layer, so adding a back layer leaves the front one's layout alone.
_Avoid_: Random, noise

**Layer**:
One depth plane inside a spec: an independent set of nodes and edges, with its own grid of cells, shapes and edge rule. A spec's layers draw back to front.
_Avoid_: Level

**Cell** (grid cell):
One square of a layer's fixed, seeded grid, and a potential node. The grid's resolution is the layer's cell density (`density` in code): the number of cells along the window's longer side. A cell has a fixed home (its rest position), a small drift around it and a fixed random roll from 0 to 1. An inactive cell stays a grid cell; once it becomes active it is a node of the layer's graph. It is active while the density of the layer's shapes at its home passes its roll. This is decided every frame, so a morph switches cells on and off.
_Avoid_: Slot

**Node**:
A grid cell that has become active, as the graph shows it: a dot joined to others by edges. An inactive cell is not a node. It keeps its home in its cell and drifts a little around it. A cell also has to be present enough to be drawn, and its node needs two visible edges; the edge rule repairs or prunes one that has fewer.
_Avoid_: Vertex, point

**Edge**:
A line joining two nodes of one layer. An edge that enters or leaves the layer's edge set fades in or out, so nothing pops.
_Avoid_: Link, connection

**Edge rule**:
The rule that decides which pairs of a layer's nodes get an edge. `Proximity` joins every pair within reach, every frame. `FixedTriangulation` triangulates once over the nodes' homes and keeps the edges while the nodes drift. `LivingDelaunay` triangulates again every frame and fades the edges that flip.
_Avoid_: Edge mode, connection rule

**Shape**:
A density field over the surface, such as a pill, ribbon, bloom, ring, rect or triangle. A layer combines its shapes by taking the strongest at each point. `ShapePrimitive` is the code name only.
_Avoid_: Primitive (in prose)

**Density**:
The number from 0 to 1 that a layer's shapes give a point of the surface: how crowded with nodes that spot should be. Outside every shape a small floor leaves a few stray nodes. Not the layer's cell density, which sets the grid's resolution.
_Avoid_: Weight, intensity

**Preset**:
A ready-made spec shipped in `MorpheusGraphPresets`.
_Avoid_: Theme, template

**Fill**:
The colour source of a graph or of one layer: solid, linear, radial or a custom brush. A layer's own fill wins over its spec's.
_Avoid_: Palette, gradient (a gradient is one kind of fill)

## Morphing

**Morph**:
A graph changing from one spec to another over time.
_Avoid_: Transition, tween

**Morph family**:
Layers that share a seed, cell density, jitter, edge rule and strict triangles, so their grids are identical and a morph between them keeps every node in place and only fades presence and fill. A morph runs per layer pair: a pair of one family morphs in place, any other pair dissolves.
_Avoid_: Family (alone)

**Presence**:
How much of a node shows, from 0 to 1. Outside a morph it rises from 0 to 1 as the density at the node's home passes its roll, over a short band, so a node fades instead of popping. In a morph each node takes its presence at the two ends and switches between them at its own point in the morph.
_Avoid_: Opacity, visibility

**Dissolve**:
What a morph does to a layer pair of different families. Their grids differ, so no cell is shared: the old layer's nodes fade out and the new layer's fade in along one sweep across the surface.
_Avoid_: Crossfade

**Comet**:
A bright head that crosses a graph during a morph, carrying light from the nodes only the old shape shows to the nodes only the new shape shows. It lights the cells it passes that neither end shows, each once. Comets do the most work when the two shapes share no nodes, because nothing else links the old shape to the new one. A spark is a comet's flash at a morph's ends.
_Avoid_: Particle
