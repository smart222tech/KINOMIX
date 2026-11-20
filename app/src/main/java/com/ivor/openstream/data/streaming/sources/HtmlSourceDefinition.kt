package com.ivor.openstream.data.streaming.sources

/**
 * CSS selectors are source-specific configuration, not assumptions baked into the parser.
 * A site whose markup changes can be fixed by updating its definition without changing
 * the title model or playback pipeline.
 */
data class HtmlSourceDefinition(
    val id: String,
    val name: String,
    val baseUrl: String,
    val searchUrlTemplate: String,
    val cardSelector: String,
    val titleSelector: String,
    val linkSelector: String,
    val posterSelector: String? = null,
    val synopsisSelector: String? = null,
    val yearSelector: String? = null,
    val detailTitleSelector: String = "h1",
    val detailSynopsisSelector: String? = null,
    val detailPosterSelector: String? = null,
    val videoSelector: String = "video source[src], video[src]",
    val iframeSelector: String = "iframe[src]"
) {
    fun searchUrl(query: String): String =
        searchUrlTemplate.replace("{query}", java.net.URLEncoder.encode(query, Charsets.UTF_8.name()))
}
