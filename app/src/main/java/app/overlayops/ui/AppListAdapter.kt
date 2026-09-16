package app.overlayops.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.overlayops.databinding.ItemAppBinding
import app.overlayops.databinding.ItemHeaderBinding
import app.overlayops.model.AppEntry

class AppListAdapter(
    private val onOpen: (AppEntry) -> Unit,
    private val onChangeOverlay: (AppEntry) -> Unit,
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemHeaderBinding.inflate(inflater, parent, false))
        } else {
            AppVH(ItemAppBinding.inflate(inflater, parent, false), onOpen, onChangeOverlay)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderVH).bind(item)
            is ListItem.App -> (holder as AppVH).bind(item.entry)
        }
    }

    class HeaderVH(private val b: ItemHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ListItem.Header) = with(b) {
            headerTitle.text = item.title
            headerCount.text = item.count.toString()
        }
    }

    class AppVH(
        private val b: ItemAppBinding,
        private val onOpen: (AppEntry) -> Unit,
        private val onChangeOverlay: (AppEntry) -> Unit,
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: AppEntry) = with(b) {
            label.text = item.label
            pkg.text = item.packageName
            icon.setImageDrawable(item.icon)
            badgeUid.text = "uid ${item.uid}"
            badgeSystem.isVisible = item.isSystem && false
            badgeDeclares.isVisible = item.declaresOverlay
            statusChip.bindStatusChip(item.overlayStatus)

            statusChip.setOnClickListener { onChangeOverlay(item) }
            rowRoot.setOnClickListener { onOpen(item) }
            rowRoot.setOnLongClickListener {
                onChangeOverlay(item)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1

        private val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(old: ListItem, new: ListItem) = old.key == new.key
            override fun areContentsTheSame(old: ListItem, new: ListItem) = old == new
        }
    }
}
