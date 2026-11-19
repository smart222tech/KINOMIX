package com.ivor.openstream.data.streaming.sources

import com.ivor.openstream.domain.model.PlaybackLinkKind
import com.ivor.openstream.domain.model.SourceCatalogTitle
import com.ivor.openstream.domain.model.SourcePlaybackOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFirstCatalogTest {
    private class FakeProvider(
        override val definition: HtmlSourceDefinition,
        private val results: List<SourceCatalogTitle>
    ) : SourceCatalogProvider {
        override suspend fun search(query: String): List<SourceCatalogTitle> = results
        override suspend fun inspect(title: SourceCatalogTitle): Pair<SourceCatalogTitle, List<SourcePlaybackOption>> =
            title to listOf(SourcePlaybackOption(definition.id, definition.name, title.pageUrl,
                "https://media.example/${definition.id}.m3u8", PlaybackLinkKind.HLS, referer = title.pageUrl))
    }

    private fun definition(id: String, name: String) = HtmlSourceDefinition(
        id, name, "https://$id.example", "https://$id.example/search?q={query}",
        ".card", ".title", "a"
    )

    @Test
    fun sameTitleOnDifferentSourcesRetainsBothPageIdentities() = kotlinx.coroutines.runBlocking {
        val alpha = definition("alpha", "Alpha")
        val beta = definition("beta", "Beta")
        val catalog = SourceFirstCatalog(listOf(
            FakeProvider(alpha, listOf(SourceCatalogTitle("Example Film", "https://alpha.example/film", "alpha", "Alpha", year = 2024))),
            FakeProvider(beta, listOf(SourceCatalogTitle("Example Film", "https://beta.example/movie", "beta", "Beta", year = 2024)))
        ))
        val results = catalog.search("Example Film")
        assertEquals(2, results.size)
        assertEquals(setOf("alpha", "beta"), results.map { it.sourceId }.toSet())
        assertEquals(2, results.map { it.stableId }.distinct().size)
    }

    @Test
    fun playbackOptionsAreResolvedOnlyByTheTitleSource() = kotlinx.coroutines.runBlocking {
        val alpha = definition("alpha", "Alpha")
        val beta = definition("beta", "Beta")
        val title = SourceCatalogTitle("Example Film", "https://alpha.example/film", "alpha", "Alpha")
        val catalog = SourceFirstCatalog(listOf(FakeProvider(alpha, listOf(title)), FakeProvider(beta, emptyList())))
        val (_, options) = catalog.playbackOptions(title)
        assertEquals(1, options.size)
        assertEquals("alpha", options.single().sourceId)
        assertEquals(title.pageUrl, options.single().titlePageUrl)
    }

    @Test
    fun routerRejectsPlaybackOptionsBelongingToAnotherTitlePage() {
        val title = SourceCatalogTitle("Example Film", "https://alpha.example/film", "alpha", "Alpha")
        val foreign = SourcePlaybackOption("alpha", "Alpha", "https://alpha.example/another-film",
            "https://media.example/video.m3u8", PlaybackLinkKind.HLS)
        assertTrue(SourcePlaybackRouter().routes(title, listOf(foreign)).isEmpty())
    }
}