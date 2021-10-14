package org.sorz.lab.tinykeepass.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.composethemeadapter.MdcTheme
import org.sorz.lab.tinykeepass.keepass.KdbxNative
import org.sorz.lab.tinykeepass.keepass.Repository


private object Routes {
    const val LOCKED = "locked"
    const val LIST = "list"
    const val Setup = "setup"
}

@Composable
fun App(repo: Repository, openDatabaseUri: Uri? = null) {
    MdcTheme {
        val navController = rememberNavController()
        NavGraph(navController, repo, openDatabaseUri)
    }

    LaunchedEffect(Unit) {
        val bytes = KdbxNative.loadDatabase("/test/database.kdbx", "password")
        println("KdbxNative return ${String(bytes)}")
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    repo: Repository,
    openDatabaseUri: Uri? = null
) {
    val start = if (openDatabaseUri != null) Routes.Setup else Routes.LOCKED
    var ignoreOpenDatabaseUri by rememberSaveable { mutableStateOf(false) }

    NavHost(navController, start) {
        // Locked screen
        composable(Routes.LOCKED) {
            LockScreen(repo, navController)
            ignoreOpenDatabaseUri = false
        }
        // List screen
        composable(Routes.LIST) {
            ListScreen(repo, navController)
            ignoreOpenDatabaseUri = false
        }
        // Setup screen
        composable(Routes.Setup) {
            SetupScreen(repo, navController, openDatabaseUri.takeUnless { ignoreOpenDatabaseUri })
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
    val setup: () -> Unit = {
        navController.navigate(Routes.Setup)
    }
    val popBack: () -> Unit = {
        navController.popBackStack()
    }
}
