package com.moviesrecommender.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.navigation.Screen

@Composable
fun SplashScreen(navController: NavHostController) {
    LaunchedEffect(Unit) {
        val app = MoviesRecommenderApp.instance
        val allConfigured = app.dropboxService.isAuthenticated() &&
            app.dropboxService.authManager.getListPath() != null &&
            app.tmdbService.isConfigured() &&
            app.anthropicService.isConfigured()

        val target = if (allConfigured) Screen.Actions.route
                     else Screen.Setup.createRoute(showContinueAnyway = true)
        navController.navigate(target) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Movies Recommender",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
