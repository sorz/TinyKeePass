package org.sorz.lab.tinykeepass.keepass

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import org.sorz.lab.tinykeepass.R

/**
 * Open KeePassFile with a loading dialog.
 */
class OpenKeePassDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        isCancelable = false
        dialog?.setTitle(R.string.open_db_dialog_title)
        dialog?.setCancelable(false)
        dialog?.setCanceledOnTouchOutside(false)
        return inflater.inflate(R.layout.fragment_open_database_dialog,
                container, false)
    }

    fun onOpenError(message: String) {
        if (view == null) {
            dismiss()
            return
        }
        val note = view!!.findViewById<TextView>(R.id.textView)
        val progressBar = view!!.findViewById<ProgressBar>(R.id.progressBar)
        dialog?.setTitle(R.string.open_db_dialog_fail)
        note.text = message
        progressBar.visibility = View.INVISIBLE
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(true)
    }

    fun onOpenOk() {
        dismiss()
    }

    companion object {

        fun newInstance(): OpenKeePassDialogFragment {
            return OpenKeePassDialogFragment()
        }
    }
}
