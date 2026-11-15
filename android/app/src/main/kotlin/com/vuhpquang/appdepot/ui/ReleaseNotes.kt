package com.vuhpquang.appdepot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vuhpquang.appdepot.ui.theme.DepotTokens

sealed interface NoteItem {
    val text: String
    data class Heading(override val text: String) : NoteItem
    data class Bullet(override val text: String) : NoteItem
    data class Text(override val text: String) : NoteItem
}

private val headingRe = Regex("""^#{1,6}\s+(.*)$""")
private val bulletRe = Regex("""^[-*]\s+(.*)$""")

/** Minimal Markdown: `#`..`######` headings and `- `/`* ` bullets; everything else (HTML too) is plain text. */
fun parseReleaseNotes(md: String): List<NoteItem> = md.split(Regex("\r?\n")).mapNotNull { raw ->
    val line = raw.trim()
    when {
        line.isEmpty() -> null
        else -> headingRe.matchEntire(line)?.let { NoteItem.Heading(it.groupValues[1].trim()) }
            ?: bulletRe.matchEntire(line)?.let { NoteItem.Bullet(it.groupValues[1].trim()) }
            ?: NoteItem.Text(line)
    }
}

@Composable
fun ReleaseNotes(notes: String, modifier: Modifier = Modifier) {
    val items = remember(notes) { parseReleaseNotes(notes) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DepotTokens.SpaceXs)) {
        items.forEachIndexed { i, item ->
            when (item) {
                is NoteItem.Heading -> Text(
                    item.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = if (i > 0) Modifier.padding(top = DepotTokens.SpaceSm) else Modifier,
                )
                is NoteItem.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(DepotTokens.SpaceSm)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                }
                is NoteItem.Text -> Text(item.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
