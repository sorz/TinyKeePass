package org.sorz.lab.tinykeepass

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.sorz.lab.tinykeepass.keepass.Repository
import org.sorz.lab.tinykeepass.ui.App
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var repo: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(repo, intent.data)
        }
    }
}

fun Context.getActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}
