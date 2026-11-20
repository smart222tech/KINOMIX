package com.ivor.openstream.domain.model

/**
 * A title discovered on a streaming source. It deliberately has no TMDB/IMDb identifier:
 * the canonical identity is the source page URL, while title/year are used for matching
 * the same work across independent sources.
 */
data class SourceCatalogTitle(
    val title: String,
    val pageUrl: String,
    val sourceId: String,
    val sourceName: String,
    val posterUrl: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val mediaKind: SourceMediaKind = SourceMediaKind.UNKNOWN
) {
    val stableId: String
        get() = pageUrl.trimEnd('/').lowercase()

    val normalizedTitle: String
        get() = title.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}

enum class SourceMediaKind { MOVIE, SERIES, UNKNOWN }

/** A playable result or an embeddable page discovered from the title's own source page. */
data class SourcePlaybackOption(
    val sourceId: String,
    val sourceName: String,
    val titlePageUrl: String,
    val playbackUrl: String,
    val kind: PlaybackLinkKind,
    val mimeType: String? = null,
    val referer: String? = null
)

enum class PlaybackLinkKind { DIRECT_FILE, HLS, DASH, EMBED_PAGE }
