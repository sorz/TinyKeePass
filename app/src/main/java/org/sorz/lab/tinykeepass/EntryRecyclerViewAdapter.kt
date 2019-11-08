package org.sorz.lab.tinykeepass

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import org.sorz.lab.tinykeepass.keepass.KeePassStorage
import org.sorz.lab.tinykeepass.search.EntryQueryRelevance

import java.util.function.BiConsumer
import java.util.function.BiPredicate

import de.slackspace.openkeepass.domain.Entry
import org.jetbrains.anko.AnkoLogger
import org.sorz.lab.tinykeepass.keepass.allEntriesNotInRecycleBin
import org.sorz.lab.tinykeepass.keepass.icon
import java.util.*
import kotlin.streams.toList


private const val PASSWORD_NUM_OF_CHARS_IN_GROUP = 4

class EntryRecyclerViewAdapter (
    private val context: Context,
    private val onClickHandler: BiConsumer<View, Entry>?,
    private val onLongClickHandler: BiPredicate<View, Entry>?
) : RecyclerView.Adapter<EntryRecyclerViewAdapter.ViewHolder>(), AnkoLogger {
    private val allEntries: MutableList<Entry> = loadEntries().toMutableList()
    private var entries: List<Entry> = allEntries
    private var filter: String? = null
    private var selectedItem = -1
        set(position) {
            val old = field
            field = position
            notifyItemChanged(old)
            notifyItemChanged(position)
        }
    private var passwordShownItem = -1

    fun reloadEntries() {
        allEntries.clear()
        allEntries.addAll(loadEntries())
        setFilter(filter)
    }

    private fun loadEntries(): Sequence<Entry> {
        return KeePassStorage.get(context)?.let { db ->
            db.allEntriesNotInRecycleBin
                .sortedBy { it.times.creationTime }
                .sortedBy { it.url }
                .sortedBy { it.username }
                .sortedBy { it.title }
        } ?: emptySequence()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_entry, parent, false)
        return ViewHolder(view)
    }

    private fun showTextViewOnlyIfNotEmpty(view: TextView) {
        view.visibility = if (view.length() == 0) View.GONE else View.VISIBLE
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.view.isSelected = selectedItem == position
        holder.imageIcon.setImageBitmap(entry.icon)
        holder.textTitle.text = entry.title ?: ""
        holder.textUsername.text = entry.username ?: ""

        val url = (entry.url ?: "").replace("^https?://(www\\.)?".toRegex(), "")
        val hostnamePath = url.split("/".toRegex(), 2)
        holder.textUrlHostname.text = hostnamePath[0]
        holder.textUrlPath.text =
                hostnamePath.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: ""
        holder.textPassword.text = ""

        if (position == passwordShownItem
                && entry.password != null && entry.password.isNotEmpty()) {
            val builder = SpannableStringBuilder()
            val textColors = intArrayOf(context.getColor(R.color.password1), context.getColor(R.color.password2))
            val backgroundColors = intArrayOf(context.getColor(R.color.passwordBackground1), context.getColor(R.color.passwordBackground2))
            var colorIndex = 0
            for (c in entry.password.toCharArray()) {
                builder.append(c)
                if (builder.length >= PASSWORD_NUM_OF_CHARS_IN_GROUP || holder.textPassword.length() + builder.length >= entry.password.length) {
                    builder.setSpan(BackgroundColorSpan(backgroundColors[colorIndex]),
                            0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(textColors[colorIndex]),
                            0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    holder.textPassword.append(builder)
                    builder.clear()
                    colorIndex = (colorIndex + 1) % textColors.size
                }
            }
        }
        showTextViewOnlyIfNotEmpty(holder.textUsername)
        showTextViewOnlyIfNotEmpty(holder.textUrlHostname)
        showTextViewOnlyIfNotEmpty(holder.textUrlPath)
        showTextViewOnlyIfNotEmpty(holder.textPassword)

        if (onClickHandler != null)
            holder.view.setOnClickListener { v -> onClickHandler.accept(v, entry) }
        if (onLongClickHandler != null)
            holder.view.setOnLongClickListener { v ->
                selectedItem = position
                onLongClickHandler.test(v, entry)
            }
    }

    val selectedEntry: Entry? get() = entries.getOrNull(selectedItem)

    fun clearSelection() {
        val item = selectedItem
        selectedItem = -1
        if (item >= 0)
            notifyItemChanged(item)
    }

    override fun getItemCount(): Int = entries.size

    inner class ViewHolder internal constructor(internal val view: View) : RecyclerView.ViewHolder(view) {
        internal val imageIcon: ImageView = view.findViewById(R.id.imageIcon)
        internal val textTitle: TextView = view.findViewById(R.id.textTitle)
        internal val textUsername: TextView = view.findViewById(R.id.textUsername)
        internal val textUrlHostname: TextView = view.findViewById(R.id.textUrlHostname)
        internal val textUrlPath: TextView = view.findViewById(R.id.textUrlPath)
        internal val textPassword: TextView = view.findViewById(R.id.textPassword)

        override fun toString(): String {
            return super.toString() + " '" + textTitle.text + "'"
        }
    }

    fun setFilter(query: String?) {
        selectedItem = -1
        passwordShownItem = -1
        entries = if (query.isNullOrBlank()) {
            allEntries
        } else {
            val keywords = query.toLowerCase(Locale.getDefault()).trim().split(' ')
            allEntries.parallelStream()
                    .map { e -> EntryQueryRelevance(e, keywords) }
                    .filter { it.isRelated }
                    .sorted()
                    .map { it.entry }
                    .toList()
        }
        filter = query
        notifyDataSetChanged()
    }

    fun showPassword(entry: Entry) {
        hidePassword()
        passwordShownItem = entries.indexOf(entry)
        notifyItemChanged(passwordShownItem)
    }

    fun hidePassword() {
        val item = passwordShownItem
        passwordShownItem = -1
        if (item < 0 || item >= entries.size)
            return
        notifyItemChanged(item)
    }

}
