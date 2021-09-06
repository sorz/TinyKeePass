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


private object Routes {
    const val LOCKED = "locked"
    const val LIST = "list"
    const val CONFIG = "config"
}

@Composable
fun App() {
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
            NavGraph(navController, scaffoldState)
        }
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    scaffoldState: ScaffoldState,
) {

    NavHost(navController, Routes.LOCKED) {
        // Locked screen
        composable(Routes.LOCKED) {
            TODO()
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