package com.bakajiten.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.bakajiten.app.data.RustKana

// IME field state (web `kanaField` parity): a raw romaji buffer
// plus the display cursor. Typing converts through the buffer;
// picks insert at the cursor, replacing any selection, and advance
// past the inserted text. External query changes (candidate picks
// through the ViewModel, scope switches) resync the buffer.

class KanaFieldState(
  initialRaw: String,
  private val displayFn: (String) -> String = RustKana::display,
) {
  var raw by mutableStateOf(initialRaw)
    private set
  var selection by mutableStateOf(TextRange(initialRaw.length))
    private set

  fun display(meaning: Boolean): String = if (meaning) raw else displayFn(raw)

  fun field(meaning: Boolean): TextFieldValue = TextFieldValue(display(meaning), selection)

  fun onInput(value: TextFieldValue, meaning: Boolean, onRaw: (String) -> Unit) {
    val disp = display(meaning)
    if (value.text == disp) {
      selection = value.selection
      return
    }
    val next = if (meaning) value.text else applyKanaEdit(raw, value.text, displayFn)
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

/**
 * Reconcile one field edit: the editable text is the converted
 * display, so end-insertions arrive as literal suffixes (the typed
 * key, not the converted result) and backspace arrives as a
 * shorter string. End-backspace deletes one whole display
 * character, walking raw back to the longest prefix that still
 * renders it (never un-converting stepwise); any other shape
 * (mid-string edits, selections) resets the buffer to the literal
 * text. Field mechanics over an injected display fn — the
 * conversion itself lives in Rust.
 */
internal fun applyKanaEdit(raw: String, newText: String, display: (String) -> String): String {
  val disp = display(raw)
  if (newText == disp) return raw
  if (newText.length < disp.length) {
    val want = disp.substring(0, disp.offsetByCodePoints(disp.length, -1))
    if (newText == want) {
      for (i in raw.length - 1 downTo 0) {
        if (display(raw.substring(0, i)) == want) return raw.substring(0, i)
      }
      return if (raw.isNotEmpty()) raw.dropLast(1) else raw
    }
    return newText
  }
  if (newText.startsWith(disp)) return raw + newText.substring(disp.length)
  return newText
}
