package com.moviesrecommender.navigation

sealed class Screen(val route: String) {
    object Splash    : Screen("splash")
    object Setup     : Screen("setup/{showContinueAnyway}") {
        fun createRoute(showContinueAnyway: Boolean) = "setup/$showContinueAnyway"
    }
    object Actions   : Screen("actions")
    object Search    : Screen("search")
    object Recommend : Screen("recommend")
    object Wishlist  : Screen("wishlist")
    object Preview   : Screen("preview/{tmdbId}/{mediaType}") {
        fun createRoute(id: Int, mediaType: String) = "preview/$id/$mediaType"
    }
}
