package com.vuhpquang.appdepot.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as the portfolio's depot-release-notes.test.ts. */
class ReleaseNotesTest {
    @Test fun parsesHeadingsAndBulletsInOrder() {
        assertEquals(
            listOf(
                NoteItem.Heading("What's new"),
                NoteItem.Bullet("A"),
                NoteItem.Bullet("B"),
                NoteItem.Heading("Fixes"),
                NoteItem.Bullet("C"),
            ),
            parseReleaseNotes("## What's new\n- A\n- B\n## Fixes\n- C"),
        )
    }

    @Test fun returnsHtmlAsPlainTextNeverParsed() {
        assertEquals(listOf(NoteItem.Text("<script>alert(1)</script>")), parseReleaseNotes("<script>alert(1)</script>"))
        assertEquals(listOf(NoteItem.Bullet("<b>x</b>")), parseReleaseNotes("- <b>x</b>"))
    }

    @Test fun skipsBlankLinesAndHandlesCrlfAndStarBullets() {
        assertEquals(
            listOf(NoteItem.Heading("H"), NoteItem.Bullet("one"), NoteItem.Text("plain")),
            parseReleaseNotes("\r\n## H\r\n\r\n* one\r\nplain\r\n"),
        )
    }

    @Test fun emptyNotesGiveNothing() {
        assertEquals(emptyList<NoteItem>(), parseReleaseNotes("  \n\n"))
    }
}
