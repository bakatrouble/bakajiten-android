package com.bakajiten.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.bakajiten.app.domain.applyKanaEdit
import com.bakajiten.app.domain.romajiDisplay

// IME field state (web `kanaField` parity): a raw romaji buffer
// plus the display cursor. Typing converts through the buffer;
// picks insert at the cursor, replacing any selection, and advance
// past the inserted text. External query changes (candidate picks
// through the ViewModel, scope switches) resync the buffer.

class KanaFieldState(initialRaw: String) {
  var raw by mutableStateOf(initialRaw)
    private set
  var selection by mutableStateOf(TextRange(initialRaw.length))
    private set

  fun display(meaning: Boolean): String = if (meaning) raw else romajiDisplay(raw)

  fun field(meaning: Boolean): TextFieldValue = TextFieldValue(display(meaning), selection)

  fun onInput(value: TextFieldValue, meaning: Boolean, onRaw: (String) -> Unit) {
    val disp = display(meaning)
    if (value.text == disp) {
      selection = value.selection
      return
    }
    val next = if (meaning) value.text else applyKanaEdit(raw, value.text)
    if (next == raw) return
    raw = next
    val shown = display(meaning)
    selection = if (isEndEdit(disp, value.text)) {
      // Typed or deleted at the end: follow to the new end so a
      // pending mora completes in place.
      TextRange(shown.length)
    } else {
      // Mid-string edit: keep the IME cursor where it landed so
      // the user can finish the mora instead of jumping to the end.
      TextRange(
        value.selection.start.coerceIn(0, shown.length),
        value.selection.end.coerceIn(0, shown.length),
      )
    }
    onRaw(next)
  }

  /** True when the edit only appended to or trimmed the end. */
  private fun isEndEdit(old: String, new: String): Boolean =
    (new.length >= old.length && new.startsWith(old)) ||
      (new.length < old.length && old.startsWith(new))

  fun insertChar(ch: String, meaning: Boolean, onRaw: (String) -> Unit) {
    val disp = display(meaning)
    val start = selection.start.coerceIn(0, disp.length)
    val end = selection.end.coerceIn(0, disp.length)
    raw = disp.replaceRange(start, end, ch)
    val shown = display(meaning)
    selection = TextRange((start + ch.length).coerceAtMost(shown.length))
    onRaw(raw)
  }

  fun syncExternal(query: String) {
    if (query != raw) {
      raw = query
      selection = TextRange(query.length)
    }
  }
}
