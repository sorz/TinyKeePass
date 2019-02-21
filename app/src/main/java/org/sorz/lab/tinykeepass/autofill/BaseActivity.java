package org.sorz.lab.tinykeepass.autofill;

import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.autofill.FillResponse;
import androidx.annotation.RequiresApi;

import android.view.autofill.AutofillManager;
import android.widget.Toast;

import org.sorz.lab.tinykeepass.keepass.KeePassStorage;


@RequiresApi(api = Build.VERSION_CODES.O)
abstract class BaseActivity extends org.sorz.lab.tinykeepass.BaseActivity {
    private final static String TAG = BaseActivity.class.getName();

    private Intent replyIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (KeePassStorage.get(this) != null) {
            onDatabaseOpened();
        } else {
            getDatabaseKeys(keys ->
                    openDatabase(keys.get(0), db -> onDatabaseOpened())
            , this::onError);
        }
    }

    private void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void finish() {
        if (replyIntent != null) {
            setResult(RESULT_OK, replyIntent);
        } else {
            setResult(RESULT_CANCELED);
        }
        super.finish();
    }

    abstract void onDatabaseOpened();

    protected StructureParser.Result parseStructure() {
        AssistStructure structure =
                getIntent().getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE);
        return new StructureParser(structure).parse();
    }


    protected void setFillResponse(FillResponse response) {
        replyIntent = new Intent();
        replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response);
    }
}
