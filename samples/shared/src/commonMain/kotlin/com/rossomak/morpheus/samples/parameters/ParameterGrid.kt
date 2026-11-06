@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.parameters

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleGraph
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.WideCanvas
import com.rossomak.morpheus.samples.mapLayers
import kotlin.math.roundToInt

/** The name every parameter grid gives its one tunable spec. */
internal const val PARAMETER_DISC = "Disc"

private const val MAX_PARAMETER_ROWS = 3

/** A low, a middle and a high value per row. */
private const val PARAMETER_COLUMNS = 3

private val PARAMETER_GRID_SPACING = 8.dp

/** A hard-rimmed, fully filled disc that fits a square tile, with small nodes and neutral looks, so a tile shows only what its row sets. */
internal val parameterDiscSpec = MorpheusGraphSpec(
    seed = 21,
    layers = listOf(
        MorpheusGraphLayerSpec(
            shapes = listOf(Bloom(x = 0.5f, y = 0.5f, reach = 0.36f, plateau = 1f)),
            density = 60f,
            jitter = 0.3f,
            fillChance = 1f,
            envelopeFloor = 0f,
            edgeKeepChance = 1f,
            nodeSizeDp = 1.4f,
            nodeSizeVariance = 0f,
        ),
    ),
)

/** One grid row: the API [names] it sets, one list of values per tile in [values], and how [applyTo] puts them on a layer. */
internal class ParameterRow(val names: List<String>, val values: List<List<Float>>, val applyTo: MorpheusGraphLayerSpec.(List<Float>) -> MorpheusGraphLayerSpec) {
    init {
        require(values.size == PARAMETER_COLUMNS) { "Row $names needs a low, a middle and a high value, not $values" }
        require(values.all { it.size == names.size }) { "Every tile of row $names needs one value per name, not $values" }
    }

    /** Each tile's label: every setting's name and value, on one line. */
    fun label(tileValues: List<Float>): String = names.zip(tileValues).joinToString(", ") { (name, value) -> "$name ${labelValue(value)}" }
}

/** A row that changes the one setting [name] to [values], low to high. */
internal fun parameterRow(name: String, values: List<Float>, applyTo: MorpheusGraphLayerSpec.(Float) -> MorpheusGraphLayerSpec) =
    ParameterRow(listOf(name), values.map { listOf(it) }) { applyTo(it.single()) }

/**
 * One row of tiles per entry of [rows], each tile [spec] with that row's values set on it, labelled inside its
 * corner. Every tile shares the seed, so it differs from its neighbours only in what its row sets.
 */
@Composable
internal fun ParameterGrid(spec: MorpheusGraphSpec, rows: List<ParameterRow>, modifier: Modifier = Modifier) {
    require(rows.size in 1..MAX_PARAMETER_ROWS) { "A parameter grid shows 1 to $MAX_PARAMETER_ROWS rows, not ${rows.size}" }
    SampleGrid(
        modifier = modifier,
        columns = PARAMETER_COLUMNS,
        spacing = PARAMETER_GRID_SPACING,
        labelsInside = true,
        tiles = rows.flatMap { row ->
            row.values.map { tileValues ->
                SampleTile(row.label(tileValues)) { SampleGraph(spec.mapLayers { it.(row.applyTo)(tileValues) }) }
            }
        },
    )
}

/** A [WideCanvas]-wide canvas, so every spec keeps its cell size, as tall as [rows] square tiles need. */
private fun parameterRecipe(rows: List<ParameterRow>): CaptureRecipe {
    val tileSide = (WideCanvas.width - PARAMETER_GRID_SPACING * (PARAMETER_COLUMNS - 1)) / PARAMETER_COLUMNS
    val height = tileSide * rows.size + PARAMETER_GRID_SPACING * (rows.size - 1)
    return CaptureRecipe(canvasSize = DpSize(WideCanvas.width, height))
}

/**
 * A sample in the Basic Parameters section: the grid of [rows] over [spec], its one tunable spec, which [content] draws.
 */
internal fun parameterSample(
    id: String,
    title: String,
    summary: String,
    description: String,
    rows: List<ParameterRow>,
    spec: MorpheusGraphSpec,
    content: @Composable (MorpheusGraphSpec) -> Unit,
) = Sample(
    id = id,
    title = title,
    summary = summary,
    description = description,
    section = SampleSection.BasicParameters,
    galleryOnly = true,
    recipe = parameterRecipe(rows),
    specs = listOf(TunableSpec(PARAMETER_DISC, spec)),
    content = { specs -> content(specs[PARAMETER_DISC]) },
)

/** [value] with at most two decimals and no trailing zeros, the same on every platform. */
private fun labelValue(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${hundredths / 100}" else "${hundredths / 100}.$fraction"
}
