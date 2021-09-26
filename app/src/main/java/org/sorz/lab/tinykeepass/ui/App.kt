package org.sorz.lab.tinykeepass.ui

import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        val floatingActionButton = remember { mutableStateOf<@Composable () -> Unit>({}) }

        Scaffold(
            scaffoldState = scaffoldState,
            floatingActionButton = floatingActionButton.value,
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.app_name))
                    },
                )
             },
        ) {
            NavGraph(navController, scaffoldState, repo, floatingActionButton)
        }
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    scaffoldState: ScaffoldState,
    repo: Repository,
    floatingActionButton: MutableState<@Composable () -> Unit>,
) {

    NavHost(navController, Routes.LOCKED) {
        // Locked screen
        composable(Routes.LOCKED) {
            floatingActionButton.value = { }
            LockScreen(repo, navController, scaffoldState)
        }
        // List screen
        composable(Routes.LIST) {
            ListScreen(repo, navController, scaffoldState, floatingActionButton)
        }
        // Setup screen
        composable(Routes.Setup) {
            floatingActionButton.value = { }
            SetupScreen(repo, navController, scaffoldState)
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