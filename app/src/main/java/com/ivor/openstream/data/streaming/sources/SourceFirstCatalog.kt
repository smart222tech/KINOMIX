package com.ivor.openstream.data.streaming.sources

import com.ivor.openstream.domain.model.SourceCatalogTitle
import com.ivor.openstream.domain.model.SourcePlaybackOption
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Source-first orchestration: title identity is matched by normalized title/year and every
 * result retains its original page URL. Playback resolution starts from that exact page.
 */
class SourceFirstCatalog(private val providers: List<SourceCatalogProvider>) {

    suspend fun search(query: String): List<SourceCatalogTitle> = coroutineScope {
        providers.map { provider ->
            async { runCatching { provider.search(query) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
            .groupBy { it.normalizedTitle to it.year }
            .map { (_, matches) ->
                // Keep source-specific page records; don't collapse distinct page URLs.
                matches.distinctBy { it.pageUrl }
            }
            .flatten()
            .sortedWith(compareBy<SourceCatalogTitle> { it.normalizedTitle }.thenByDescending { it.year ?: 0 })
    }

    suspend fun playbackOptions(title: SourceCatalogTitle): Pair<SourceCatalogTitle, List<SourcePlaybackOption>> {
        val provider = providers.firstOrNull { it.definition.id == title.sourceId }
            ?: return title to emptyList()
        return provider.inspect(title)
    }
}
