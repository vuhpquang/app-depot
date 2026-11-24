package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.android.HistoryRow
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.core.planFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StageTest {
    private fun rel(v: String) =
        CatalogRelease(version = v, assetName = "a", downloadUrl = "https://x/a", sha256 = "s", size = 10, publishedAt = "2026-11-01T00:00:00Z")

    private fun app(slug: String, platform: String, name: String) =
        CatalogApp(slug = slug, name = name, tagline = "", platform = platform, appId = "com.$slug", repo = "v/$slug", projectSlug = slug, latest = rel("2.0"))

    private val catalog = listOf(
        app("zentask-android", "android", "ZenTask"),
        app("wiki", "android", "Personal Wiki"),
        app("app-depot-android", "android", "App Depot"),
        app("padlet", "macos", "Padlet"),
    )

    private fun sections(installed: Map<String, String>, filter: PlatformFilter = PlatformFilter.All): HomeSections {
        val rows = productRows(catalog, "android") { a ->
            val s = planFor(a, installed[a.slug])
            AppRow(a, s, cardModel(s, null, true, a.latest), true)
        }
        return homeSections(rows, filter, SortOrder.Name, "")
    }

    @Test fun updatesGoOnTheStageAndLeaveTheList() {
        val s = stageSplit(sections(mapOf("zentask-android" to "1.0", "wiki" to "2.0", "app-depot-android" to "1.0")), showStage = true)
        assertEquals(listOf("App Depot", "ZenTask"), s.stage.map { it.app.name })
        assertEquals(listOf("Personal Wiki"), s.sections.local.map { it.app.name })
        assertEquals(listOf("Padlet"), s.sections.other.map { it.app.name })
    }

    @Test fun noStageWhileSearchingOrWithoutUpdates() {
        val withUpdates = sections(mapOf("zentask-android" to "1.0"))
        val hidden = stageSplit(withUpdates, showStage = false)
        assertTrue(hidden.stage.isEmpty())
        assertEquals(withUpdates, hidden.sections)

        val upToDate = sections(mapOf("zentask-android" to "2.0"))
        assertTrue(stageSplit(upToDate, showStage = true).stage.isEmpty())
    }

    @Test fun notInstalledAppsStayInTheList() {
        val s = stageSplit(sections(emptyMap()), showStage = true)
        assertTrue(s.stage.isEmpty())
        assertEquals(3, s.sections.local.size)
    }

    @Test fun groupPositions() {
        assertEquals(GroupPosition.Single, groupPosition(0, 1))
        assertEquals(listOf(GroupPosition.First, GroupPosition.Last), (0..1).map { groupPosition(it, 2) })
        assertEquals(
            listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Middle, GroupPosition.Last),
            (0..3).map { groupPosition(it, 4) },
        )
    }

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun h(id: Long, at: String) = HistoryRow(id, "update", "zentask-android", "1.0", "2.0", null, Instant.parse(at))

    @Test fun historyGroupsByLocalDayKeepingOrder() {
        // 2026-11-23T20:00Z is already 2026-11-24 03:00 in Ho Chi Minh City.
        val rows = listOf(h(4, "2026-11-24T05:00:00Z"), h(3, "2026-11-23T20:00:00Z"), h(2, "2026-11-23T10:00:00Z"), h(1, "2026-11-01T10:00:00Z"))
        val days = historyDays(rows, zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 1)), days.map { it.day })
        assertEquals(listOf(listOf(4L, 3L), listOf(2L), listOf(1L)), days.map { d -> d.rows.map { it.id } })
    }

    @Test fun dayLabels() {
        val today = LocalDate.of(2026, 11, 24)
        assertEquals(DayLabel.Today, dayLabel(today, today))
        assertEquals(DayLabel.Yesterday, dayLabel(today.minusDays(1), today))
        assertEquals(DayLabel.Date, dayLabel(today.minusDays(2), today))
    }
}
