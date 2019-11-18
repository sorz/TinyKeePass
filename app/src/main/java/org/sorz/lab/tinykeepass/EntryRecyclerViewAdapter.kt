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
import android.widget.TextView
import androidx.databinding.BindingAdapter

import org.sorz.lab.tinykeepass.search.EntryQueryRelevance

import java.util.function.BiConsumer
import java.util.function.BiPredicate

import de.slackspace.openkeepass.domain.Entry
import org.jetbrains.anko.AnkoLogger
import org.sorz.lab.tinykeepass.databinding.FragmentEntryBinding
import org.sorz.lab.tinykeepass.keepass.*
import java.util.*
import kotlin.properties.Delegates
import kotlin.streams.toList


private const val PASSWORD_NUM_OF_CHARS_IN_GROUP = 4

class EntryRecyclerViewAdapter (
    private val context: Context,
    private val onClickHandler: BiConsumer<View, Entry>,
    private val onLongClickHandler: BiPredicate<View, Entry>
) : RecyclerView.Adapter<EntryViewHolder>(), AnkoLogger {
    private val allEntries: MutableList<Entry> = loadEntries().toMutableList()
    private var entries: List<Entry> = allEntries
    private var filter: String? = null
    private var selectedPosition by entryPositionObservable()
    private var passwordShownPosition by entryPositionObservable()

    private fun entryPositionObservable() = Delegates.observable(-1) {
        _, old, new ->
        if (old == new) return@observable
        if (old >= 0 && old < entries.size) notifyItemChanged(old)
        if (new >= 0 && new < entries.size) notifyItemChanged(new)
    }

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EntryViewHolder(
        FragmentEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val entry = entries[position]
        holder.binding.entry = entry
        holder.binding.password = entry.password.takeIf { position == passwordShownPosition }
        holder.binding.root.apply {
            isSelected = selectedPosition == position || passwordShownPosition == position
            setOnClickListener { onClickHandler.accept(it, entry) }
            setOnLongClickListener { view ->
                selectedPosition = position
                onLongClickHandler.test(view, entry)
            }
        }
    }

    val selectedEntry: Entry? get() = entries.getOrNull(selectedPosition)

    fun clearSelection() {
        selectedPosition = -1
    }

    override fun getItemCount(): Int = entries.size

    fun setFilter(query: String?) {
        selectedPosition = -1
        passwordShownPosition = -1
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
        passwordShownPosition = entries.indexOf(entry)
    }

    fun hidePassword() {
        passwordShownPosition = -1
    }
}

class EntryViewHolder(val binding: FragmentEntryBinding) : RecyclerView.ViewHolder(binding.root)

@BindingAdapter("app:nullableText")
fun setNullableText(view: TextView, text: String?) {
    view.text = text ?: ""
    view.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
}

@BindingAdapter("app:coloredPassword")
fun setColoredPassword(view: TextView, password: String?) {
    view.visibility = if (password.isNullOrEmpty()) View.GONE else View.VISIBLE
    view.text = ""
    if (password == null) return

    val context = view.context
    val builder = SpannableStringBuilder()
    val textColors = intArrayOf(
            context.getColor(R.color.password1),
            context.getColor(R.color.password2)
    )
    val backgroundColors = intArrayOf(
            context.getColor(R.color.passwordBackground1),
            context.getColor(R.color.passwordBackground2)
    )
    var colorIndex = 0
    for (c in password.toCharArray()) {
        builder.append(c)
        if (builder.length >= PASSWORD_NUM_OF_CHARS_IN_GROUP || view.length() + builder.length >= password.length) {
            builder.setSpan(BackgroundColorSpan(backgroundColors[colorIndex]),
                    0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(textColors[colorIndex]),
                    0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            view.append(builder)
            builder.clear()
            colorIndex = (colorIndex + 1) % textColors.size
        }
    }
}