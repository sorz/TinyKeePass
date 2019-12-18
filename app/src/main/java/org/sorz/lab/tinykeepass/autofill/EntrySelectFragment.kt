package org.sorz.lab.tinykeepass.autofill

import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import de.slackspace.openkeepass.domain.Entry
import org.sorz.lab.tinykeepass.BaseEntryFragment
import org.sorz.lab.tinykeepass.R

@RequiresApi(api = Build.VERSION_CODES.O)
class EntrySelectFragment : BaseEntryFragment() {
    override val fragmentLayout = R.layout.fragment_entry_select

    override fun onEntryClick(view: View, entry: Entry) {
        (requireActivity() as EntrySelectActivity).onEntrySelected(entry)
    }

    override fun onEntryLongClick(view: View, entry: Entry): Boolean {
        return false
    }

    companion object {
        @JvmStatic
        fun newInstance(): EntrySelectFragment {
            return EntrySelectFragment()
        }
    }
}