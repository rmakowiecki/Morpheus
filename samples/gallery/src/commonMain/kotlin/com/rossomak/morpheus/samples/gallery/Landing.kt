@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphComets
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Uniform
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.ViewerNotice
import kotlin.math.max

internal const val MORPHEUS_REPO_URL = "https://github.com/rmakowiecki/Morpheus"

private const val HEADLINE_FIRST = "Animated graph backgrounds for Compose Multiplatform"
private const val HEADLINE_SECOND = "that morph smoothly between shapes."

/** One paragraph, broken into two lines by hand so the break falls between its two halves. */
private const val FLEXIBLE = "Flexible by design: every shape, layer, color and motion is yours to tune.\nEasy to add to any Compose app on Android, desktop and the web."

private const val WIDE_INSTRUCTIONS = "Pick a sample from the list on the left. Each one says what it shows and how the library " +
    "does it, and Tune changes its spec live and copies it as Kotlin."

private const val NARROW_INSTRUCTIONS = "Select a sample below to start."

/**
 * The wide layout's detail pane with no sample open: a calm graph over the whole pane, and over it what Morpheus is,
 * a link to GitHub, the [ViewerNotice] and how to use the gallery. The graph never morphs or flashes, since the notice is read here.
 */
@Composable
internal fun LandingPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        CalmGraph(modifier = Modifier.matchParentSize())
        CentreScrim(modifier = Modifier.matchParentSize())
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val paneHeight = maxHeight
            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.heightIn(min = paneHeight).widthIn(max = 680.dp).padding(horizontal = 32.dp, vertical = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Hero()
                    CentredText(text = WIDE_INSTRUCTIONS, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 520.dp))
                    ViewerNoticeCard(modifier = Modifier.widthIn(max = 560.dp))
                    GitHubButton()
                    VersionLabel()
                }
            }
        }
    }
}

/**
 * The narrow layout's landing page, at the top of the sample list and scrolling away with it: short enough that
 * the first section and its first sample still show on a phone, so the list reads as something to scroll.
 */
@Composable
internal fun LandingHeader(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        CalmGraph(modifier = Modifier.matchParentSize())
        CentreScrim(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "Morpheus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            CentredText(text = "$HEADLINE_FIRST $HEADLINE_SECOND", style = MaterialTheme.typography.bodyMedium)
            // A phone is too narrow for the hand-made break, so the paragraph wraps on its own.
            CentredText(text = FLEXIBLE.replace('\n', ' '), style = MaterialTheme.typography.bodySmall)
            CentredText(text = NARROW_INSTRUCTIONS, style = MaterialTheme.typography.bodyMedium)
            ViewerNoticeCard()
            GitHubButton()
        }
    }
}

@Composable
private fun Hero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Morpheus", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        CentredText(text = HEADLINE_FIRST, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        CentredText(text = HEADLINE_SECOND, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        CentredText(text = FLEXIBLE, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CentredText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text = text, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier)
}

/** The [ViewerNotice] for viewers sensitive to flashing lights, on the landing page so it is read before any sample plays. */
@Composable
internal fun ViewerNoticeCard(modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append("${ViewerNotice.HEADLINE}. ") }
        append("${ViewerNotice.RISK} ${ViewerNotice.PAUSE_HINT}")
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(imageVector = Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GitHubButton(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Button(onClick = { uriHandler.openUri(MORPHEUS_REPO_URL) }, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(imageVector = GitHubMark, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text("View on GitHub")
    }
}

@Composable
private fun VersionLabel(modifier: Modifier = Modifier) {
    Text(
        text = "Version ${GalleryBuildInfo.VERSION}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A dim mesh, drifting and gently waving, in the theme's colors: the library at rest, with no morph, comets, pulses or glows to flash. */
@Composable
private fun CalmGraph(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = remember(colors.primary, colors.tertiary) { MorpheusGraphFill.linear(listOf(colors.primary, colors.tertiary), angleDegrees = 45f) }
    Box(modifier = modifier.morpheusGraphBackground(calmGraphSpec, fill))
}

/** The background color fading out from the middle, so centred text reads over the graph and the graph shows at the edges. */
@Composable
private fun CentreScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier.drawBehind {
            val brush = Brush.radialGradient(
                colors = listOf(background.copy(alpha = 0.75f), background.copy(alpha = 0.45f), Color.Transparent),
                center = center,
                radius = max(size.width, size.height) * 0.6f,
            )
            drawRect(brush)
        },
    )
}

private val calmGraphSpec = MorpheusGraphSpec(
    seed = 7,
    layers = listOf(
        MorpheusGraphLayerSpec(
            shapes = listOf(Uniform()),
            density = 22f,
            driftAmplitude = 0.5f,
            driftSpeed = 1f,
            waveAmplitude = 0.04f,
            waveSpeed = 0.35f,
            edgeAlpha = 0.5f,
            pulseDepth = 0f,
            glowStrength = 0f,
            comets = MorpheusGraphComets(strength = 0f),
            alpha = 0.55f,
        ),
    ),
)

/** GitHub's mark (the Octicons `mark-github` path), which Material's icons don't carry. */
private val GitHubMark: ImageVector = ImageVector.Builder(name = "GitHubMark", defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 16f, viewportHeight = 16f)
    .addPath(
        pathData = addPathNodes(
            "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94" +
                "-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07" +
                "-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27" +
                ".68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95" +
                ".29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z",
        ),
        fill = SolidColor(Color.Black),
    )
    .build()
