package org.sorz.lab.tinykeepass.autofill

import android.content.Intent
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.sorz.lab.tinykeepass.keepass.*
import org.sorz.lab.tinykeepass.ui.AutofillAction
import org.sorz.lab.tinykeepass.ui.AutofillScreen
import java.lang.IllegalArgumentException
import javax.inject.Inject


@AndroidEntryPoint
class AutofillActivity : AppCompatActivity() {
    @Inject lateinit var repo: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = AutofillAction.valueOf(
            intent.action ?: throw IllegalArgumentException("Missing intent action"))
        val structure = intent.getParcelableExtra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)
            ?: throw IllegalArgumentException("Missing intent extra EXTRA_ASSIST_STRUCTURE")

        setContent {
            AutofillScreen(repo, action, StructureParser(structure).parse()) { resultCode, intent ->
                if (intent != null) setResult(resultCode, intent)
                else setResult(resultCode)
                finish()
            }
        }
    }
}


fun Context.getAutofillIntentSender(autofillAction: AutofillAction) = PendingIntent.getActivity(
    this,
    0,
    Intent(this, AutofillActivity::class.java).apply {
        action = autofillAction.toString()
    },
    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
).intentSender
