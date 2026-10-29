package com.rossomak.morpheus.samples.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.LocalMorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground

/** A [spec]'s graph over the whole surface, in the inherited fill unless [fill] is given. A fill set on the spec still wins. */
@Composable
internal fun SampleGraph(spec: MorpheusGraphSpec, modifier: Modifier = Modifier, fill: MorpheusGraphFill = LocalMorpheusGraphFill.current) {
    Box(modifier.fillMaxSize().morpheusGraphBackground(spec, fill))
}
