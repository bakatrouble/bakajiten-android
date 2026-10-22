package com.bakajiten.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.bakajiten.app.data.CannedShaping

// Search field row: IME kana input plus the handwriting toggle
// (tonal fill while the panel is open). Affix/mode toggles live in
// the options menu. Test tags keep the headless e2e stable.

@Composable
fun SearchFieldRow(
  field: KanaFieldState,
  onRaw: (String) -> Unit,
  meaning: Boolean,
  modifier: Modifier = Modifier,
  // While the handwriting panel is open the keyboard stays down:
  // the field stays editable and focusable (selection keeps
  // working), autofill included — only the auto-show is off.
  showKeyboardOnFocus: Boolean = true,
) {
  OutlinedTextField(
    value = field.field(meaning),
    onValueChange = { field.onInput(it, meaning, onRaw) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = showKeyboardOnFocus),
    placeholder = { Text(if (meaning) "English…" else "かな…") },
    modifier = modifier.testTag("searchField"),
  )
}

@Composable
fun RecognizerToggleButton(recognizerOpen: Boolean, onRecognizer: () -> Unit) {
  if (recognizerOpen) {
    FilledTonalIconButton(
      onClick = onRecognizer,
      modifier = Modifier.testTag("recognizerToggle")
    ) {
      Icon(Icons.Filled.Create, contentDescription = "Hide handwriting")
    }
  } else {
    IconButton(onClick = onRecognizer, modifier = Modifier.testTag("recognizerToggle")) {
      Icon(Icons.Filled.Create, contentDescription = "Show handwriting")
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun SearchFieldRowPreview() {
  com.bakajiten.app.theme.BakajitenTheme {
    val field = KanaFieldState("taberu", CannedShaping::kanaDisplay)
    SearchFieldRow(field = field, onRaw = {}, meaning = false)
  }
}
