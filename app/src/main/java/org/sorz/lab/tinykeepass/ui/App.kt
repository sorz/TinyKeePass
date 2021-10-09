package org.sorz.lab.tinykeepass.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.material.composethemeadapter.MdcTheme
import kotlinx.coroutines.Dispatchers
import org.sorz.lab.tinykeepass.keepass.RealRepository
import org.sorz.lab.tinykeepass.keepass.Repository


private object Routes {
    const val LOCKED = "locked"
    const val LIST = "list"
    const val SEARCH = "search?${Params.KEYWORD}={${Params.KEYWORD}}"
    const val SETUP = "setup"
}

private object Params {
    const val KEYWORD = "keyword"
}

@Composable
fun App() {
    val repo = RealRepository(
        LocalContext.current,
        Dispatchers.IO,
    )

    MdcTheme {
        val navController = rememberNavController()
        NavGraph(navController, repo)
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    repo: Repository,
) {
    NavHost(navController, Routes.LOCKED) {
        // Locked screen
        composable(Routes.LOCKED) {
            LockScreen(repo, navController)
        }
        // List screen
        composable(Routes.LIST) {
            ListScreen(repo, navController)
        }
        // Search screen
        composable(
            Routes.SEARCH,
            arguments = listOf(navArgument(Params.KEYWORD) { defaultValue = "" })
        ) { backStackEntry ->
            val keyword = backStackEntry.arguments?.getString(Params.KEYWORD) ?: ""
            ListScreen(repo, navController, keyword)
        }
        // Setup screen
        composable(Routes.SETUP) {
            SetupScreen(repo, navController)
        }
    }
}

class NavActions(navController: NavController) {
    val locked: () -> Unit = {
        navController.navigate(Routes.LOCKED)
    }
    val list: () -> Unit = {
        navController.navigate(Routes.LIST) {
            launchSingleTop = true
            popUpTo(Routes.LOCKED)
        }
    }
    val search: (keyword: String) -> Unit = { keyword ->
        navController.navigate("search?${Params.KEYWORD}=$keyword") {
            launchSingleTop = true
            popUpTo(Routes.LIST)
        }
    }
    val setup: () -> Unit = {
        navController.navigate(Routes.SETUP)
    }
}