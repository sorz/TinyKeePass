package org.sorz.lab.tinykeepass.autofill;

import android.app.assist.AssistStructure;
import android.content.IntentSender;
import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.RemoteViews;

import org.sorz.lab.tinykeepass.R;


@RequiresApi(api = Build.VERSION_CODES.O)
public class TinyAutofillService extends AutofillService {
    static private final String TAG = TinyAutofillService.class.getName();
    @Override
    public void onFillRequest(@NonNull FillRequest request,
                              @NonNull CancellationSignal cancellationSignal,
                              @NonNull FillCallback callback) {

        cancellationSignal.setOnCancelListener(() -> Log.d(TAG, "autofill canceled."));

        AssistStructure structure = request.getFillContexts()
                .get(request.getFillContexts().size() - 1).getStructure();
        StructureParser.Result parseResult = new StructureParser(structure).parse();
        if (parseResult.password.isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        FillResponse.Builder responseBuilder = new FillResponse.Builder();

        RemoteViews presentation = AutofillUtils.getRemoteViews(this,
                getString(R.string.autofill_unlock_db),
                android.R.drawable.ic_lock_lock);
        IntentSender sender = AuthActivity.getAuthIntentSenderForResponse(this);
        responseBuilder.setAuthentication(parseResult.getAutofillIds(), sender, presentation);

        callback.onSuccess(responseBuilder.build());
    }



    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
        callback.onFailure(getString(R.string.autofill_not_support_save));
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
    }
}
