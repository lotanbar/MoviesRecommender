package com.moviesrecommender.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.moviesrecommender.R
import com.moviesrecommender.data.remote.tmdb.MediaType

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadedContent(
    state: PreviewUiState.Loaded,
    modifier: Modifier = Modifier
) {
    val title = state.title
    val context = LocalContext.current
    fun openUrl(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    var awardsExpanded by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier) {
        item {
            AsyncImage(
                model = title.posterUrl(500),
                contentDescription = title.title,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = title.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = title.year.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MediaTypePill(title.mediaType)
                }
            }
        }
        item {
            Column {
                // Four equal-width action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // YouTube
                    val query = Uri.encode("${title.title} ${title.year}")
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        label = "YouTube",
                        onClick = { openUrl("https://www.youtube.com/results?search_query=$query") }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_youtube),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Wikipedia
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Wikipedia",
                        onClick = { state.wikipediaUrl?.let { openUrl(it) } },
                        enabled = state.wikipediaReady && state.wikipediaUrl != null
                    ) {
                        if (!state.wikipediaReady) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_wikipedia),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(30.dp)
                                    .alpha(if (state.wikipediaUrl != null) 1f else 0.35f)
                            )
                        }
                    }

                    // Awards (Oscar / Emmy)
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        label = if (!state.awardsReady) "Awards"
                                else if (state.awards.isEmpty()) "No wins"
                                else "${state.awards.size} win${if (state.awards.size == 1) "" else "s"}",
                        onClick = { if (state.awardsReady && state.awards.isNotEmpty()) awardsExpanded = !awardsExpanded },
                        enabled = state.awardsReady && state.awards.isNotEmpty(),
                        isActive = awardsExpanded
                    ) {
                        if (!state.awardsReady) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                painter = painterResource(
                                    if (title.mediaType == MediaType.MOVIE) R.drawable.ic_oscar
                                    else R.drawable.ic_emmy
                                ),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(30.dp)
                                    .alpha(if (state.awards.isNotEmpty()) 1f else 0.35f)
                            )
                        }
                    }

                    // Details (genres + country + crew + description)
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Details",
                        onClick = { detailsExpanded = !detailsExpanded },
                        isActive = detailsExpanded
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Expandable: awards list
                AnimatedVisibility(visible = awardsExpanded && state.awardsReady && state.awards.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        state.awards.forEach { award ->
                            Text(
                                text = "· $award",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expandable: details panel
                AnimatedVisibility(visible = detailsExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Country flag + name
                        if (title.country != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(text = countryCodeToFlag(title.country), fontSize = 32.sp)
                                Text(
                                    text = countryCodeToName(title.country),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                        // Genres
                        if (title.genres.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                title.genres.forEach { genre ->
                                    AssistChip(onClick = {}, label = {
                                        Text(genre, style = MaterialTheme.typography.bodyMedium)
                                    })
                                }
                            }
                        }
                        // Crew
                        title.director?.let {
                            Text(
                                text = "Director: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        if (title.leadActors.isNotEmpty()) {
                            Text(
                                text = "Starring: ${title.leadActors.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        title.productionCompany?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        // Description
                        title.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val bg = if (isActive) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                       else if (!enabled) Color.White.copy(alpha = 0.35f)
                       else Color.White

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled || isActive, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun MediaTypePill(mediaType: MediaType) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = if (mediaType == MediaType.TV) "TV" else "Film",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
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

private fun countryCodeToFlag(code: String): String {
    if (code.length != 2) return ""
    val base = 0x1F1E6 - 'A'.code
    return String(Character.toChars(base + code[0].uppercaseChar().code)) +
           String(Character.toChars(base + code[1].uppercaseChar().code))
}

private fun countryCodeToName(code: String): String =
    java.util.Locale("", code).displayCountry.takeIf { it.isNotBlank() } ?: code
