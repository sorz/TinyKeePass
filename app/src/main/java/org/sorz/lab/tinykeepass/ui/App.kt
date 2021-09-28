package org.sorz.lab.tinykeepass.ui

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.composethemeadapter.MdcTheme
import kotlinx.coroutines.Dispatchers
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.keepass.RealRepository
import org.sorz.lab.tinykeepass.keepass.Repository


private object Routes {
    const val LOCKED = "locked"
    const val LIST = "list"
    const val Setup = "setup"
}

@Composable
fun App() {
    val repo = RealRepository(
        LocalContext.current,
        Dispatchers.IO,
    )

    MdcTheme {
        val navController = rememberNavController()
        val scaffoldState = rememberScaffoldState()

        Scaffold(
            scaffoldState = scaffoldState,
            floatingActionButton = { SyncDatabaseFloatingActionButton(
                repo = repo,
                snackbarHostState = scaffoldState.snackbarHostState
            ) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.app_name))
                    },
                )
             },
        ) {
            NavGraph(navController, scaffoldState, repo)
        }
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    scaffoldState: ScaffoldState,
    repo: Repository,
) {
    NavHost(navController, Routes.LOCKED) {
        // Locked screen
        composable(Routes.LOCKED) {
            LockScreen(repo, navController, scaffoldState.snackbarHostState)
        }
        // List screen
        composable(Routes.LIST) {
            ListScreen(repo, navController, scaffoldState.snackbarHostState)
        }
        // Setup screen
        composable(Routes.Setup) {
            SetupScreen(repo, navController, scaffoldState.snackbarHostState)
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
}