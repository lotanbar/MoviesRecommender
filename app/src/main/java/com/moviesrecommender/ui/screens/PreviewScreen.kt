package com.moviesrecommender.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.moviesrecommender.R
import com.moviesrecommender.data.remote.tmdb.MediaType
import com.moviesrecommender.data.remote.tmdb.Title

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

    Scaffold(
        bottomBar = {
            val loaded = uiState as? PreviewUiState.Loaded
            if (loaded != null) {
                RatingBottomBar(
                    rating = loaded.rating,
                    isUploading = loaded.isUploading,
                    uploadError = loaded.uploadError,
                    onToggleInList = viewModel::toggleInList,
                    onSetRating = viewModel::setRating
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: PreviewUiState.Loaded,
    modifier: Modifier = Modifier
) {
    val title = state.title
    val context = LocalContext.current
    fun openUrl(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LazyColumn(modifier = modifier) {
        item {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = title.posterUrl(500),
                    contentDescription = title.title,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .width(maxWidth * 0.8f)
                        .padding(top = 16.dp)
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
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val query = Uri.encode("${title.title} ${title.year}")
        IconButton(onClick = { onOpenUrl("https://www.youtube.com/results?search_query=$query") }) {
            Icon(
                painter = painterResource(R.drawable.ic_youtube),
                contentDescription = "YouTube",
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        }
        when {
            !wikipediaReady -> Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            wikipediaUrl != null -> IconButton(onClick = { onOpenUrl(wikipediaUrl) }) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = "Wikipedia",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingBottomBar(
    rating: Int?,
    isUploading: Boolean,
    uploadError: Boolean,
    onToggleInList: () -> Unit,
    onSetRating: (Int) -> Unit
) {
    Surface(tonalElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RatingCircleButton(
                    isActive = rating != null,
                    onClick = onToggleInList
                ) {
                    Icon(
                        imageVector = if (rating != null) Icons.Filled.Bookmark
                                      else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (rating != null) "Remove from wishlist"
                                             else "Add to wishlist",
                        modifier = Modifier.size(22.dp)
                    )
                }
                (1..4).forEach { n ->
                    RatingCircleButton(
                        isActive = rating == n,
                        onClick = { onSetRating(if (rating == n) 0 else n) }
                    ) {
                        Text(text = "$n", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            when {
                isUploading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Text(
                        "Saving…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uploadError -> Text(
                    "Upload failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RatingCircleButton(
    isActive: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val bg = if (isActive) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isActive) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            content()
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
