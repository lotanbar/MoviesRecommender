package com.moviesrecommender.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.moviesrecommender.data.remote.tmdb.MediaType
import com.moviesrecommender.data.remote.tmdb.Title
import com.moviesrecommender.ui.theme.StarYellow

@Composable
fun PreviewScreen(
    navController: NavHostController,
    tmdbId: Int,
    mediaType: String
) {
    val viewModel: PreviewViewModel = viewModel(
        factory = PreviewViewModelFactory(tmdbId, mediaType)
    )
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is PreviewUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is PreviewUiState.Error -> {
                Text(
                    text = state.message,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
            }
            is PreviewUiState.Loaded -> {
                LoadedContent(
                    state = state,
                    onToggleInList = viewModel::toggleInList,
                    onSetRating = viewModel::setRating,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: PreviewUiState.Loaded,
    onToggleInList: () -> Unit,
    onSetRating: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = state.title
    val context = LocalContext.current
    fun openUrl(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LazyColumn(modifier = modifier) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = title.posterUrl(500),
                    contentDescription = title.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = title.year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MediaTypePill(title.mediaType)
            }
        }
        item {
            IconRow(
                title = title,
                wikipediaUrl = state.wikipediaUrl,
                wikipediaReady = state.wikipediaReady,
                onOpenUrl = ::openUrl
            )
        }
        if (title.genres.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    title.genres.forEach { genre ->
                        AssistChip(onClick = {}, label = { Text(genre) })
                    }
                }
            }
        }
        title.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            item {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        item {
            CreditsSection(title)
        }
        item {
            RatingSection(
                rating = state.rating,
                isUploading = state.isUploading,
                uploadError = state.uploadError,
                onToggleInList = onToggleInList,
                onSetRating = onSetRating
            )
        }
    }
}

@Composable
private fun IconRow(
    title: Title,
    wikipediaUrl: String?,
    wikipediaReady: Boolean,
    onOpenUrl: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val query = Uri.encode("${title.title} ${title.year} trailer")
        AssistChip(
            onClick = { onOpenUrl("https://www.youtube.com/results?search_query=$query") },
            label = { Text("Trailer") },
            leadingIcon = {
                Icon(Icons.Default.PlayArrow, contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
        when {
            !wikipediaReady -> AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Wikipedia") },
                leadingIcon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                        strokeWidth = 2.dp
                    )
                }
            )
            wikipediaUrl != null -> AssistChip(
                onClick = { onOpenUrl(wikipediaUrl) },
                label = { Text("Wikipedia") },
                leadingIcon = {
                    Icon(Icons.Default.Language, contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize))
                }
            )
        }
        title.imdbId?.let { imdbId ->
            AssistChip(
                onClick = { onOpenUrl("https://www.imdb.com/title/$imdbId") },
                label = { Text("IMDb") },
                leadingIcon = {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize))
                }
            )
        }
    }
}

@Composable
private fun MediaTypePill(mediaType: MediaType) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (mediaType == MediaType.TV) "TV" else "Film",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun CreditsSection(title: Title) {
    val lines = buildList {
        title.director?.let { add("Director: $it") }
        if (title.leadActors.isNotEmpty()) add("Starring: ${title.leadActors.joinToString(", ")}")
        val meta = listOfNotNull(title.country, title.productionCompany).joinToString(" · ")
        if (meta.isNotBlank()) add(meta)
    }
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RatingSection(
    rating: Int?,
    isUploading: Boolean,
    uploadError: Boolean,
    onToggleInList: () -> Unit,
    onSetRating: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onToggleInList, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (rating != null) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (rating != null) "Remove from list" else "Add to list",
                    tint = if (rating != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            if (rating != null) {
                Spacer(Modifier.width(4.dp))
                (1..4).forEach { star ->
                    IconButton(
                        onClick = { onSetRating(if (rating == star) 0 else star) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "$star stars",
                            tint = if (star <= rating) StarYellow
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        when {
            isUploading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    "Saving…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            uploadError -> Text(
                text = "Upload failed — tap to retry",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
