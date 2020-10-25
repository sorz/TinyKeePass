package org.sorz.lab.tinykeepass

import android.os.Bundle
import android.view.*
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kunzisoft.keepass.database.element.Entry

abstract class BaseEntryFragment : Fragment(), SearchView.OnQueryTextListener {
    protected lateinit var entryAdapter: EntryRecyclerViewAdapter
        private set
    @get:LayoutRes
    protected abstract val fragmentLayout: Int

    protected abstract fun onEntryClick(view: View, entry: Entry)
    protected abstract fun onEntryLongClick(view: View, entry: Entry): Boolean

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(fragmentLayout, container, false)
        // Set the adapter
        val recyclerView: RecyclerView = view.findViewById(R.id.list)
        val spanCount = resources.configuration.screenWidthDp / ENTRY_MAX_WIDTH_DP
        val layoutManager: RecyclerView.LayoutManager =
                if (spanCount <= 1) LinearLayoutManager(context)
                else GridLayoutManager(context, spanCount)
        recyclerView.layoutManager = layoutManager
        entryAdapter = EntryRecyclerViewAdapter(
                requireContext(),
                { v, entry -> onEntryClick(v, entry) },
                { v, entry -> onEntryLongClick(v, entry) }
        )
        recyclerView.adapter = entryAdapter
        recyclerView.setHasFixedSize(false)
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_search, menu)
        val searchMenu = menu.findItem(R.id.action_search)
        val searchView = searchMenu.actionView as SearchView
        searchView.setOnQueryTextListener(this)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return onQueryTextChange(query)
    }

    override fun onQueryTextChange(newText: String): Boolean {
        entryAdapter.setFilter(newText)
        return true
    }

    companion object {
        private const val ENTRY_MAX_WIDTH_DP = 350
    }
}