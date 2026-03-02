package com.moviesrecommender.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.moviesrecommender.ui.screens.ActionsScreen
import com.moviesrecommender.ui.screens.PreviewScreen
import com.moviesrecommender.ui.screens.SearchScreen
import com.moviesrecommender.ui.screens.SetupScreen
import com.moviesrecommender.ui.screens.SplashScreen
import com.moviesrecommender.ui.screens.WishlistScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController)
        }
        composable(Screen.Setup.route) {
            SetupScreen(navController)
        }
        composable(Screen.Actions.route) {
            ActionsScreen(navController)
        }
        composable(Screen.Search.route) {
            SearchScreen(navController)
        }
        composable(Screen.Wishlist.route) {
            WishlistScreen(navController)
        }
        composable(Screen.Preview.route) {
            PreviewScreen(navController)
        }
    }
}
