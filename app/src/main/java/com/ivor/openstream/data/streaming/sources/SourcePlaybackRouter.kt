package com.ivor.openstream.data.streaming.sources

import com.ivor.openstream.domain.model.HLS_MIME_TYPE
import com.ivor.openstream.domain.model.PlaybackLinkKind
import com.ivor.openstream.domain.model.SourceCatalogTitle
import com.ivor.openstream.domain.model.SourcePlaybackOption
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer

/**
 * Keeps the discovered title, its originating page and the selected playback target together.
 * Direct media is handed to Media3; an embed page remains an explicit WebView route and is never
 * incorrectly passed to Media3 as if it were an MP4/HLS stream.
 */
sealed interface SourcePlaybackRoute {
    val title: SourceCatalogTitle
    val option: SourcePlaybackOption

    data class DirectMedia(
        override val title: SourceCatalogTitle,
        override val option: SourcePlaybackOption,
        val server: VideoServer
    ) : SourcePlaybackRoute

    data class EmbeddedPage(
        override val title: SourceCatalogTitle,
        override val option: SourcePlaybackOption
    ) : SourcePlaybackRoute
}

class SourcePlaybackRouter {
    fun routes(
        title: SourceCatalogTitle,
        options: List<SourcePlaybackOption>
    ): List<SourcePlaybackRoute> = options
        .filter { it.sourceId == title.sourceId && it.titlePageUrl == title.pageUrl }
        .distinctBy { it.playbackUrl }
        .map { option ->
            when (option.kind) {
                PlaybackLinkKind.EMBED_PAGE -> SourcePlaybackRoute.EmbeddedPage(title, option)
                PlaybackLinkKind.DIRECT_FILE,
                PlaybackLinkKind.HLS,
                PlaybackLinkKind.DASH -> SourcePlaybackRoute.DirectMedia(
                    title = title,
                    option = option,
                    server = VideoServer(
                        id = "${option.sourceId}-${option.playbackUrl.hashCode()}",
                        providerId = option.sourceId,
                        providerName = option.sourceName,
                        name = option.kind.name.replace('_', ' '),
                        url = option.playbackUrl,
                        quality = StreamQuality.UNKNOWN,
                        audio = StreamAudio.UNKNOWN,
                        headers = option.referer?.let { mapOf("Referer" to it) }.orEmpty(),
                        mimeType = when (option.kind) {
                            PlaybackLinkKind.HLS -> HLS_MIME_TYPE
                            PlaybackLinkKind.DASH -> "application/dash+xml"
                            else -> option.mimeType
                        }
                    )
                )
            }
        }
}
