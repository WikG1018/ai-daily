package com.wikg.aidaily.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wikg.aidaily.AiDailyApp
import com.wikg.aidaily.DeepLink
import com.wikg.aidaily.ui.detail.DetailScreen
import com.wikg.aidaily.ui.home.HomeScreen
import com.wikg.aidaily.ui.home.HomeViewModel
import com.wikg.aidaily.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppNav(deepLinks: StateFlow<DeepLink?>, consumeDeepLink: () -> Unit) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as AiDailyApp
    val homeVm: HomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(app.container) as T
    })

    val link by deepLinks.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        when (val l = link) {
            is DeepLink.Issue -> {
                nav.popBackStack("home", inclusive = false)
                homeVm.openIssue(l.date)
                consumeDeepLink()
            }
            is DeepLink.Item -> {
                nav.navigate("item/${l.id}") { launchSingleTop = true }
                consumeDeepLink()
            }
            null -> Unit
        }
    }

    val dur = 320
    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(dur)) + fadeIn(tween(dur)) },
        exitTransition = { fadeOut(tween(dur / 2)) },
        popEnterTransition = { fadeIn(tween(dur)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(dur)) + fadeOut(tween(dur)) },
    ) {
        composable("home") {
            HomeScreen(
                vm = homeVm,
                onOpenItem = { nav.navigate("item/$it") },
                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
            )
        }
        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(
                itemId = id,
                onBack = { nav.popBackStack() },
                onOpenItem = { nav.navigate("item/$it") },
                onOpenIssue = { date ->
                    nav.popBackStack("home", inclusive = false)
                    homeVm.openIssue(date)
                },
            )
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
