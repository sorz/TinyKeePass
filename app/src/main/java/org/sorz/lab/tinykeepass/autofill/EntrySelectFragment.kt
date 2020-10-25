package org.sorz.lab.tinykeepass.autofill

import android.content.Context
import android.os.Build
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.annotation.RequiresApi
import com.kunzisoft.keepass.database.element.Entry
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.action_search).expandActionView()
    }

    companion object {
        @JvmStatic
        fun newInstance(): EntrySelectFragment {
            return EntrySelectFragment()
        }
    }
}