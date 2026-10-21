package com.bakajiten.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.bakajiten.app.domain.SenseDisplay
import com.bakajiten.app.domain.numberedContentSenses

// One search result row: heading + reading headline over the
// compact senses line, as a segmented list item (same idiom as the
// dictionary screen): joined rounded shapes per position over a
// surface container.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultRow(
  index: Int,
  count: Int,
  heading: String,
  reading: String,
  kanaOnly: Boolean,
  chain: List<String>,
  senses: List<SenseDisplay>,
  highlight: String,
  onBadgeTap: (TipTap) -> Unit,
  onOpen: () -> Unit,
) {
  val titleSize = MaterialTheme.typography.titleMedium.fontSize
  val bodySize = MaterialTheme.typography.bodyMedium.fontSize
  val headRead = buildAnnotatedString {
    withStyle(
      SpanStyle(
        fontSize = titleSize,
        color = if (kanaOnly) {
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        } else {
          MaterialTheme.colorScheme.onSurface
        },
      )
    ) {
      append(heading)
    }
    if (reading != heading) {
      append(" ")
      withStyle(SpanStyle(fontSize = bodySize, color = MaterialTheme.colorScheme.primary)) {
        append(reading)
      }
    }
  }
  SegmentedListItem(
    selected = false,
    onClick = onOpen,
    shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
    colors = ListItemDefaults.segmentedColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    supportingContent = {
      if (chain.isNotEmpty()) {
        Text(
          "← ${chain.joinToString(" · ")}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      SensesLine(numberedContentSenses(senses), highlight, onBadgeTap = onBadgeTap)
    },
    modifier = Modifier.testTag("resultRow$index"),
  ) {
    // One line when heading + reading fit, two when they don't.
    Text(
      headRead,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
