package org.gallery.swiper.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.gallery.swiper.ui.home.HomeScreen
import org.gallery.swiper.ui.swipe.SwipeScreen
import org.gallery.swiper.ui.review.ReviewScreen
import org.gallery.swiper.ui.bookmarks.BookmarksScreen
import org.gallery.swiper.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val SWIPE = "swipe/{monthKey}"
    const val REVIEW = "review/{monthKey}"
    const val BOOKMARKS = "bookmarks"
    const val STATS = "stats"

    fun swipe(monthKey: String) = "swipe/$monthKey"
    fun review(monthKey: String) = "review/$monthKey"
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onMonthClick = { monthKey ->
                    navController.navigate(Routes.swipe(monthKey))
                },
                onBookmarksClick = {
                    navController.navigate(Routes.BOOKMARKS)
                },
                onStatsClick = {
                    navController.navigate(Routes.STATS)
                },
            )
        }

        composable(
            route = Routes.SWIPE,
            arguments = listOf(navArgument("monthKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val monthKey = backStackEntry.arguments?.getString("monthKey") ?: return@composable
            SwipeScreen(
                monthKey = monthKey,
                onFinish = { reviewedKey ->
                    navController.navigate(Routes.review(reviewedKey)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("monthKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val monthKey = backStackEntry.arguments?.getString("monthKey") ?: return@composable
            ReviewScreen(
                monthKey = monthKey,
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.BOOKMARKS) {
            BookmarksScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
