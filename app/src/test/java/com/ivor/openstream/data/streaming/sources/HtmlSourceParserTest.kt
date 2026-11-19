package com.ivor.openstream.data.streaming.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSourceParserTest {
    private val definition = HtmlSourceDefinition(
        id = "fixture",
        name = "Fixture",
        baseUrl = "https://catalog.example",
        searchUrlTemplate = "https://catalog.example/search?q={query}",
        cardSelector = ".movie",
        titleSelector = ".title",
        linkSelector = "a",
        posterSelector = "img",
        synopsisSelector = ".summary",
        yearSelector = ".year",
        detailTitleSelector = "h1",
        detailSynopsisSelector = ".description",
        detailPosterSelector = ".cover img"
    )

    @Test fun parsesRelativeSearchLinksAndArtwork() {
        val html = """
            <div class="movie">
              <a href="/film/test"><img src="/poster.jpg"><span class="title">Test Film</span></a>
              <span class="year">2024</span><p class="summary">A short summary</p>
            </div>
        """.trimIndent()

        val result = HtmlSourceParser(definition)
            .parseSearchPage(html, "https://catalog.example/search?q=test")

        assertEquals(1, result.size)
        assertEquals("Test Film", result.single().title)
        assertEquals("https://catalog.example/film/test", result.single().pageUrl)
        assertEquals("https://catalog.example/poster.jpg", result.single().posterUrl)
        assertEquals(2024, result.single().year)
    }

    @Test fun rejectsJavascriptAndDataPlaybackUrls() {
        val html = """
            <h1>Test</h1>
            <video><source src="javascript:alert(1)"></video>
            <iframe src="data:text/html,bad"></iframe>
        """.trimIndent()
        val title = com.ivor.openstream.domain.model.SourceCatalogTitle(
            title = "Test", pageUrl = "https://catalog.example/film/test",
            sourceId = "fixture", sourceName = "Fixture"
        )

        val (_, options) = HtmlSourceParser(definition)
            .parseDetailPage(html, title.pageUrl, title)

        assertTrue(options.isEmpty())
    }

    @Test fun identifiesHlsAndEmbedPages() {
        val html = """
            <h1>Test</h1>
            <video><source src="/media/master.m3u8" type="application/x-mpegURL"></video>
            <iframe src="https://player.example/embed/123"></iframe>
        """.trimIndent()
        val title = com.ivor.openstream.domain.model.SourceCatalogTitle(
            title = "Test", pageUrl = "https://catalog.example/film/test",
            sourceId = "fixture", sourceName = "Fixture"
        )

        val (_, options) = HtmlSourceParser(definition)
            .parseDetailPage(html, title.pageUrl, title)

        assertEquals(2, options.size)
        assertEquals(com.ivor.openstream.domain.model.PlaybackLinkKind.HLS, options[0].kind)
        assertEquals(com.ivor.openstream.domain.model.PlaybackLinkKind.EMBED_PAGE, options[1].kind)
    }
}
