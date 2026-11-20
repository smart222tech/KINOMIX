package com.ivor.openstream.presentation.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.domain.model.SourceCatalogTitle
import com.ivor.openstream.domain.model.SourcePlaybackOption
import com.ivor.openstream.domain.model.PlaybackLinkKind
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.streaming.sources.SourceFirstCatalog
import com.ivor.openstream.presentation.player.SourcePlaybackActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceCatalogUiState(
    val query: String = "",
    val results: List<SourceCatalogTitle> = emptyList(),
    val selected: SourceCatalogTitle? = null,
    val playback: List<SourcePlaybackOption> = emptyList(),
    val loading: Boolean = false,
    val message: String? = "Search the Internet Archive's public catalog."
)

@HiltViewModel
class SourceCatalogViewModel @Inject constructor(
    private val catalog: SourceFirstCatalog
) : ViewModel() {
    private val _state = MutableStateFlow(SourceCatalogUiState())
    val state: StateFlow<SourceCatalogUiState> = _state.asStateFlow()

    fun updateQuery(value: String) { _state.value = _state.value.copy(query = value) }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank()) {
            _state.value = _state.value.copy(message = "Enter a movie or series title.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, selected = null, playback = emptyList(), message = null)
            val results = catalog.search(query)
            _state.value = _state.value.copy(
                loading = false,
                results = results,
                message = if (results.isEmpty()) "No results. Try another title." else null
            )
        }
    }

    fun select(title: SourceCatalogTitle) {
        viewModelScope.launch {
            _state.value = _state.value.copy(selected = title, loading = true, playback = emptyList(), message = null)
            val (enriched, options) = catalog.playbackOptions(title)
            _state.value = _state.value.copy(
                selected = enriched,
                playback = options,
                loading = false,
                message = if (options.isEmpty()) "No supported direct video file was found. Open the source page to check available formats." else null
            )
        }
    }

    fun clearSelection() { _state.value = _state.value.copy(selected = null, playback = emptyList(), message = null) }
}

@Composable
fun SourceCatalogScreen(
    modifier: Modifier = Modifier,
    viewModel: SourceCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("KINOMIX", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Movies and series by title · source-first catalog", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Movie or series title") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Button(onClick = viewModel::search, enabled = !state.loading) { Text("Search") }
        }
        if (state.selected != null) {
            val title = state.selected!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::clearSelection) { Text("Back to results") }
                Text(title.sourceName, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
            }
            Text(title.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            title.year?.let { Text(it.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            title.synopsis?.let { Text(it, maxLines = 5, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.loading) CircularProgressIndicator()
            state.playback.forEach { option ->
                Button(onClick = {
                    val intent = Intent(context, SourcePlaybackActivity::class.java).apply {
                        putExtra(SourcePlaybackActivity.EXTRA_URL, option.playbackUrl)
                        putExtra(SourcePlaybackActivity.EXTRA_MIME, option.mimeType)
                        putExtra(SourcePlaybackActivity.EXTRA_REFERER, option.referer)
                        putExtra(SourcePlaybackActivity.EXTRA_TITLE, title.title)
                    }
                    runCatching { context.startActivity(intent) }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" Play · ${option.kind.name.replace('_', ' ')}")
                }
            }
            Button(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(title.pageUrl))) }
            }) { Text("Open source page") }
        } else {
            if (state.loading) CircularProgressIndicator()
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.results, key = { it.stableId }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.select(item) }.focusable(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.posterUrl != null) {
                                AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.size(76.dp, 108.dp))
                            } else {
                                Surface(modifier = Modifier.size(76.dp, 108.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
                                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.padding(22.dp))
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(item.year?.toString(), item.sourceName).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Details and playback options", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
