package org.sorz.lab.tinykeepass.autofill

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import androidx.annotation.RequiresApi

import org.sorz.lab.tinykeepass.R
import org.sorz.lab.tinykeepass.keepass.hasDatabaseConfigured
import kotlin.streams.toList


private const val TAG = "TinyAutofillService"

@RequiresApi(api = Build.VERSION_CODES.O)
class TinyAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest,
                               cancellationSignal: CancellationSignal,
                               callback: FillCallback) {
        cancellationSignal.setOnCancelListener { Log.d(TAG, "autofill canceled.") }
        if (!hasDatabaseConfigured) {
            callback.onSuccess(null)
            return
        }

        val structure = request.fillContexts[request.fillContexts.size - 1].structure
        val parseResult = StructureParser(structure).parse()
        if (parseResult.password.isEmpty()) {
            Log.d(TAG, "no password field found")
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()

        val presentation = AutofillUtils.getRemoteViews(this,
                getString(R.string.autofill_unlock_db),
                android.R.drawable.ic_lock_lock)
        val sender = AuthActivity.getAuthIntentSenderForResponse(this)
        val autofillIds = parseResult.allAutofillIds().toList().toTypedArray()
        responseBuilder.setAuthentication(autofillIds, sender, presentation)

        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onFailure(getString(R.string.autofill_not_support_save))
    }
    override fun onConnected() { Log.d(TAG, "onConnected") }
    override fun onDisconnected() { Log.d(TAG, "onDisconnected") }
}
