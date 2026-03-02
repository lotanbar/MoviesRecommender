package com.moviesrecommender.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.dropbox.DropboxEntries
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.ui.theme.Primary
import com.moviesrecommender.ui.theme.StarYellow

@Composable
fun FolderPickerDialog(
    onFileSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dropboxService = MoviesRecommenderApp.instance.dropboxService

    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(DropboxEntries(emptyList(), emptyList())) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        isLoading = true
        errorMessage = null
        when (val result = dropboxService.listEntries(currentPath)) {
            is DropboxResult.Success -> entries = result.value
            is DropboxResult.Failure -> {
                errorMessage = "Error: ${result.error}"
                entries = DropboxEntries(emptyList(), emptyList())
            }
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentPath.isNotEmpty()) {
                        IconButton(
                            onClick = { currentPath = parentPath(currentPath) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (currentPath.isEmpty()) "Dropbox" else currentPath,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .fillMaxWidth()
                ) {
                    when {
                        isLoading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                        errorMessage != null -> Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                        entries.folders.isEmpty() && entries.files.isEmpty() -> Text(
                            text = "Empty folder",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                        else -> LazyColumn {
                            items(entries.folders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentPath = folder.pathDisplay }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        folder.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            items(entries.files) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onFileSelected(file.pathDisplay) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = StarYellow,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = StarYellow
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

private fun parentPath(path: String): String {
    val i = path.lastIndexOf('/')
    return if (i <= 0) "" else path.substring(0, i)
}
