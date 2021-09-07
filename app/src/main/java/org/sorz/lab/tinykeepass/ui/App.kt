package org.sorz.lab.tinykeepass.ui

import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.composethemeadapter.MdcTheme
import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.keepass.RealRepository
import org.sorz.lab.tinykeepass.keepass.Repository


private object Routes {
    const val LOCKED = "locked"
    const val LIST = "list"
    const val CONFIG = "config"
}

@Composable
fun App() {
    val repo = RealRepository()

    MdcTheme {
        val navController = rememberNavController()
        val scaffoldState = rememberScaffoldState()

        Scaffold(
            scaffoldState = scaffoldState,
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
            LockScreen(repo, navController)
        }
        // List screen
        composable(Routes.LIST) {
            TODO()
        }
        // Config screen
        composable(Routes.CONFIG) {
            TODO()
        }
    }
}

class NavActions(navController: NavController) {
    val locked: () -> Unit = {
        navController.navigate(Routes.LOCKED)
    }
    val list: () -> Unit = {
        navController.navigate(Routes.LIST)
    }
    val config: () -> Unit = {
        navController.navigate(Routes.CONFIG)
    }
}