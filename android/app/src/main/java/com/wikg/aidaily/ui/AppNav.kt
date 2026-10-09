package com.wikg.aidaily.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
import com.wikg.aidaily.ui.settings.FeaturedScreen
import com.wikg.aidaily.ui.settings.FollowKind
import com.wikg.aidaily.ui.settings.FollowScreen
import com.wikg.aidaily.ui.settings.SettingsPage
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

    // 转场：新页从右侧滑入（不透明），旧页轻微视差左移；底下垫一层主题背景色，
    // 任何时刻都不会露出窗口底色（深色模式下进设置不再闪白）。
    val dur = 300
    val ease = FastOutSlowInEasing
    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(dur, easing = ease)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(dur, easing = ease)) { it / 4 } },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(dur, easing = ease)) { it / 4 } },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(dur, easing = ease)) },
    ) {
        composable("home") {
            HomeScreen(
                vm = homeVm,
                onOpenItem = { nav.navigate("item/$it") },
                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onOpenFeatured = { nav.navigate("featured") { launchSingleTop = true } },
                onOpenNotifySettings = { nav.navigate(SettingsPage.NOTIFY.route) { launchSingleTop = true } },
                onOpenFollows = { sid -> nav.navigate(FollowKind.forSection(sid).route) { launchSingleTop = true } },
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
        SettingsPage.entries.forEach { page ->
            composable(page.route) {
                SettingsScreen(
                    page = page,
                    onBack = { nav.popBackStack() },
                    onOpenFeatured = { nav.navigate("featured") { launchSingleTop = true } },
                    onNavigate = { nav.navigate(it.route) { launchSingleTop = true } },
                    onOpenFollow = { k -> nav.navigate(k.route) { launchSingleTop = true } },
                )
            }
        }
        composable("featured") { FeaturedScreen(onBack = { nav.popBackStack() }) }
        FollowKind.entries.forEach { kind ->
            composable(kind.route) { FollowScreen(kind = kind, onBack = { nav.popBackStack() }) }
        }
    }
}
