# Morpheus

Compose Multiplatform libraries that draw an animated graph behind content and morph it from one shape to another.

## Language

**Graph**:
The animated field of nodes joined by edges that Morpheus draws; the one name for what the library produces, in prose and in the API (`MorpheusGraph…`). Unrelated to charts.
_Avoid_: Network, network graph, background (that is where a graph is drawn, not what it is), mesh (only the triangulated edges of a graph, as in an edge rule)

**Spec**:
The full description of one graph: its layers, a seed and an optional fill.
_Avoid_: Config, settings

**Layer**:
One depth plane of nodes and edges inside a spec.
_Avoid_: Level

**Shape**:
The form a layer's nodes are confined to, such as a pill, ribbon, bloom, ring, rect or triangle. `ShapePrimitive` is the code name only.
_Avoid_: Primitive (in prose)

**Preset**:
A ready-made spec shipped in `MorpheusGraphPresets`.
_Avoid_: Theme, template

**Fill**:
The colour source of a graph: solid, linear, radial or a custom brush.
_Avoid_: Palette, gradient (a gradient is one kind of fill)

## Morphing

**Morph**:
A graph changing from one spec to another over time.
_Avoid_: Transition, tween

**Morph family**:
Specs that share a seed and each layer's density, jitter, edge rule and strict triangles, so a morph between them keeps every node in place and only fades presence and fill. A morph between specs of different families dissolves instead.
_Avoid_: Family (alone)

**Comet**:
A bright head that crosses a graph during a morph, carrying light from the old shape to the new one.
_Avoid_: Particle, spark (a spark is a comet's flash at a morph's ends)
