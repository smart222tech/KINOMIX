package com.ivor.openstream.data.streaming.sources

import com.ivor.openstream.domain.model.PlaybackLinkKind
import com.ivor.openstream.domain.model.SourceCatalogTitle
import com.ivor.openstream.domain.model.SourceMediaKind
import com.ivor.openstream.domain.model.SourcePlaybackOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Internet Archive public metadata provider. It indexes material made available by uploaders;
 * rights vary by item, so the UI opens the item's own Archive page and does not claim every
 * result is public domain or licensed for every use.
 */
@Singleton
class InternetArchiveCatalogProvider @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient
) : SourceCatalogProvider {
    override val definition = HtmlSourceDefinition(
        id = "internet-archive",
        name = "Internet Archive",
        baseUrl = "https://archive.org",
        searchUrlTemplate = "https://archive.org/advancedsearch.php?q={query}&fl[]=identifier&fl[]=title&fl[]=year&fl[]=description&rows=30&page=1&output=json",
        cardSelector = "a",
        titleSelector = "a",
        linkSelector = "a"
    )

    override suspend fun search(query: String): List<SourceCatalogTitle> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier,title,year,description&rows=30&page=1&output=json"
        val json = get(url)?.let(::JSONObject) ?: return@withContext emptyList()
        val docs = json.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until docs.length()) {
                val item = docs.optJSONObject(i) ?: continue
                val id = item.optString("identifier").takeIf { it.isNotBlank() } ?: continue
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                add(
                    SourceCatalogTitle(
                        title = title,
                        pageUrl = "https://archive.org/details/$id",
                        sourceId = definition.id,
                        sourceName = definition.name,
                        synopsis = item.optString("description").takeIf { it.isNotBlank() && it != "null" },
                        year = item.optString("year").toIntOrNull(),
                        mediaKind = SourceMediaKind.UNKNOWN
                    )
                )
            }
        }
    }

    override suspend fun inspect(title: SourceCatalogTitle): Pair<SourceCatalogTitle, List<SourcePlaybackOption>> =
        withContext(Dispatchers.IO) {
            if (title.sourceId != definition.id || !title.pageUrl.startsWith("https://archive.org/details/")) {
                return@withContext title to emptyList()
            }
            val identifier = title.pageUrl.substringAfterLast('/')
            val metadata = get("https://archive.org/metadata/$identifier")?.let(::JSONObject)
                ?: return@withContext title to emptyList()
            val files = metadata.optJSONArray("files") ?: return@withContext title to emptyList()
            val options = buildList {
                for (i in 0 until files.length()) {
                    val file = files.optJSONObject(i) ?: continue
                    val name = file.optString("name")
                    val format = file.optString("format").lowercase()
                    val lower = name.lowercase()
                    val kind = when {
                        lower.endsWith(".m3u8") -> PlaybackLinkKind.HLS
                        lower.endsWith(".mpd") -> PlaybackLinkKind.DASH
                        lower.endsWith(".mp4") || lower.endsWith(".ogv") || lower.endsWith(".webm") -> PlaybackLinkKind.DIRECT_FILE
                        else -> null
                    } ?: continue
                    add(
                        SourcePlaybackOption(
                            sourceId = definition.id,
                            sourceName = definition.name,
                            titlePageUrl = title.pageUrl,
                            playbackUrl = "https://archive.org/download/$identifier/" + name.split('/').joinToString("/") { segment ->
                                java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
                            },
                            kind = kind,
                            mimeType = when {
                                lower.endsWith(".m3u8") -> "application/x-mpegURL"
                                lower.endsWith(".mpd") -> "application/dash+xml"
                                lower.endsWith(".webm") -> "video/webm"
                                lower.endsWith(".ogv") -> "video/ogg"
                                else -> "video/mp4"
                            },
                            referer = title.pageUrl
                        )
                    )
                }
            }
            title to options.distinctBy { it.playbackUrl }
        }

    private fun get(url: String): String? = runCatching {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "KINOMIX/1.0 (Android TV; public metadata catalog)")
                .header("Accept", "application/json")
                .build()
        ).execute().use { response -> if (response.isSuccessful) response.body?.string() else null }
    }.getOrNull()
}
