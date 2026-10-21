package com.bakajiten.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.bakajiten.app.ui.components.KanaFieldState
import org.junit.Assert.assertEquals
import org.junit.Test

// Cursor-aware IME field (web kanaField parity): typing converts
// through the raw buffer, picks insert at the cursor replacing any
// selection and advance past the inserted text.

class KanaFieldTest {
  @Test fun typingConvertsThroughBuffer() {
    val f = KanaFieldState("")
    var queried = ""
    f.onInput(TextFieldValue("t"), false) { queried = it }
    assertEquals("t", f.raw)
    assertEquals("t", queried)
    f.onInput(TextFieldValue("ta"), false) { queried = it }
    assertEquals("ta", f.raw)
    assertEquals("た", f.display(false))
  }

  @Test fun selectionOnlyChangeKeepsRaw() {
    val f = KanaFieldState("taberu")
    var queried = ""
    f.onInput(TextFieldValue("たべる", TextRange(1, 3)), false) { queried = it }
    assertEquals("taberu", f.raw)
    assertEquals("", queried)
    assertEquals(TextRange(1, 3), f.selection)
  }

  @Test fun insertCharReplacesSelectionAndAdvances() {
    val f = KanaFieldState("taberu")
    var queried = ""
    f.onInput(TextFieldValue("たべる", TextRange(1, 3)), false) {}
    f.insertChar("あ", false) { queried = it }
    assertEquals("たあ", f.raw)
    assertEquals("たあ", f.display(false))
    assertEquals(TextRange(2), f.selection)
    assertEquals("たあ", queried)
  }

  @Test fun insertCharAppendsAtEndCursor() {
    val f = KanaFieldState("ta")
    var queried = ""
    f.insertChar("べ", false) { queried = it }
    assertEquals("たべ", f.raw)
    assertEquals(TextRange(2), f.selection)
    assertEquals("たべ", queried)
  }

  @Test fun syncExternalResetsBuffer() {
    val f = KanaFieldState("taberu")
    f.syncExternal("taberu")
    assertEquals(TextRange(6), f.selection)
    f.syncExternal("みず")
    assertEquals("みず", f.raw)
    assertEquals(TextRange(2), f.selection)
  }

  @Test fun midStringEditKeepsCursor() {
    // Typed in the middle: the cursor stays where the IME put it
    // instead of jumping to the end, so the mora can be finished.
    val f = KanaFieldState("taberu")
    var queried = ""
    f.onInput(TextFieldValue("たxべる", TextRange(2)), false) { queried = it }
    assertEquals("たxべる", queried)
    assertEquals("たxべる", f.display(false))
    assertEquals(TextRange(2), f.selection)
  }

  @Test fun meaningModePassesThrough() {
    val f = KanaFieldState("")
    var queried = ""
    f.onInput(TextFieldValue("eat food"), true) { queried = it }
    assertEquals("eat food", f.raw)
    assertEquals("eat food", queried)
    f.insertChar("!", true) { queried = it }
    assertEquals("eat food!", f.raw)
    assertEquals("eat food!", queried)
  }
}
