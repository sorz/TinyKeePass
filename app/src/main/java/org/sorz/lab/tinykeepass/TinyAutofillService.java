package org.sorz.lab.tinykeepass;

import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;


@RequiresApi(api = Build.VERSION_CODES.O)
public class TinyAutofillService extends AutofillService {
    @Override
    public void onFillRequest(@NonNull FillRequest request,
                              @NonNull CancellationSignal cancellationSignal,
                              @NonNull FillCallback callback) {


    }

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {

    }
}
