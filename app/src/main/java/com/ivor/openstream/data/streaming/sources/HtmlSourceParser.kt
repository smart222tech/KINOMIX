package com.ivor.openstream.data.streaming.sources

import com.ivor.openstream.domain.model.PlaybackLinkKind
import com.ivor.openstream.domain.model.SourceCatalogTitle
import com.ivor.openstream.domain.model.SourceMediaKind
import com.ivor.openstream.domain.model.SourcePlaybackOption
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Parses ordinary, publicly accessible HTML pages. It does not execute scripts, defeat
 * access controls, solve CAPTCHAs, decrypt DRM or infer hidden API endpoints.
 */
class HtmlSourceParser(private val definition: HtmlSourceDefinition) {

    fun parseSearchPage(html: String, pageUrl: String): List<SourceCatalogTitle> {
        val document = Jsoup.parse(html, pageUrl)
        return document.select(definition.cardSelector).mapNotNull { card ->
            val titleElement = card.selectFirst(definition.titleSelector)
                ?: card.selectFirst(definition.linkSelector)
                ?: return@mapNotNull null
            val linkElement = card.selectFirst(definition.linkSelector) ?: titleElement
            val title = (titleElement.attr("title").ifBlank { titleElement.text() }).trim()
            val href = linkElement.attr("href")
            val canonicalUrl = safeHttpUrl(pageUrl, href) ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null

            val poster = definition.posterSelector?.let { selector ->
                card.selectFirst(selector)?.let { image ->
                    image.attr("data-src").ifBlank {
                        image.attr("data-lazy-src").ifBlank { image.attr("src") }
                    }
                }
            }?.let { safeHttpUrl(pageUrl, it) }

            val yearText = definition.yearSelector?.let { card.selectFirst(it)?.text() }.orEmpty()
            SourceCatalogTitle(
                title = title,
                pageUrl = canonicalUrl,
                sourceId = definition.id,
                sourceName = definition.name,
                posterUrl = poster,
                synopsis = definition.synopsisSelector?.let { card.selectFirst(it)?.text()?.trim() },
                year = YEAR.find(yearText)?.value?.toIntOrNull(),
                mediaKind = inferKind(card, title)
            )
        }.distinctBy { it.pageUrl }
    }

    fun parseDetailPage(html: String, pageUrl: String, knownTitle: SourceCatalogTitle): Pair<SourceCatalogTitle, List<SourcePlaybackOption>> {
        val document = Jsoup.parse(html, pageUrl)
        val title = definition.detailTitleSelector.takeIf { it.isNotBlank() }
            ?.let { document.selectFirst(it)?.text()?.trim() }
            ?.takeIf { it.isNotBlank() } ?: knownTitle.title
        val synopsis = definition.detailSynopsisSelector
            ?.let { document.selectFirst(it)?.text()?.trim() }
            ?.takeIf { it.isNotBlank() } ?: knownTitle.synopsis
        val poster = definition.detailPosterSelector
            ?.let { document.selectFirst(it)?.let(::imageUrl) }
            ?.let { safeHttpUrl(pageUrl, it) } ?: knownTitle.posterUrl

        val enriched = knownTitle.copy(title = title, synopsis = synopsis, posterUrl = poster)
        val options = buildList {
            document.select(definition.videoSelector).forEach { element ->
                val raw = element.attr("src").ifBlank { element.attr("data-src") }
                val url = safeHttpUrl(pageUrl, raw) ?: return@forEach
                val kind = when {
                    url.substringBefore('?').endsWith(".m3u8", true) -> PlaybackLinkKind.HLS
                    url.substringBefore('?').endsWith(".mpd", true) -> PlaybackLinkKind.DASH
                    url.substringBefore('?').matches(Regex(".*\\.(mp4|m4v|webm)$", RegexOption.IGNORE_CASE)) -> PlaybackLinkKind.DIRECT_FILE
                    else -> PlaybackLinkKind.DIRECT_FILE
                }
                add(SourcePlaybackOption(definition.id, definition.name, pageUrl, url, kind, element.attr("type").takeIf { it.isNotBlank() }, pageUrl))
            }
            document.select(definition.iframeSelector).forEach { element ->
                val url = safeHttpUrl(pageUrl, element.attr("src")) ?: return@forEach
                add(SourcePlaybackOption(definition.id, definition.name, pageUrl, url, PlaybackLinkKind.EMBED_PAGE, referer = pageUrl))
            }
        }.distinctBy { it.playbackUrl }

        return enriched to options
    }

    private fun inferKind(card: Element, title: String): SourceMediaKind {
        val hint = (card.className() + " " + card.text() + " " + title).lowercase()
        return when {
            listOf("serie", "staffel", "episode", "season").any(hint::contains) -> SourceMediaKind.SERIES
            listOf("film", "movie", "kino").any(hint::contains) -> SourceMediaKind.MOVIE
            else -> SourceMediaKind.UNKNOWN
        }
    }

    private fun imageUrl(element: Element): String =
        element.attr("data-src").ifBlank { element.attr("src") }

    private fun safeHttpUrl(base: String, raw: String): String? {
        if (raw.isBlank() || raw.startsWith("javascript:", true) || raw.startsWith("data:", true)) return null
        return runCatching {
            val resolved = URI(base).resolve(raw).normalize()
            if (resolved.scheme !in setOf("http", "https") || resolved.host.isNullOrBlank()) null
            else resolved.toASCIIString()
        }.getOrNull()
    }

    private companion object {
        val YEAR = Regex("(?:19|20)\\d{2}")
    }
}
