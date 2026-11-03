# Morpheus

Compose Multiplatform libraries that draw an animated graph behind content and morph it from one shape to another.

## Language

**Graph**:
The animated field of nodes joined by edges that Morpheus draws; the one name for what the library produces, in prose and in the API (`MorpheusGraph…`). Unrelated to charts.
_Avoid_: Network, network graph, background (that is where a graph is drawn, not what it is), mesh (only the triangulated edges of a graph, as in an edge rule)

**Spec**:
The full description of one graph: its layers, a seed and an optional fill that its layers can override.
_Avoid_: Config, settings

**Layer**:
One depth plane inside a spec: an independent set of nodes and edges, with its own grid of cells, shapes and edge rule. A spec's layers draw back to front.
_Avoid_: Level

**Cell** (grid cell):
One square of a layer's fixed, seeded grid, sized by the layer's density, and a potential node. An inactive cell stays a grid cell; once it becomes active it is a node of the layer's graph. It is active while the density of the layer's shapes at its home passes its own random roll. This is decided every frame, so a morph switches cells on and off.
_Avoid_: Slot

**Node**:
A grid cell that has become active, as the graph shows it: a dot joined to others by edges. An inactive cell is not a node. It keeps its home in its cell and drifts a little around it. A cell also has to be present enough to join the mesh, and its node needs two visible edges; the edge rule repairs or prunes one that has fewer.
_Avoid_: Vertex, point

**Shape**:
A density field over the surface: a number from 0 to 1 at every point, saying how crowded with nodes that spot should be, such as a pill, ribbon, bloom, ring, rect or triangle. A layer combines its shapes by taking the strongest at each point. `ShapePrimitive` is the code name only.
_Avoid_: Primitive (in prose)

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
Layers that share a seed, density, jitter, edge rule and strict triangles, so their grids are identical and a morph between them keeps every node in place and only fades presence and fill. A morph runs per layer pair: a pair of one family morphs in place, any other pair dissolves.
_Avoid_: Family (alone)

**Comet**:
A bright head that crosses a graph during a morph, carrying light from the old shape to the new one. Comets do the most work when the two shapes share no nodes.
_Avoid_: Particle, spark (a spark is a comet's flash at a morph's ends)
