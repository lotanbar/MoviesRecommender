package com.moviesrecommender.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moviesrecommender.navigation.Screen

@Composable
fun ActionsScreen(navController: NavHostController) {
    // TODO (Commit 11/12): wire Recommend and Rate to AI flows
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf(
            "Search"    to Screen.Search.route,
            "Recommend" to null,
            "Rate"      to null,
            "Wishlist"  to Screen.Wishlist.route
        ).forEach { (label, route) ->
            Button(
                onClick = { route?.let { navController.navigate(it) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = label, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
