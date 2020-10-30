package org.sorz.lab.tinykeepass.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import org.sorz.lab.tinykeepass.BaseActivity
import org.sorz.lab.tinykeepass.GetKeyError
import org.sorz.lab.tinykeepass.keepass.KeePassStorage

@RequiresApi(api = Build.VERSION_CODES.O)
internal abstract class BaseActivity : BaseActivity() {
    private var replyIntent: Intent? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (KeePassStorage.get(this) != null) {
            onDatabaseOpened()
        } else {
            lifecycleScope.launchWhenResumed {
                try {
                    val keys = getDatabaseKeys()
                    openDatabase(keys[0]) { onDatabaseOpened() }
                } catch (err: GetKeyError) {
                    Toast.makeText(this@BaseActivity, err.message!!, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun finish() {
        if (replyIntent != null) {
            setResult(RESULT_OK, replyIntent)
        } else {
            setResult(RESULT_CANCELED)
        }
        super.finish()
    }

    protected abstract fun onDatabaseOpened()
    protected fun parseStructure(): StructureParser.Result {
        val structure = intent.getParcelableExtra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        return StructureParser(structure).parse()
    }

    protected fun setFillResponse(response: FillResponse?) {
        replyIntent = Intent()
        replyIntent!!.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
    }
}