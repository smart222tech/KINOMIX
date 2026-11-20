package com.ivor.openstream.data.streaming.sources

import com.ivor.openstream.domain.model.SourceCatalogTitle
import com.ivor.openstream.domain.model.SourcePlaybackOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface SourceCatalogProvider {
    val definition: HtmlSourceDefinition
    suspend fun search(query: String): List<SourceCatalogTitle>
    suspend fun inspect(title: SourceCatalogTitle): Pair<SourceCatalogTitle, List<SourcePlaybackOption>>
}

/** HTML-backed provider for a source with a documented/approved public page structure. */
class HtmlSourceCatalogProvider(
    override val definition: HtmlSourceDefinition,
    private val client: OkHttpClient,
    private val parser: HtmlSourceParser = HtmlSourceParser(definition)
) : SourceCatalogProvider {

    override suspend fun search(query: String): List<SourceCatalogTitle> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val html = fetch(definition.searchUrl(query)) ?: return emptyList()
        return parser.parseSearchPage(html, definition.searchUrl(query))
    }

    override suspend fun inspect(title: SourceCatalogTitle): Pair<SourceCatalogTitle, List<SourcePlaybackOption>> {
        require(title.sourceId == definition.id) { "Title belongs to another source" }
        val html = fetch(title.pageUrl) ?: return title to emptyList()
        return parser.parseDetailPage(html, title.pageUrl, title)
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "KINOMIX/1.0 (Android; source catalog)")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }
}
